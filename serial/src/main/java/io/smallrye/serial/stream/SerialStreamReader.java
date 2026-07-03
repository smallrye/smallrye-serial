package io.smallrye.serial.stream;

import static io.smallrye.serial.stream.SerialProtocol.*;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.StreamCorruptedException;
import java.io.WriteAbortedException;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.smallrye.common.constraint.Assert;
import io.smallrye.serial.SerialData;
import io.smallrye.serial.Serialized;
import io.smallrye.serial.SerializedArrayClass;
import io.smallrye.serial.SerializedBooleanArray;
import io.smallrye.serial.SerializedByteArray;
import io.smallrye.serial.SerializedCharArray;
import io.smallrye.serial.SerializedClass;
import io.smallrye.serial.SerializedDoubleArray;
import io.smallrye.serial.SerializedEnum;
import io.smallrye.serial.SerializedEnumClass;
import io.smallrye.serial.SerializedExternalizable;
import io.smallrye.serial.SerializedExternalizableClass;
import io.smallrye.serial.SerializedFloatArray;
import io.smallrye.serial.SerializedIntArray;
import io.smallrye.serial.SerializedKnownClassLoader;
import io.smallrye.serial.SerializedLongArray;
import io.smallrye.serial.SerializedNull;
import io.smallrye.serial.SerializedObjectArray;
import io.smallrye.serial.SerializedPrimitiveClass;
import io.smallrye.serial.SerializedProxyClass;
import io.smallrye.serial.SerializedProxyObject;
import io.smallrye.serial.SerializedSerializable;
import io.smallrye.serial.SerializedSerializableClass;
import io.smallrye.serial.SerializedShortArray;
import io.smallrye.serial.SerializedSpecialSerializableClass;
import io.smallrye.serial.SerializedString;
import io.smallrye.serial.StreamData;
import io.smallrye.serial.impl.ModifiedUtf8;
import io.smallrye.serial.impl.Util;

/**
 * Reads the standard Java serialization wire format (as produced by
 * {@link java.io.ObjectOutputStream}) and constructs a {@link Serialized}
 * object graph.
 * <p>
 * Instances are created via the {@link #builder(InputStream) builder}. The builder's
 * {@link Builder#build() build()} method eagerly reads and validates the stream
 * magic and version header. Subsequent calls to {@link #readSerialized()} read
 * top-level objects from the stream.
 * <p>
 * The reader maintains a handle table for resolving {@code TC_REFERENCE}
 * back-references. It mirrors the handle assignment order of the standard
 * serialization protocol so that back-references resolve to the same objects
 * they would in a standard {@link java.io.ObjectInputStream}.
 * <p>
 * This class implements {@link SerialInput} so that the
 * {@link ClassAnnotationReader} callback can read arbitrary data (block data
 * and {@link Serialized} objects) from the stream during class annotation processing.
 *
 * @see SerialStreamWriter
 */
public final class SerialStreamReader implements SerialInput, Closeable {

    // ---- Instance fields ----

    /** The underlying data input stream. */
    private final DataInputStream in;
    /** Optional callback for reading class annotation data. */
    private final ClassAnnotationReader classAnnotationReader;
    /** Maximum nesting depth for object reads. */
    private final int maxDepth;
    /** Maximum array size allowed. */
    private final long maxArraySize;
    /** Handle table: index = handle - BASE_WIRE_HANDLE. */
    private final ArrayList<Serialized> handles = new ArrayList<>();
    /** Current nesting depth. */
    private int depth;

    // Block data read state (for ObjectInput methods inside ClassAnnotationReader callbacks)
    /** Whether the reader is currently inside a block data reading context. */
    private boolean blockDataMode;
    /** Bytes remaining in the current block data segment. */
    private int blockDataRemaining;
    /** Whether {@code TC_ENDBLOCKDATA} has already been consumed during block data reading. */
    private boolean endOfBlockData;
    /** A peeked non-block-data type code, or {@code -1} if none. */
    private int peekTC = -1;
    /** Temporary buffer for multi-byte primitive reads that may cross block boundaries. */
    private final byte[] primReadBuf = new byte[8];

    // ---- Builder ----

    /**
     * Create a new builder for a {@link SerialStreamReader} that reads from the given input stream.
     *
     * @param in the input stream to read serialization data from (must not be {@code null})
     * @return a new builder (not {@code null})
     */
    public static Builder builder(InputStream in) {
        return new Builder(Assert.checkNotNullParam("in", in));
    }

    /**
     * Builder for {@link SerialStreamReader} instances.
     */
    public static final class Builder {
        private final InputStream in;
        private ClassAnnotationReader classAnnotationReader;
        private int maxDepth = 1024;
        private long maxArraySize = Integer.MAX_VALUE;

        private Builder(InputStream in) {
            this.in = in;
        }

        /**
         * Set the callback for reading class annotation data.
         * If not set, annotation data is skipped (matching default
         * {@link java.io.ObjectInputStream} behavior).
         *
         * @param reader the annotation reader callback (may be {@code null})
         * @return this builder
         */
        public Builder classAnnotationReader(ClassAnnotationReader reader) {
            this.classAnnotationReader = reader;
            return this;
        }

        /**
         * Set the maximum nesting depth for object reads.
         * Exceeding this depth throws {@link InvalidObjectException}.
         *
         * @param maxDepth the maximum depth (must be positive)
         * @return this builder
         */
        public Builder maxDepth(int maxDepth) {
            Assert.checkMinimumParameter("maxDepth", 1, maxDepth);
            this.maxDepth = maxDepth;
            return this;
        }

        /**
         * Set the maximum array size allowed.
         * Arrays exceeding this size throw {@link InvalidObjectException}.
         *
         * @param maxArraySize the maximum array size (must be non-negative)
         * @return this builder
         */
        public Builder maxArraySize(long maxArraySize) {
            Assert.checkMinimumParameter("maxArraySize", 0, maxArraySize);
            this.maxArraySize = maxArraySize;
            return this;
        }

        /**
         * Build the reader and eagerly read and validate the stream header
         * (magic number and version).
         *
         * @return the new reader (not {@code null})
         * @throws StreamCorruptedException if the magic number or version is invalid
         * @throws IOException if an I/O error occurs while reading the header
         */
        public SerialStreamReader build() throws IOException {
            return new SerialStreamReader(this);
        }
    }

    // ---- Constructor ----

    private SerialStreamReader(Builder builder) throws IOException {
        this.in = new DataInputStream(builder.in);
        this.classAnnotationReader = builder.classAnnotationReader;
        this.maxDepth = builder.maxDepth;
        this.maxArraySize = builder.maxArraySize;
        short magic = in.readShort();
        if (magic != STREAM_MAGIC) {
            throw new StreamCorruptedException(
                    String.format("invalid stream header: %04X%04X", magic & 0xFFFF, in.readShort() & 0xFFFF));
        }
        short version = in.readShort();
        if (version != STREAM_VERSION) {
            throw new StreamCorruptedException("unsupported stream version: " + version);
        }
    }

    // ---- Public API ----

    /**
     * {@inheritDoc}
     * <p>
     * When called at the top level, reads the next object from the stream and
     * returns {@code null} at end-of-stream.
     * When called inside a {@link ClassAnnotationReader} callback (block data mode),
     * skips any remaining block data to find the next embedded object, and returns
     * {@code null} at the end of the annotation block ({@code TC_ENDBLOCKDATA}).
     */
    @Override
    public Serialized readSerialized() throws IOException {
        if (blockDataMode) {
            return readSerializedInBlockMode();
        }
        try {
            return readObject0();
        } catch (EOFException e) {
            return null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void close() throws IOException {
        in.close();
    }

    // ---- Core dispatch ----

    /**
     * Read a single object from the stream. Reads the type code then dispatches.
     */
    private Serialized readObject0() throws IOException {
        if (++depth > maxDepth) {
            throw new InvalidObjectException("maximum stream depth exceeded: " + maxDepth);
        }
        try {
            return readObject0(in.readByte());
        } finally {
            depth--;
        }
    }

    /**
     * Dispatch on a type code that has already been read.
     *
     * @param tc the type code byte
     * @return the deserialized node (not {@code null})
     * @throws IOException on I/O or protocol errors
     */
    private Serialized readObject0(byte tc) throws IOException {
        return switch (tc) {
            case TC_NULL -> SerializedNull.INSTANCE;
            case TC_REFERENCE -> readReference();
            case TC_STRING -> readString();
            case TC_LONGSTRING -> readLongString();
            case TC_OBJECT -> readTcObject();
            case TC_ARRAY -> readArray();
            case TC_ENUM -> readEnum();
            case TC_CLASS -> readClassValue();
            case TC_CLASSDESC, TC_PROXYCLASSDESC -> readClassDesc(tc);
            case TC_RESET -> {
                handles.clear();
                yield readObject0();
            }
            case TC_EXCEPTION -> {
                handles.clear();
                readObject0();
                throw new WriteAbortedException("writing aborted", new IOException("TC_EXCEPTION in stream"));
            }
            case TC_BLOCKDATA, TC_BLOCKDATALONG -> throw new StreamCorruptedException(
                    "unexpected block data (TC 0x" + Integer.toHexString(tc & 0xFF) + ")");
            case TC_ENDBLOCKDATA -> throw new StreamCorruptedException("unexpected end of block data");
            default -> throw new StreamCorruptedException("invalid type code: 0x" + Integer.toHexString(tc & 0xFF));
        };
    }

    // ---- Handle management ----

    /**
     * Read a TC_REFERENCE and resolve it from the handle table.
     *
     * @return the referenced Serialized node (not {@code null})
     * @throws StreamCorruptedException if the handle is out of range
     */
    private Serialized readReference() throws IOException {
        int wireHandle = in.readInt();
        int index = wireHandle - BASE_WIRE_HANDLE;
        if (index < 0 || index >= handles.size()) {
            throw new StreamCorruptedException(
                    "invalid handle: 0x" + Integer.toHexString(wireHandle) + " (table size: " + handles.size() + ")");
        }
        return handles.get(index);
    }

    // ---- String reading ----

    /**
     * Read a TC_STRING (2-byte length prefix + modified UTF-8 data).
     *
     * @return the string node with handle assigned (not {@code null})
     */
    private SerializedString readString() throws IOException {
        int utfLen = in.readUnsignedShort();
        SerializedString s = new SerializedString(ModifiedUtf8.decode(readNBytes(utfLen), 0, utfLen));
        handles.add(s);
        return s;
    }

    /**
     * Read a TC_LONGSTRING (8-byte length prefix + modified UTF-8 data).
     *
     * @return the string node with handle assigned (not {@code null})
     */
    private SerializedString readLongString() throws IOException {
        long utfLen = in.readLong();
        if (utfLen < 0 || utfLen > Integer.MAX_VALUE) {
            throw new StreamCorruptedException("long string length out of range: " + utfLen);
        }
        byte[] buf = readNBytes((int) utfLen);
        SerializedString s = new SerializedString(ModifiedUtf8.decode(buf, 0, (int) utfLen));
        handles.add(s);
        return s;
    }

    /**
     * Read a raw modified UTF-8 string (2-byte length + data).
     * This is NOT a TC_STRING — used for class names, field names, etc.
     *
     * @return the decoded string (not {@code null})
     */
    private String readRawUTF() throws IOException {
        int utfLen = in.readUnsignedShort();
        return ModifiedUtf8.decode(readNBytes(utfLen), 0, utfLen);
    }

    /**
     * Read a string object that may be TC_STRING, TC_LONGSTRING, TC_REFERENCE, or TC_NULL.
     * Used where the grammar expects a "string" production (e.g. field type descriptors).
     *
     * @return the Serialized string node or null/reference (not {@code null})
     */
    private Serialized readStringOrReference() throws IOException {
        byte tc = in.readByte();
        return switch (tc) {
            case TC_STRING -> readString();
            case TC_LONGSTRING -> readLongString();
            case TC_REFERENCE -> readReference();
            case TC_NULL -> SerializedNull.INSTANCE;
            default -> throw new StreamCorruptedException(
                    "expected TC_STRING, TC_LONGSTRING, TC_REFERENCE, or TC_NULL, got: 0x"
                            + Integer.toHexString(tc & 0xFF));
        };
    }

    // ---- Class descriptor reading ----

    /**
     * Read a class descriptor from the stream. The next byte may be TC_CLASSDESC,
     * TC_PROXYCLASSDESC, TC_REFERENCE, or TC_NULL.
     *
     * @return the class descriptor node (may be {@link SerializedNull#INSTANCE})
     */
    private Serialized readClassDescOrRef() throws IOException {
        byte tc = in.readByte();
        return switch (tc) {
            case TC_CLASSDESC -> readNormalClassDesc();
            case TC_PROXYCLASSDESC -> readProxyClassDesc();
            case TC_REFERENCE -> readReference();
            case TC_NULL -> SerializedNull.INSTANCE;
            default -> throw new StreamCorruptedException(
                    "expected class descriptor, got: 0x" + Integer.toHexString(tc & 0xFF));
        };
    }

    /**
     * Read a class descriptor given a type code that was already consumed.
     *
     * @param tc the type code (TC_CLASSDESC or TC_PROXYCLASSDESC)
     * @return the class descriptor node (not {@code null})
     */
    private Serialized readClassDesc(byte tc) throws IOException {
        return switch (tc) {
            case TC_CLASSDESC -> readNormalClassDesc();
            case TC_PROXYCLASSDESC -> readProxyClassDesc();
            default -> throw new StreamCorruptedException(
                    "expected TC_CLASSDESC or TC_PROXYCLASSDESC, got: 0x" + Integer.toHexString(tc & 0xFF));
        };
    }

    /**
     * Read a normal (non-proxy) class descriptor: name, SUID, flags, fields,
     * annotation, superclass.
     * <p>
     * Per the spec, the handle is assigned immediately after TC_CLASSDESC
     * (before reading the descriptor body). We reserve a slot and fill it
     * once the descriptor type is determined.
     *
     * @return the class descriptor node (not {@code null})
     */
    private Serialized readNormalClassDesc() throws IOException {
        int handleIndex = handles.size();
        handles.add(null);

        String name = readRawUTF();
        long uid = in.readLong();
        int flags = in.readUnsignedByte();
        int fieldCount = in.readUnsignedShort();

        // read field descriptors
        FieldDesc[] fields = new FieldDesc[fieldCount];
        for (int i = 0; i < fieldCount; i++) {
            char typeCode = (char) in.readByte();
            String fieldName = readRawUTF();
            ClassDesc type;
            if (typeCode == 'L' || typeCode == '[') {
                // object or array field: type string is a TC_STRING object
                Serialized typeStr = readStringOrReference();
                String typeDescStr;
                if (typeStr instanceof SerializedString ss) {
                    typeDescStr = ss.string();
                } else {
                    throw new StreamCorruptedException("field type descriptor is not a string");
                }
                // wire format uses dots; ClassDesc uses slashes
                type = ClassDesc.ofDescriptor(typeDescStr.replace('.', '/'));
            } else {
                type = primitiveClassDesc(typeCode);
            }
            fields[i] = new FieldDesc(fieldName, type);
        }

        // class annotation: read and extract class loader
        Serialized classLoader = readClassAnnotation();

        // superclass descriptor
        Serialized superDesc = readClassDescOrRef();

        // build the appropriate class descriptor type from the flags
        Serialized classDesc = buildClassDesc(name, uid, flags, fields, superDesc, classLoader);
        handles.set(handleIndex, classDesc);
        return classDesc;
    }

    /**
     * Build the appropriate class descriptor type from parsed wire data.
     *
     * @param name the class name
     * @param uid the serial version UID
     * @param flags the class descriptor flags byte
     * @param fields the parsed field descriptors
     * @param superDesc the superclass descriptor (may be {@link SerializedNull#INSTANCE})
     * @param classLoader the class loader from the class annotation (not {@code null})
     * @return the constructed class descriptor node
     */
    private Serialized buildClassDesc(String name, long uid, int flags, FieldDesc[] fields,
            Serialized superDesc, Serialized classLoader) throws IOException {
        boolean isEnum = (flags & SC_ENUM) != 0;
        boolean isSerializable = (flags & SC_SERIALIZABLE) != 0;
        boolean isExternalizable = (flags & SC_EXTERNALIZABLE) != 0;

        ClassDesc cd = Util.classDescOfName(name);

        if (Util.classDescEquals(cd, ConstantDescs.CD_Enum)) {
            return SerializedSpecialSerializableClass.ENUM;
        }
        if (isEnum) {
            SerializedClass enumSuper = superDesc instanceof SerializedClass sc ? sc : null;
            return new SerializedEnumClass(cd, classLoader, uid, enumSuper);
        } else if (isExternalizable) {
            if ((flags & SC_BLOCK_DATA) == 0) {
                throw new StreamCorruptedException(
                        "Pre-1.2 externalizable stream (SC_BLOCK_DATA not set) cannot be structurally parsed");
            }
            SerializedClass extSuper = superDesc instanceof SerializedClass sc ? sc : null;
            return new SerializedExternalizableClass(cd, classLoader, uid, extSuper);
        } else if (isSerializable) {
            if (name.startsWith("[")) {
                ClassDesc componentDesc = cd.componentType();
                return new SerializedArrayClass(cd, classLoader, uid,
                        buildComponentTypeDesc(componentDesc, classLoader));
            }
            SerializedSerializableClass.Builder builder = SerializedSerializableClass.builder()
                    .classDesc(cd)
                    .classLoader(classLoader)
                    .uid(uid)
                    .hasWriteMethod((flags & SC_WRITE_METHOD) != 0);
            for (FieldDesc fd : fields) {
                builder.addField(fd.name, fd.type);
            }
            if (superDesc instanceof SerializedSerializableClass ssc) {
                builder.superClass(ssc);
            }
            return builder.build();
        } else {
            throw new StreamCorruptedException("invalid class descriptor flags: 0x" + Integer.toHexString(flags));
        }
    }

    /**
     * Build a minimal class descriptor for the component type of an array.
     * <p>
     * Primitives are represented by the appropriate {@link SerializedPrimitiveClass} singleton.
     * Nested arrays recurse. Object types produce a placeholder {@link SerializedSerializableClass}
     * with no fields.
     *
     * @param componentDesc the component type class descriptor (must not be {@code null})
     * @param classLoader the class loader to use for the component type (not {@code null})
     * @return a class descriptor for the component type (not {@code null})
     */
    private SerializedClass buildComponentTypeDesc(ClassDesc componentDesc, Serialized classLoader) {
        if (componentDesc.isPrimitive()) {
            return SerializedPrimitiveClass.of(componentDesc);
        }
        if (componentDesc.isArray()) {
            return new SerializedArrayClass(componentDesc, classLoader, 0,
                    buildComponentTypeDesc(componentDesc.componentType(), classLoader));
        }
        return SerializedSerializableClass.builder()
                .classDesc(componentDesc)
                .classLoader(classLoader)
                .uid(0)
                .build();
    }

    /**
     * Read a proxy class descriptor: interface count, interface names,
     * annotation, superclass.
     *
     * @return the proxy class descriptor node (not {@code null})
     */
    private Serialized readProxyClassDesc() throws IOException {
        int handleIndex = handles.size();
        handles.add(null);

        int ifaceCount = in.readInt();
        List<String> ifaceNames = new ArrayList<>(ifaceCount);
        for (int i = 0; i < ifaceCount; i++) {
            ifaceNames.add(readRawUTF());
        }

        Serialized classLoader = readClassAnnotation();

        // superclass descriptor (typically java.lang.reflect.Proxy)
        Serialized rawSuperDesc = readClassDescOrRef();
        SerializedClass proxySuperClass = rawSuperDesc instanceof SerializedClass sc ? sc : null;

        SerializedProxyClass proxyClass = new SerializedProxyClass(ifaceNames, classLoader, proxySuperClass);
        handles.set(handleIndex, proxyClass);
        return proxyClass;
    }

    /**
     * Read the class annotation data and return the class loader to use for the
     * class descriptor being constructed.
     * <p>
     * When a {@link ClassAnnotationReader} callback is configured, block data mode
     * is entered before invoking the callback so that the callback can use this
     * reader's {@link SerialInput} methods ({@link #readInt()}, {@link #readSerialized()},
     * etc.) to consume annotation data. After the callback returns, any unconsumed
     * annotation data is skipped up to and including the {@code TC_ENDBLOCKDATA} marker.
     * <p>
     * If no callback is configured, or the callback returns {@code null}, the
     * unspecified class loader constant is returned.
     *
     * @return the class loader representation for the class descriptor (not {@code null})
     * @throws IOException if an I/O error or protocol error occurs
     */
    private Serialized readClassAnnotation() throws IOException {
        Serialized classLoader = null;
        if (classAnnotationReader != null) {
            blockDataMode = true;
            blockDataRemaining = 0;
            endOfBlockData = false;
            peekTC = -1;
            try {
                classLoader = classAnnotationReader.read(this);
            } finally {
                blockDataMode = false;
            }
        }
        if (!endOfBlockData) {
            skipAnnotation();
        }
        endOfBlockData = false;
        peekTC = -1;
        return classLoader != null ? classLoader : SerializedKnownClassLoader.forUnspecifiedClassLoader();
    }

    /**
     * Skip content until {@code TC_ENDBLOCKDATA} is encountered.
     * Handles residual block data state from the callback (remaining bytes in the
     * current segment, a peeked type code), then reads and discards any remaining
     * block data and embedded objects.
     *
     * @throws IOException if an I/O error or protocol error occurs
     */
    private void skipAnnotation() throws IOException {
        // skip remaining bytes from a partially-consumed block data segment
        if (blockDataRemaining > 0) {
            skipNBytes(blockDataRemaining);
            blockDataRemaining = 0;
        }
        // process peeked TC if any (always an object TC, never block data or end marker)
        if (peekTC >= 0) {
            byte tc = (byte) peekTC;
            peekTC = -1;
            readObject0(tc);
        }
        // read remaining TCs until TC_ENDBLOCKDATA
        for (;;) {
            byte tc = in.readByte();
            switch (tc) {
                case TC_ENDBLOCKDATA -> {
                    return;
                }
                case TC_BLOCKDATA -> {
                    int len = in.readUnsignedByte();
                    skipNBytes(len);
                }
                case TC_BLOCKDATALONG -> {
                    int len = in.readInt();
                    skipNBytes(len);
                }
                default -> readObject0(tc);
            }
        }
    }

    // ---- Object reading ----

    /**
     * Read TC_OBJECT: the class descriptor determines the concrete object type.
     *
     * @return the deserialized object node (not {@code null})
     */
    private Serialized readTcObject() throws IOException {
        Serialized classDescNode = readClassDescOrRef();

        if (classDescNode instanceof SerializedProxyClass proxyClass) {
            return readProxyObject(proxyClass);
        } else if (classDescNode instanceof SerializedExternalizableClass extClass) {
            return readExternalizableObject(extClass);
        } else if (classDescNode instanceof SerializedEnumClass) {
            throw new StreamCorruptedException("enum used TC_OBJECT instead of TC_ENUM");
        } else if (classDescNode instanceof SerializedSerializableClass serClass) {
            return readSerializableObject(serClass);
        } else {
            throw new StreamCorruptedException(
                    "unexpected class descriptor type for TC_OBJECT: " + classDescNode.getClass().getName());
        }
    }

    /**
     * Read a serializable object's per-level data.
     * Uses the wire-reader constructor to register the handle before reading data,
     * supporting circular references.
     *
     * @param serClass the serializable class descriptor
     * @return the serialized object node (not {@code null})
     */
    private Serialized readSerializableObject(SerializedSerializableClass serClass) throws IOException {
        return new SerializedSerializable(serClass, handles::add, () -> {
            // walk the superclass chain and read per-level data from root to leaf
            List<SerialData> levels = new ArrayList<>();
            collectClassLevels(serClass, levels);
            return levels;
        });
    }

    /**
     * Build the superclass chain from root to leaf, then read field data
     * for each level in that order.
     *
     * @param classDesc the leaf (most-derived) class descriptor
     * @param levels output list that receives per-level data in root-to-leaf order
     */
    private void collectClassLevels(SerializedSerializableClass classDesc, List<SerialData> levels) throws IOException {
        List<SerializedSerializableClass> chain = new ArrayList<>();
        for (SerializedSerializableClass c = classDesc; c != null; c = c.superClass()) {
            chain.add(c);
        }
        // read from root (last in chain) to leaf (first in chain),
        // skipping levels that have no fields and no write method (nothing in the stream)
        for (int i = chain.size() - 1; i >= 0; i--) {
            SerializedSerializableClass c = chain.get(i);
            if (c.primitiveBufferSize() == 0 && c.objectBufferSize() == 0 && !c.hasWriteMethod()) {
                continue;
            }
            levels.add(readClassLevelData(c));
        }
    }

    /**
     * Read data for a single class level: primitive fields, object fields, and
     * optional stream data (if SC_WRITE_METHOD was set in the stream class descriptor).
     * <p>
     * Stream data (block data + objects + TC_ENDBLOCKDATA) only follows field data
     * when the class descriptor's flags included SC_WRITE_METHOD. Without this flag,
     * only the raw field data is present.
     *
     * @param levelClass the class descriptor for this hierarchy level
     * @return the serial data for this level (not {@code null})
     */
    private SerialData readClassLevelData(SerializedSerializableClass levelClass) throws IOException {
        int primSize = levelClass.primitiveBufferSize();
        int objSize = levelClass.objectBufferSize();

        // primitive field data: raw bytes in big-endian wire order
        byte[] primBuf = primSize > 0 ? readNBytes(primSize) : new byte[0];

        // object field data: one object per reference-typed field, in declaration order
        Serialized[] objBuf = new Serialized[objSize];
        for (int i = 0; i < objSize; i++) {
            objBuf[i] = readObject0();
        }

        // stream data + TC_ENDBLOCKDATA, only if SC_WRITE_METHOD was set
        List<StreamData> streamData;
        if (levelClass.hasWriteMethod()) {
            streamData = readAnnotationContent();
        } else {
            streamData = List.of();
        }

        return new SerialData(
                levelClass,
                StreamData.of(primBuf),
                StreamData.of(objBuf),
                streamData);
    }

    /**
     * Read an externalizable object: block data terminated by TC_ENDBLOCKDATA.
     *
     * @param extClass the externalizable class descriptor
     * @return the deserialized externalizable node (not {@code null})
     */
    private Serialized readExternalizableObject(SerializedExternalizableClass extClass) throws IOException {
        return new SerializedExternalizable(extClass, handles::add, this::readAnnotationContent);
    }

    /**
     * Read a proxy object's data.
     * In the wire format, a proxy object's data consists of the
     * java.lang.reflect.Proxy superclass level (one object field: the invocation handler).
     *
     * @param proxyClass the proxy class descriptor
     * @return the deserialized proxy object node (not {@code null})
     */
    private Serialized readProxyObject(SerializedProxyClass proxyClass) throws IOException {
        // proxy data: Proxy super's one object field (invocation handler)
        return new SerializedProxyObject(proxyClass, handles::add, this::readObject0);
    }

    // ---- Enum reading ----

    /**
     * Read a TC_ENUM: class descriptor + handle + constant name.
     *
     * @return the deserialized enum node (not {@code null})
     */
    private Serialized readEnum() throws IOException {
        Serialized classDescNode = readClassDescOrRef();
        if (!(classDescNode instanceof SerializedEnumClass enumClass)) {
            throw new StreamCorruptedException("TC_ENUM class descriptor is not an enum class");
        }
        return new SerializedEnum(enumClass, handles::add, this::readObject0);
    }

    // ---- Array reading ----

    /**
     * Read a TC_ARRAY: class descriptor + handle + count + elements.
     * Dispatches to type-specific readers based on the component type code
     * from the array class name.
     *
     * @return the deserialized array node (not {@code null})
     */
    private Serialized readArray() throws IOException {
        Serialized classDescNode = readClassDescOrRef();
        if (!(classDescNode instanceof SerializedArrayClass arrayClass)) {
            throw new StreamCorruptedException("TC_ARRAY class descriptor is not an array class");
        }
        int handleIndex = handles.size();
        handles.add(null);

        int length = in.readInt();
        if (length < 0 || length > maxArraySize) {
            throw new InvalidObjectException("array size exceeds limit: " + Integer.toUnsignedLong(length));
        }

        char componentCode = arrayClass.descriptor().componentType().descriptorString().charAt(0);

        Serialized result = switch (componentCode) {
            case 'Z' -> readBooleanArray(arrayClass, length);
            case 'B' -> readByteArray(arrayClass, length);
            case 'C' -> readCharArray(arrayClass, length);
            case 'S' -> readShortArray(arrayClass, length);
            case 'I' -> readIntArray(arrayClass, length);
            case 'J' -> readLongArray(arrayClass, length);
            case 'F' -> readFloatArray(arrayClass, length);
            case 'D' -> readDoubleArray(arrayClass, length);
            case 'L', '[' -> readObjectArray(arrayClass, length);
            default -> throw new IllegalStateException("Unexpected array component code: " + componentCode);
        };
        handles.set(handleIndex, result);
        return result;
    }

    /**
     * Read a boolean array of the given length.
     *
     * @param arrayClass the array class descriptor
     * @param length the number of elements
     * @return the deserialized boolean array node (not {@code null})
     */
    private SerializedBooleanArray readBooleanArray(SerializedArrayClass arrayClass, int length) throws IOException {
        boolean[] arr = new boolean[length];
        for (int i = 0; i < length; i++) {
            arr[i] = in.readByte() != 0;
        }
        return new SerializedBooleanArray(arrayClass, arr);
    }

    /**
     * Read a byte array of the given length.
     *
     * @param arrayClass the array class descriptor
     * @param length the number of elements
     * @return the deserialized byte array node (not {@code null})
     */
    private SerializedByteArray readByteArray(SerializedArrayClass arrayClass, int length) throws IOException {
        return new SerializedByteArray(arrayClass, readNBytes(length));
    }

    /**
     * Read a char array of the given length.
     *
     * @param arrayClass the array class descriptor
     * @param length the number of elements
     * @return the deserialized char array node (not {@code null})
     */
    private SerializedCharArray readCharArray(SerializedArrayClass arrayClass, int length) throws IOException {
        char[] arr = new char[length];
        for (int i = 0; i < length; i++) {
            arr[i] = in.readChar();
        }
        return new SerializedCharArray(arrayClass, arr);
    }

    /**
     * Read a short array of the given length.
     *
     * @param arrayClass the array class descriptor
     * @param length the number of elements
     * @return the deserialized short array node (not {@code null})
     */
    private SerializedShortArray readShortArray(SerializedArrayClass arrayClass, int length) throws IOException {
        short[] arr = new short[length];
        for (int i = 0; i < length; i++) {
            arr[i] = in.readShort();
        }
        return new SerializedShortArray(arrayClass, arr);
    }

    /**
     * Read an int array of the given length.
     *
     * @param arrayClass the array class descriptor
     * @param length the number of elements
     * @return the deserialized int array node (not {@code null})
     */
    private SerializedIntArray readIntArray(SerializedArrayClass arrayClass, int length) throws IOException {
        int[] arr = new int[length];
        for (int i = 0; i < length; i++) {
            arr[i] = in.readInt();
        }
        return new SerializedIntArray(arrayClass, arr);
    }

    /**
     * Read a long array of the given length.
     *
     * @param arrayClass the array class descriptor
     * @param length the number of elements
     * @return the deserialized long array node (not {@code null})
     */
    private SerializedLongArray readLongArray(SerializedArrayClass arrayClass, int length) throws IOException {
        long[] arr = new long[length];
        for (int i = 0; i < length; i++) {
            arr[i] = in.readLong();
        }
        return new SerializedLongArray(arrayClass, arr);
    }

    /**
     * Read a float array of the given length.
     *
     * @param arrayClass the array class descriptor
     * @param length the number of elements
     * @return the deserialized float array node (not {@code null})
     */
    private SerializedFloatArray readFloatArray(SerializedArrayClass arrayClass, int length) throws IOException {
        float[] arr = new float[length];
        for (int i = 0; i < length; i++) {
            arr[i] = in.readFloat();
        }
        return new SerializedFloatArray(arrayClass, arr);
    }

    /**
     * Read a double array of the given length.
     *
     * @param arrayClass the array class descriptor
     * @param length the number of elements
     * @return the deserialized double array node (not {@code null})
     */
    private SerializedDoubleArray readDoubleArray(SerializedArrayClass arrayClass, int length) throws IOException {
        double[] arr = new double[length];
        for (int i = 0; i < length; i++) {
            arr[i] = in.readDouble();
        }
        return new SerializedDoubleArray(arrayClass, arr);
    }

    /**
     * Read an object array of the given length.
     *
     * @param arrayClass the array class descriptor
     * @param length the number of elements
     * @return the deserialized object array node (not {@code null})
     */
    private SerializedObjectArray readObjectArray(SerializedArrayClass arrayClass, int length) throws IOException {
        Serialized[] arr = new Serialized[length];
        for (int i = 0; i < length; i++) {
            arr[i] = readObject0();
        }
        return new SerializedObjectArray(arrayClass, arr);
    }

    // ---- TC_CLASS reading ----

    /**
     * Read a TC_CLASS value: class descriptor + handle.
     * The handle is assigned for the Class value itself (distinct from the class
     * descriptor's own handle).
     *
     * @return the class descriptor node (not {@code null})
     */
    private Serialized readClassValue() throws IOException {
        Serialized classDesc = readClassDescOrRef();
        // assign handle for the Class *value* (distinct from the class descriptor handle)
        handles.add(classDesc);
        return classDesc;
    }

    // ---- SerialInput / DataInput implementation ----

    /**
     * Read the next {@link Serialized} object from within a block data context.
     * Skips any remaining block data (current segment and subsequent segments)
     * until a non-block-data type code is encountered.
     *
     * @return the deserialized object, or {@code null} if {@code TC_ENDBLOCKDATA} is reached
     * @throws IOException if an I/O error or protocol error occurs
     */
    private Serialized readSerializedInBlockMode() throws IOException {
        // skip remaining bytes in current block
        if (blockDataRemaining > 0) {
            skipNBytes(blockDataRemaining);
            blockDataRemaining = 0;
        }
        // check for a previously peeked object TC
        if (peekTC >= 0) {
            byte tc = (byte) peekTC;
            peekTC = -1;
            return readObject0(tc);
        }
        // skip block data segments until we find an object or end marker
        for (;;) {
            byte tc = in.readByte();
            switch (tc) {
                case TC_BLOCKDATA -> skipNBytes(in.readUnsignedByte());
                case TC_BLOCKDATALONG -> skipNBytes(in.readInt());
                case TC_ENDBLOCKDATA -> {
                    endOfBlockData = true;
                    blockDataMode = false;
                    return null;
                }
                default -> {
                    return readObject0(tc);
                }
            }
        }
    }

    /**
     * Read a single byte from block data.
     * Returns {@code -1} if the end of block data has been reached.
     *
     * @return the byte read (0–255), or {@code -1} at end of block data
     * @throws IllegalStateException if not in block data mode
     * @throws IOException if an I/O error occurs
     */
    @Override
    public int read() throws IOException {
        checkBlockDataMode();
        return readBlockByte();
    }

    /**
     * Read bytes from block data into a byte array.
     * Returns the number of bytes actually read, or {@code -1} if the end of
     * block data has been reached before any bytes could be read.
     *
     * @param b the buffer to read into (not {@code null})
     * @return the number of bytes read, or {@code -1} at end of block data
     * @throws IllegalStateException if not in block data mode
     * @throws IOException if an I/O error occurs
     */
    @Override
    public int read(final byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    /**
     * Read bytes from block data into a portion of a byte array.
     * Returns the number of bytes actually read, or {@code -1} if the end of
     * block data has been reached before any bytes could be read.
     *
     * @param b the buffer to read into (not {@code null})
     * @param off the start offset in the buffer
     * @param len the maximum number of bytes to read
     * @return the number of bytes read, or {@code -1} at end of block data
     * @throws IllegalStateException if not in block data mode
     * @throws IOException if an I/O error occurs
     */
    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        checkBlockDataMode();
        if (len == 0) {
            return 0;
        }
        if (blockDataRemaining == 0 && !refillBlockData()) {
            return -1;
        }
        int n = Math.min(len, blockDataRemaining);
        in.readFully(b, off, n);
        blockDataRemaining -= n;
        return n;
    }

    /**
     * Skip up to {@code n} bytes of block data.
     *
     * @param n the number of bytes to skip
     * @return the actual number of bytes skipped
     * @throws IllegalStateException if not in block data mode
     * @throws IOException if an I/O error occurs
     */
    @Override
    public long skip(final long n) throws IOException {
        checkBlockDataMode();
        if (n <= 0) {
            return 0;
        }
        if (blockDataRemaining == 0 && !refillBlockData()) {
            return 0;
        }
        long toSkip = Math.min(n, blockDataRemaining);
        skipNBytes(toSkip);
        blockDataRemaining -= (int) toSkip;
        return toSkip;
    }

    /**
     * {@return the number of bytes of block data known to be immediately available
     * without blocking (the remaining bytes in the current block segment)}
     *
     * @throws IllegalStateException if not in block data mode
     * @throws IOException if an I/O error occurs
     */
    @Override
    public int available() throws IOException {
        if (!blockDataMode) {
            return 0;
        }
        return blockDataRemaining;
    }

    /** {@inheritDoc} */
    @Override
    public void readFully(final byte[] b) throws IOException {
        readFully(b, 0, b.length);
    }

    /**
     * Read exactly {@code len} bytes from block data into the given buffer.
     *
     * @param b the buffer to read into (not {@code null})
     * @param off the start offset in the buffer
     * @param len the number of bytes to read
     * @throws EOFException if the end of block data is reached before all bytes are read
     * @throws IllegalStateException if not in block data mode
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void readFully(final byte[] b, final int off, final int len) throws IOException {
        checkBlockDataMode();
        readBlockFully(b, off, len);
    }

    /**
     * Skip exactly {@code n} bytes of block data.
     *
     * @param n the number of bytes to skip
     * @return the number of bytes actually skipped (always {@code n})
     * @throws EOFException if the end of block data is reached before all bytes are skipped
     * @throws IllegalStateException if not in block data mode
     * @throws IOException if an I/O error occurs
     */
    @Override
    public int skipBytes(final int n) throws IOException {
        checkBlockDataMode();
        if (n <= 0) {
            return 0;
        }
        skipBlockData(n);
        return n;
    }

    /** {@inheritDoc} */
    @Override
    public boolean readBoolean() throws IOException {
        int b = read();
        if (b < 0) {
            throw new EOFException("end of block data");
        }
        return b != 0;
    }

    /** {@inheritDoc} */
    @Override
    public byte readByte() throws IOException {
        int b = read();
        if (b < 0) {
            throw new EOFException("end of block data");
        }
        return (byte) b;
    }

    /** {@inheritDoc} */
    @Override
    public int readUnsignedByte() throws IOException {
        int b = read();
        if (b < 0) {
            throw new EOFException("end of block data");
        }
        return b;
    }

    /** {@inheritDoc} */
    @Override
    public short readShort() throws IOException {
        checkBlockDataMode();
        if (blockDataRemaining >= 2) {
            blockDataRemaining -= 2;
            return in.readShort();
        }
        readBlockFully(primReadBuf, 0, 2);
        return (short) Util.BE16.get(primReadBuf, 0);
    }

    /** {@inheritDoc} */
    @Override
    public int readUnsignedShort() throws IOException {
        return readShort() & 0xFFFF;
    }

    /** {@inheritDoc} */
    @Override
    public char readChar() throws IOException {
        return (char) readShort();
    }

    /** {@inheritDoc} */
    @Override
    public int readInt() throws IOException {
        checkBlockDataMode();
        if (blockDataRemaining >= 4) {
            blockDataRemaining -= 4;
            return in.readInt();
        }
        readBlockFully(primReadBuf, 0, 4);
        return (int) Util.BE32.get(primReadBuf, 0);
    }

    /** {@inheritDoc} */
    @Override
    public long readLong() throws IOException {
        checkBlockDataMode();
        if (blockDataRemaining >= 8) {
            blockDataRemaining -= 8;
            return in.readLong();
        }
        readBlockFully(primReadBuf, 0, 8);
        return (long) Util.BE64.get(primReadBuf, 0);
    }

    /** {@inheritDoc} */
    @Override
    public float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    /** {@inheritDoc} */
    @Override
    public double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    /**
     * This method is not supported.
     *
     * @return never returns normally
     * @throws UnsupportedOperationException always
     * @deprecated Use {@link #readUTF()} instead.
     */
    @Deprecated
    @Override
    public String readLine() {
        throw new UnsupportedOperationException("readLine is not supported");
    }

    /**
     * Read a modified UTF-8 encoded string from block data.
     * Reads a 2-byte length prefix followed by that many bytes of modified UTF-8 data.
     *
     * @return the decoded string (not {@code null})
     * @throws EOFException if the end of block data is reached before all data is read
     * @throws IllegalStateException if not in block data mode
     * @throws IOException if an I/O error occurs
     */
    @Override
    public String readUTF() throws IOException {
        int utfLen = readUnsignedShort();
        byte[] buf = new byte[utfLen];
        readFully(buf);
        return ModifiedUtf8.decode(buf, 0, utfLen);
    }

    // ---- Block data read helpers ----

    /**
     * Assert that the reader is currently in block data mode.
     *
     * @throws IllegalStateException if not in block data mode
     */
    private void checkBlockDataMode() {
        if (!blockDataMode) {
            throw new IllegalStateException(
                    "DataInput methods are only available inside a ClassAnnotationReader callback");
        }
    }

    /**
     * Read a single byte from block data, automatically refilling when the current
     * segment is exhausted.
     *
     * @return the byte read (0–255), or {@code -1} at end of block data
     * @throws IOException if an I/O error occurs
     */
    private int readBlockByte() throws IOException {
        if (blockDataRemaining == 0 && !refillBlockData()) {
            return -1;
        }
        blockDataRemaining--;
        return in.readUnsignedByte();
    }

    /**
     * Read exactly {@code len} bytes from block data into the given buffer,
     * crossing block segment boundaries as needed.
     *
     * @param b the buffer to read into (not {@code null})
     * @param off the start offset in the buffer
     * @param len the number of bytes to read
     * @throws EOFException if the end of block data is reached before all bytes are read
     * @throws IOException if an I/O error occurs
     */
    private void readBlockFully(final byte[] b, int off, int len) throws IOException {
        while (len > 0) {
            if (blockDataRemaining == 0 && !refillBlockData()) {
                throw new EOFException("end of block data");
            }
            int n = Math.min(len, blockDataRemaining);
            in.readFully(b, off, n);
            blockDataRemaining -= n;
            off += n;
            len -= n;
        }
    }

    /**
     * Skip exactly {@code count} bytes of block data, crossing block segment
     * boundaries as needed.
     *
     * @param count the number of bytes to skip
     * @throws EOFException if the end of block data is reached before all bytes are skipped
     * @throws IOException if an I/O error occurs
     */
    private void skipBlockData(int count) throws IOException {
        while (count > 0) {
            if (blockDataRemaining == 0 && !refillBlockData()) {
                throw new EOFException("end of block data");
            }
            int n = Math.min(count, blockDataRemaining);
            skipNBytes(n);
            blockDataRemaining -= n;
            count -= n;
        }
    }

    /**
     * Attempt to start reading the next block data segment.
     * Reads the next type code from the stream and, if it is a block data header,
     * sets {@link #blockDataRemaining} to the segment length.
     *
     * @return {@code true} if a new block data segment was found, {@code false} if
     *         the end of block data was reached or a non-block-data type code was encountered
     * @throws IOException if an I/O error occurs
     */
    private boolean refillBlockData() throws IOException {
        if (peekTC >= 0) {
            return false;
        }
        byte tc = in.readByte();
        switch (tc) {
            case TC_BLOCKDATA -> {
                blockDataRemaining = in.readUnsignedByte();
                return true;
            }
            case TC_BLOCKDATALONG -> {
                blockDataRemaining = in.readInt();
                if (blockDataRemaining < 0) {
                    throw new StreamCorruptedException("negative block data length");
                }
                return true;
            }
            case TC_ENDBLOCKDATA -> {
                endOfBlockData = true;
                blockDataMode = false;
                return false;
            }
            default -> {
                peekTC = tc;
                return false;
            }
        }
    }

    // ---- Annotation / block data reading ----

    /**
     * Read annotation content: alternating block data and objects,
     * terminated by TC_ENDBLOCKDATA.
     * <p>
     * Consecutive byte blocks are coalesced into single {@link StreamData.OfBytes} entries;
     * consecutive objects are grouped into single {@link StreamData.OfObjects} entries.
     *
     * @return the list of stream data entries (not {@code null})
     */
    private List<StreamData> readAnnotationContent() throws IOException {
        List<StreamData> result = new ArrayList<>();
        byte[] byteBuf = null;
        List<Serialized> objBuf = null;

        for (;;) {
            byte tc = in.readByte();
            switch (tc) {
                case TC_ENDBLOCKDATA -> {
                    if (byteBuf != null) {
                        result.add(StreamData.of(byteBuf));
                    }
                    if (objBuf != null) {
                        result.add(StreamData.of(objBuf));
                    }
                    return result;
                }
                case TC_BLOCKDATA -> {
                    if (objBuf != null) {
                        result.add(StreamData.of(objBuf));
                        objBuf = null;
                    }
                    int len = in.readUnsignedByte();
                    byte[] block = readNBytes(len);
                    byteBuf = appendBytes(byteBuf, block);
                }
                case TC_BLOCKDATALONG -> {
                    if (objBuf != null) {
                        result.add(StreamData.of(objBuf));
                        objBuf = null;
                    }
                    int len = in.readInt();
                    if (len < 0) {
                        throw new StreamCorruptedException("negative block data length");
                    }
                    byte[] block = readNBytes(len);
                    byteBuf = appendBytes(byteBuf, block);
                }
                default -> {
                    if (byteBuf != null) {
                        result.add(StreamData.of(byteBuf));
                        byteBuf = null;
                    }
                    Serialized obj = readObject0(tc);
                    if (objBuf == null) {
                        objBuf = new ArrayList<>();
                    }
                    objBuf.add(obj);
                }
            }
        }
    }

    /**
     * Append a byte block to an accumulation buffer.
     *
     * @param existing the existing buffer (may be {@code null})
     * @param block the new block to append (not {@code null})
     * @return the combined buffer (not {@code null})
     */
    private static byte[] appendBytes(byte[] existing, byte[] block) {
        if (existing == null) {
            return block;
        }
        byte[] combined = Arrays.copyOf(existing, existing.length + block.length);
        System.arraycopy(block, 0, combined, existing.length, block.length);
        return combined;
    }

    // ---- Utility methods ----

    /**
     * Read exactly {@code n} bytes from the input stream.
     *
     * @param n the number of bytes to read
     * @return a byte array of length {@code n} (not {@code null})
     * @throws EOFException if the stream ends before all bytes are read
     */
    private byte[] readNBytes(int n) throws IOException {
        byte[] buf = new byte[n];
        in.readFully(buf);
        return buf;
    }

    /**
     * Skip exactly {@code n} bytes from the input stream.
     *
     * @param n the number of bytes to skip
     * @throws EOFException if the stream ends before all bytes are skipped
     */
    private void skipNBytes(long n) throws IOException {
        long remaining = n;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) {
                if (in.read() < 0) {
                    throw new EOFException("unexpected end of stream while skipping");
                }
                remaining--;
            } else {
                remaining -= skipped;
            }
        }
    }

    /**
     * Map a single-character primitive type code to its {@link ClassDesc}.
     *
     * @param typeCode the primitive type code character
     * @return the corresponding class descriptor (not {@code null})
     * @throws StreamCorruptedException if the type code is not a valid primitive
     */
    private static ClassDesc primitiveClassDesc(char typeCode) throws StreamCorruptedException {
        return switch (typeCode) {
            case 'B' -> ClassDesc.ofDescriptor("B");
            case 'C' -> ClassDesc.ofDescriptor("C");
            case 'D' -> ClassDesc.ofDescriptor("D");
            case 'F' -> ClassDesc.ofDescriptor("F");
            case 'I' -> ClassDesc.ofDescriptor("I");
            case 'J' -> ClassDesc.ofDescriptor("J");
            case 'S' -> ClassDesc.ofDescriptor("S");
            case 'Z' -> ClassDesc.ofDescriptor("Z");
            default -> throw new StreamCorruptedException("invalid primitive type code: " + typeCode);
        };
    }

    /**
     * Intermediate holder for field descriptors during class descriptor parsing.
     *
     * @param name the field name
     * @param type the field type descriptor
     */
    private record FieldDesc(String name, ClassDesc type) {
    }
}
