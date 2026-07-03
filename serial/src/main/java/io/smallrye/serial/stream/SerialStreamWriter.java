package io.smallrye.serial.stream;

import static io.smallrye.serial.stream.SerialProtocol.*;

import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UTFDataFormatException;
import java.lang.constant.ConstantDescs;
import java.util.ArrayList;
import java.util.List;

import io.smallrye.common.constraint.Assert;
import io.smallrye.serial.SerialData;
import io.smallrye.serial.SerialField;
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
import io.smallrye.serial.SerializedFieldedClass;
import io.smallrye.serial.SerializedFloatArray;
import io.smallrye.serial.SerializedIntArray;
import io.smallrye.serial.SerializedKnownClassLoader;
import io.smallrye.serial.SerializedLongArray;
import io.smallrye.serial.SerializedNull;
import io.smallrye.serial.SerializedObjectArray;
import io.smallrye.serial.SerializedProxyClass;
import io.smallrye.serial.SerializedProxyObject;
import io.smallrye.serial.SerializedRecord;
import io.smallrye.serial.SerializedRecordClass;
import io.smallrye.serial.SerializedSerializable;
import io.smallrye.serial.SerializedSerializableClass;
import io.smallrye.serial.SerializedShortArray;
import io.smallrye.serial.SerializedString;
import io.smallrye.serial.StreamData;
import io.smallrye.serial.impl.IntMap;
import io.smallrye.serial.impl.ModifiedUtf8;
import io.smallrye.serial.impl.Util;

/**
 * Writes a {@link Serialized} object graph into the standard Java serialization
 * wire format (consumable by {@link java.io.ObjectInputStream}).
 * <p>
 * Instances are created via the {@link #builder(OutputStream) builder}. The builder's
 * {@link Builder#build() build()} method eagerly writes the stream magic and version
 * header. Subsequent calls to {@link #writeSerialized(Serialized)} write top-level
 * objects to the stream.
 * <p>
 * The writer maintains a handle table for back-reference deduplication: if the same
 * {@link Serialized} instance (by identity) is written more than once, subsequent
 * occurrences are emitted as {@code TC_REFERENCE}.
 * <p>
 * This class implements {@link SerialOutput} so that the
 * {@link ClassAnnotationWriter} callback can write arbitrary data (block data
 * and {@link Serialized} objects) to the stream during class annotation processing.
 *
 * @see SerialStreamReader
 */
public final class SerialStreamWriter implements SerialOutput, Closeable {

    // Block data buffer size (matches JDK's ObjectOutputStream)
    private static final int BLOCK_BUF_SIZE = 1024;

    // ---- Instance fields ----

    /** Direct output (bypasses block data mode). */
    private final DataOutputStream out;
    /** Optional callback for writing class annotation data. */
    private final ClassAnnotationWriter classAnnotationWriter;
    /** Identity-based handle table mapping Serialized nodes to wire handles. */
    private final IntMap<Serialized> handles = IntMap.identity();
    /** Separate handle table for raw type strings (field type descriptors). */
    private final IntMap<String> typeStringHandles = IntMap.equality();
    /** Next handle number to assign (0-based; wire handle = nextHandle + BASE_WIRE_HANDLE). */
    private int nextHandle;

    // Shared buffer for both block-data mode (SerialOutput API) and raw structural writes.
    // In block-data mode, draining prepends a TC_BLOCKDATA/TC_BLOCKDATALONG header.
    // In raw mode, draining writes bytes directly with no header.
    // The two modes never interleave: structural writes always enter after exitBlockDataMode().
    private final byte[] blockBuf = new byte[BLOCK_BUF_SIZE];
    private int blockPos;
    private boolean blockDataMode;

    // Lazily created synthetic class descriptor for enum superclass
    private SerializedEnumClass enumSuperDesc;

    // ---- Builder ----

    /**
     * Create a new builder for a {@link SerialStreamWriter} that writes to the given output stream.
     *
     * @param out the output stream to write the serialization data to (must not be {@code null})
     * @return a new builder (not {@code null})
     */
    public static Builder builder(OutputStream out) {
        return new Builder(Assert.checkNotNullParam("out", out));
    }

    /**
     * Builder for {@link SerialStreamWriter} instances.
     */
    public static final class Builder {
        private final OutputStream out;
        private ClassAnnotationWriter classAnnotationWriter;

        private Builder(OutputStream out) {
            this.out = out;
        }

        /**
         * Set the callback for writing class annotation data.
         * If not set, no annotation data is written (matching the default
         * {@link java.io.ObjectOutputStream} behavior).
         *
         * @param writer the annotation writer callback (may be {@code null})
         * @return this builder
         */
        public Builder classAnnotationWriter(ClassAnnotationWriter writer) {
            this.classAnnotationWriter = writer;
            return this;
        }

        /**
         * Build the writer and eagerly write the stream header (magic number and version).
         *
         * @return the new writer (not {@code null})
         * @throws IOException if an I/O error occurs while writing the header
         */
        public SerialStreamWriter build() throws IOException {
            return new SerialStreamWriter(this);
        }
    }

    // ---- Constructor ----

    private SerialStreamWriter(Builder builder) throws IOException {
        this.out = new DataOutputStream(builder.out);
        this.classAnnotationWriter = builder.classAnnotationWriter;
        out.writeShort(STREAM_MAGIC);
        out.writeShort(STREAM_VERSION);
    }

    // ---- Public API ----

    /**
     * Write a {@link Serialized} object graph to the stream.
     * This is the strongly-typed entry point for writing serialized data.
     * If the given instance has already been written (by identity), a
     * {@code TC_REFERENCE} back-reference is emitted instead.
     *
     * @param serialized the serialized object to write (must not be {@code null})
     * @throws IOException if an I/O error occurs
     */
    public void writeSerialized(Serialized serialized) throws IOException {
        exitBlockDataMode();
        writeSerialized0(Assert.checkNotNullParam("serialized", serialized));
    }

    /** {@inheritDoc} */
    @Override
    public void close() throws IOException {
        exitBlockDataMode();
        out.close();
    }

    /** {@inheritDoc} */
    @Override
    public void flush() throws IOException {
        if (blockDataMode && blockPos > 0) {
            drainBlockBuffer();
        }
        out.flush();
    }

    // ---- DataOutput methods (write through block data mode) ----

    /** {@inheritDoc} */
    @Override
    public void write(int b) throws IOException {
        enterBlockDataMode();
        if (blockPos >= BLOCK_BUF_SIZE) {
            drainBlockBuffer();
        }
        blockBuf[blockPos++] = (byte) b;
    }

    /** {@inheritDoc} */
    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    /** {@inheritDoc} */
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        enterBlockDataMode();
        while (len > 0) {
            int avail = BLOCK_BUF_SIZE - blockPos;
            int n = Math.min(avail, len);
            System.arraycopy(b, off, blockBuf, blockPos, n);
            blockPos += n;
            off += n;
            len -= n;
            if (blockPos >= BLOCK_BUF_SIZE) {
                drainBlockBuffer();
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void writeBoolean(boolean v) throws IOException {
        write(v ? 1 : 0);
    }

    /** {@inheritDoc} */
    @Override
    public void writeByte(int v) throws IOException {
        write(v);
    }

    /** {@inheritDoc} */
    @Override
    public void writeShort(int v) throws IOException {
        enterBlockDataMode();
        if (blockPos + 2 > BLOCK_BUF_SIZE) {
            drainBlockBuffer();
        }
        Util.BE16.set(blockBuf, blockPos, (short) v);
        blockPos += 2;
    }

    /** {@inheritDoc} */
    @Override
    public void writeChar(int v) throws IOException {
        writeShort(v);
    }

    /** {@inheritDoc} */
    @Override
    public void writeInt(int v) throws IOException {
        enterBlockDataMode();
        if (blockPos + 4 > BLOCK_BUF_SIZE) {
            drainBlockBuffer();
        }
        Util.BE32.set(blockBuf, blockPos, v);
        blockPos += 4;
    }

    /** {@inheritDoc} */
    @Override
    public void writeLong(long v) throws IOException {
        enterBlockDataMode();
        if (blockPos + 8 > BLOCK_BUF_SIZE) {
            drainBlockBuffer();
        }
        Util.BE64.set(blockBuf, blockPos, v);
        blockPos += 8;
    }

    /** {@inheritDoc} */
    @Override
    public void writeFloat(float v) throws IOException {
        writeInt(Float.floatToRawIntBits(v));
    }

    /** {@inheritDoc} */
    @Override
    public void writeDouble(double v) throws IOException {
        writeLong(Double.doubleToRawLongBits(v));
    }

    /** {@inheritDoc} */
    @Override
    public void writeBytes(String s) throws IOException {
        for (int i = 0; i < s.length(); i++) {
            write((byte) s.charAt(i));
        }
    }

    /** {@inheritDoc} */
    @Override
    public void writeChars(String s) throws IOException {
        for (int i = 0; i < s.length(); i++) {
            writeChar(s.charAt(i));
        }
    }

    /** {@inheritDoc} */
    @Override
    public void writeUTF(String s) throws IOException {
        long utfLen = ModifiedUtf8.encodedLength(s);
        if (utfLen > 0xFFFF) {
            throw new UTFDataFormatException("String too long for writeUTF: " + utfLen + " bytes");
        }
        writeShort((int) utfLen);
        // encode character-by-character directly through the block-data buffer;
        // no intermediate byte[] needed since write(int) already buffers into blockBuf
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c > 0 && c <= 0x7f) {
                write(c);
            } else if (c <= 0x7ff) {
                write(0xc0 | 0x1f & c >> 6);
                write(0x80 | 0x3f & c);
            } else {
                write(0xe0 | 0x0f & c >> 12);
                write(0x80 | 0x3f & c >> 6);
                write(0x80 | 0x3f & c);
            }
        }
    }

    // ---- Internal dispatch ----

    /**
     * Core dispatch: write a Serialized node to the stream, emitting TC_REFERENCE
     * for previously-written nodes or delegating to the appropriate type-specific method.
     */
    private void writeSerialized0(Serialized serialized) throws IOException {
        if (serialized instanceof SerializedNull) {
            out.writeByte(TC_NULL);
            return;
        }
        if (writeReferenceIfSeen(serialized)) {
            return;
        }
        if (serialized instanceof SerializedString s) {
            writeNewString(s);
        } else if (serialized instanceof SerializedSerializable s) {
            writeSerializable(s);
        } else if (serialized instanceof SerializedExternalizable e) {
            writeExternalizable(e);
        } else if (serialized instanceof SerializedEnum e) {
            writeEnum(e);
        } else if (serialized instanceof SerializedRecord r) {
            writeRecord(r);
        } else if (serialized instanceof SerializedProxyObject p) {
            writeProxyObject(p);
        } else if (serialized instanceof SerializedObjectArray a) {
            writeObjectArray(a);
        } else if (serialized instanceof SerializedBooleanArray a) {
            writePrimitiveArray(a);
        } else if (serialized instanceof SerializedByteArray a) {
            writePrimitiveArray(a);
        } else if (serialized instanceof SerializedCharArray a) {
            writePrimitiveArray(a);
        } else if (serialized instanceof SerializedShortArray a) {
            writePrimitiveArray(a);
        } else if (serialized instanceof SerializedIntArray a) {
            writePrimitiveArray(a);
        } else if (serialized instanceof SerializedLongArray a) {
            writePrimitiveArray(a);
        } else if (serialized instanceof SerializedFloatArray a) {
            writePrimitiveArray(a);
        } else if (serialized instanceof SerializedDoubleArray a) {
            writePrimitiveArray(a);
        } else if (serialized instanceof SerializedClass c) {
            writeClassObject(c);
        } else {
            throw new IOException("Unsupported Serialized type: " + serialized.getClass().getName());
        }
    }

    // ---- String writing ----

    /**
     * Write a new string value as TC_STRING or TC_LONGSTRING.
     * The handle is assigned after the type code, before the string data.
     */
    private void writeNewString(SerializedString s) throws IOException {
        String str = s.string();
        long utfLen = ModifiedUtf8.encodedLength(str);
        if (utfLen > Integer.MAX_VALUE) {
            throw new IOException("String modified UTF-8 encoding exceeds maximum array size");
        }
        if (utfLen <= 0xFFFF) {
            out.writeByte(TC_STRING);
            assignHandle(s);
            out.writeShort((int) utfLen);
        } else {
            out.writeByte(TC_LONGSTRING);
            assignHandle(s);
            out.writeLong(utfLen);
        }
        rawEncodeUtf(str);
        rawFlush();
    }

    /**
     * Write a raw UTF-8 string in DataOutput.writeUTF format (2-byte length + modified UTF-8 bytes).
     * Used for class names, field names, and interface names — NOT as a TC_STRING object.
     */
    private void writeRawUTF(String s) throws IOException {
        long utfLen = ModifiedUtf8.encodedLength(s);
        if (utfLen > 0xFFFF) {
            throw new UTFDataFormatException("String too long for raw UTF: " + utfLen + " bytes");
        }
        out.writeShort((int) utfLen);
        if (utfLen > 0) {
            rawEncodeUtf(s);
            rawFlush();
        }
    }

    /**
     * Write a field type descriptor string as a TC_STRING object with handle management.
     * Type strings are interned separately from Serialized handles so that identical
     * type descriptors (e.g. "Ljava.lang.String;") share a single handle via TC_REFERENCE.
     */
    private void writeTypeString(String typeString) throws IOException {
        if (typeStringHandles.containsKey(typeString)) {
            out.writeByte(TC_REFERENCE);
            out.writeInt(typeStringHandles.get(typeString) + BASE_WIRE_HANDLE);
            return;
        }
        long utfLen = ModifiedUtf8.encodedLength(typeString);
        if (utfLen <= 0xFFFF) {
            out.writeByte(TC_STRING);
            int h = nextHandle++;
            typeStringHandles.put(typeString, h);
            out.writeShort((int) utfLen);
        } else {
            out.writeByte(TC_LONGSTRING);
            int h = nextHandle++;
            typeStringHandles.put(typeString, h);
            out.writeLong(utfLen);
        }
        rawEncodeUtf(typeString);
        rawFlush();
    }

    /**
     * Encode {@code s} in modified UTF-8 into {@code blockBuf} via {@link #rawWriteByte},
     * draining to {@code out} with no block-data header whenever the buffer fills.
     * Must only be called on the structural path (i.e. when {@code !blockDataMode}).
     */
    private void rawEncodeUtf(String s) throws IOException {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c > 0 && c <= 0x7f) {
                rawWriteByte(c);
            } else if (c <= 0x7ff) {
                rawWriteByte(0xc0 | 0x1f & c >> 6);
                rawWriteByte(0x80 | 0x3f & c);
            } else {
                rawWriteByte(0xe0 | 0x0f & c >> 12);
                rawWriteByte(0x80 | 0x3f & c >> 6);
                rawWriteByte(0x80 | 0x3f & c);
            }
        }
    }

    /**
     * Buffer one byte for a raw (non-block-data) write, draining to {@code out} with no
     * header when the buffer is full.
     * Must only be called on the structural path (i.e. when {@code !blockDataMode}).
     */
    private void rawWriteByte(int b) throws IOException {
        assert !blockDataMode;
        if (blockPos >= BLOCK_BUF_SIZE) {
            rawFlush();
        }
        blockBuf[blockPos++] = (byte) b;
    }

    /**
     * Flush any bytes accumulated in {@code blockBuf} directly to {@code out}, with no
     * block-data header. Resets {@code blockPos} to zero.
     */
    private void rawFlush() throws IOException {
        if (blockPos > 0) {
            out.write(blockBuf, 0, blockPos);
            blockPos = 0;
        }
    }

    // ---- Class descriptor writing ----

    /**
     * Write a class descriptor (or TC_NULL if null, or TC_REFERENCE if already written).
     */
    private void writeClassDesc(SerializedClass classDesc) throws IOException {
        if (classDesc == null) {
            out.writeByte(TC_NULL);
            return;
        }
        if (writeReferenceIfSeen(classDesc)) {
            return;
        }
        if (classDesc instanceof SerializedArrayClass ac) {
            writeNewArrayClassDesc(ac);
        } else {
            writeNewClassDesc(classDesc);
        }
    }

    /**
     * Write a proxy class descriptor (or TC_NULL/TC_REFERENCE).
     */
    private void writeProxyClassDesc(SerializedProxyClass proxyClass) throws IOException {
        // proxy classes can't use writeReferenceIfSeen because SerializedProxyClass
        // extends Serialized, not SerializedClass; we track it in the handle table directly
        if (writeReferenceIfSeen(proxyClass)) {
            return;
        }
        out.writeByte(TC_PROXYCLASSDESC);
        assignHandle(proxyClass);
        List<String> ifaces = proxyClass.interfaceNames();
        out.writeInt(ifaces.size());
        for (String iface : ifaces) {
            writeRawUTF(iface);
        }
        writeClassAnnotation(null);
        writeClassDesc(proxyClass.superClass());
    }

    /**
     * Write a new non-proxy, non-array class descriptor.
     * Dispatches by type to determine flags, field layout, and superclass.
     */
    private void writeNewClassDesc(SerializedClass classDesc) throws IOException {
        out.writeByte(TC_CLASSDESC);
        assignHandle(classDesc);
        if (classDesc instanceof SerializedSerializableClass c) {
            writeRawUTF(c.name());
            out.writeLong(c.serialVersionUID());
            out.writeByte(SC_SERIALIZABLE | (c.hasWriteMethod() ? SC_WRITE_METHOD : 0));
            writeFieldDescs(c);
            writeClassAnnotation(c);
            writeClassDesc(c.superClass());
        } else if (classDesc instanceof SerializedRecordClass c) {
            writeRawUTF(c.name());
            out.writeLong(c.serialVersionUID());
            out.writeByte(SC_SERIALIZABLE);
            writeFieldDescs(c);
            writeClassAnnotation(c);
            out.writeByte(TC_NULL);
        } else if (classDesc instanceof SerializedExternalizableClass c) {
            writeRawUTF(c.name());
            out.writeLong(c.serialVersionUID());
            out.writeByte(SC_EXTERNALIZABLE | SC_BLOCK_DATA);
            out.writeShort(0); // no fields
            writeClassAnnotation(c);
            writeClassDesc(c.superClass());
        } else if (classDesc instanceof SerializedEnumClass c) {
            writeRawUTF(c.name());
            out.writeLong(c.serialVersionUID());
            out.writeByte(SC_SERIALIZABLE | SC_ENUM);
            out.writeShort(0); // no fields
            writeClassAnnotation(c);
            if (Util.classDescEquals(c.classDesc(), ConstantDescs.CD_Enum)) {
                out.writeByte(TC_NULL);
            } else {
                writeClassDesc(getEnumSuperDesc());
            }
        } else {
            throw new IOException(
                    "Cannot write class descriptor for " + classDesc.getClass().getName()
                            + " (" + classDesc.name() + ")");
        }
    }

    /**
     * Write an array class descriptor.
     * Array class descriptors have SC_SERIALIZABLE, 0 fields, no annotation, and no superclass.
     */
    private void writeNewArrayClassDesc(SerializedArrayClass classDesc) throws IOException {
        out.writeByte(TC_CLASSDESC);
        assignHandle(classDesc);
        writeRawUTF(classDesc.name());
        out.writeLong(classDesc.serialVersionUID());
        out.writeByte(SC_SERIALIZABLE);
        out.writeShort(0); // arrays have no declared fields at the wire level
        writeClassAnnotation(classDesc);
        out.writeByte(TC_NULL);
    }

    /**
     * Write field descriptors for a fielded class (serializable or record).
     * Primitives: 1-byte type code + UTF field name.
     * Objects/arrays: 1-byte type code + UTF field name + TC_STRING type descriptor.
     */
    private void writeFieldDescs(SerializedFieldedClass c) throws IOException {
        List<SerialField> fields = c.streamFields();
        out.writeShort(c.streamFieldCount());
        // primitives first (name-sorted = offset-sorted), then objects
        for (SerialField field : fields) {
            if (field.isPrimitive()) {
                out.writeByte(field.typeCode());
                writeRawUTF(field.name());
            }
        }
        for (SerialField field : fields) {
            if (!field.isPrimitive()) {
                out.writeByte(field.typeCode());
                writeRawUTF(field.name());
                // wire format uses dots, not slashes
                String typeDesc = field.type().descriptorString().replace('/', '.');
                writeTypeString(typeDesc);
            }
        }
    }

    /**
     * Invoke the class annotation writer callback (if set), then write TC_ENDBLOCKDATA.
     * The callback may use this writer's {@link SerialOutput} methods to write data.
     */
    private void writeClassAnnotation(SerializedClass classDesc) throws IOException {
        if (classAnnotationWriter != null && classDesc != null) {
            classAnnotationWriter.write(classDesc, this);
            exitBlockDataMode();
        }
        out.writeByte(TC_ENDBLOCKDATA);
    }

    // ---- Serializable object writing ----

    /**
     * Write a serializable object: TC_OBJECT + class desc + handle + per-level data.
     * Walks the class descriptor chain root-to-leaf, looking up data per level.
     */
    private void writeSerializable(SerializedSerializable s) throws IOException {
        out.writeByte(TC_OBJECT);
        writeClassDesc(s.serializedClass());
        assignHandle(s);
        List<SerializedSerializableClass> levels = new ArrayList<>();
        for (var c = s.serializedClass(); c != null; c = c.superClass()) {
            levels.add(0, c);
        }
        for (var c : levels) {
            SerialData level = s.dataFor(c);
            if (level != null) {
                writeClassLevelData(level, c.hasWriteMethod());
            } else {
                writeEmptyClassLevelData(c);
            }
        }
    }

    /**
     * Write an empty class level's data: zero-filled primitive fields, null object fields,
     * and TC_ENDBLOCKDATA if the class has a write method.
     *
     * @param c the class descriptor for the empty level
     */
    private void writeEmptyClassLevelData(SerializedSerializableClass c) throws IOException {
        int primSize = c.primitiveBufferSize();
        if (primSize > 0) {
            out.write(new byte[primSize]);
        }
        int objSize = c.objectBufferSize();
        for (int i = 0; i < objSize; i++) {
            out.writeByte(TC_NULL);
        }
        if (c.hasWriteMethod()) {
            out.writeByte(TC_ENDBLOCKDATA);
        }
    }

    /**
     * Write a record object: TC_OBJECT + class desc + handle + single level of field data.
     * Records are indistinguishable from serializable objects at the wire level except
     * they have no SC_WRITE_METHOD flag and no stream data.
     */
    private void writeRecord(SerializedRecord r) throws IOException {
        out.writeByte(TC_OBJECT);
        writeClassDesc(r.recordClass());
        assignHandle(r);
        writeClassLevelData(r.fieldData(), false);
    }

    /**
     * Write one class level's field data.
     *
     * @param level the serial data for this class level
     * @param hasWriteMethod if true, write stream data and TC_ENDBLOCKDATA after fields
     */
    private void writeClassLevelData(SerialData level, boolean hasWriteMethod) throws IOException {
        // primitive field data: written directly (not in block data mode)
        StreamData.OfBytes primData = level.primitiveFieldData();
        if (primData.size() > 0) {
            out.write(primData.getBytes());
        }
        // object field data: each object written in field order
        StreamData.OfObjects objData = level.objectFieldData();
        for (int i = 0; i < objData.size(); i++) {
            writeSerialized0(objData.getObject(i));
        }
        // stream data (from writeObject methods) + end marker
        if (hasWriteMethod) {
            writeAnnotationContents(level.streamData());
            out.writeByte(TC_ENDBLOCKDATA);
        }
    }

    // ---- Externalizable object writing ----

    /**
     * Write an externalizable object: TC_OBJECT + class desc + handle + block data.
     */
    private void writeExternalizable(SerializedExternalizable e) throws IOException {
        out.writeByte(TC_OBJECT);
        writeClassDesc(e.serializedClass());
        assignHandle(e);
        writeAnnotationContents(e.data());
        out.writeByte(TC_ENDBLOCKDATA);
    }

    // ---- Enum writing ----

    /**
     * Write an enum constant: TC_ENUM + class desc + handle + constant name.
     */
    private void writeEnum(SerializedEnum e) throws IOException {
        out.writeByte(TC_ENUM);
        writeClassDesc(e.enumClass());
        assignHandle(e);
        writeSerialized0(e.constantName());
    }

    // ---- Proxy object writing ----

    /**
     * Write a proxy object: TC_OBJECT + proxy class desc + handle + invocation handler.
     * The proxy class descriptor uses TC_PROXYCLASSDESC, and its superclass is
     * java.lang.reflect.Proxy (which carries the "h" field for the invocation handler).
     */
    private void writeProxyObject(SerializedProxyObject p) throws IOException {
        out.writeByte(TC_OBJECT);
        writeProxyClassDesc(p.proxyClass());
        assignHandle(p);
        // the Proxy superclass level has one object field: the invocation handler
        writeSerialized0(p.invocationHandler());
    }

    // ---- Array writing ----

    /**
     * Write an object array: TC_ARRAY + class desc + handle + count + elements.
     */
    private void writeObjectArray(SerializedObjectArray a) throws IOException {
        out.writeByte(TC_ARRAY);
        writeClassDesc(a.arrayType());
        assignHandle(a);
        int len = a.length();
        out.writeInt(len);
        for (int i = 0; i < len; i++) {
            writeSerialized0(a.get(i));
        }
    }

    /**
     * Write a primitive array: TC_ARRAY + class desc + handle + count + raw elements.
     * Each element is written in big-endian format matching the wire protocol.
     */
    private void writePrimitiveArray(SerializedBooleanArray a) throws IOException {
        out.writeByte(TC_ARRAY);
        writeClassDesc(a.arrayType());
        assignHandle(a);
        boolean[] arr = a.asArray();
        out.writeInt(arr.length);
        for (boolean v : arr) {
            out.writeByte(v ? 1 : 0);
        }
    }

    /** @see #writePrimitiveArray(SerializedBooleanArray) */
    private void writePrimitiveArray(SerializedByteArray a) throws IOException {
        out.writeByte(TC_ARRAY);
        writeClassDesc(a.arrayType());
        assignHandle(a);
        byte[] arr = a.asArray();
        out.writeInt(arr.length);
        out.write(arr);
    }

    /** @see #writePrimitiveArray(SerializedBooleanArray) */
    private void writePrimitiveArray(SerializedCharArray a) throws IOException {
        out.writeByte(TC_ARRAY);
        writeClassDesc(a.arrayType());
        assignHandle(a);
        char[] arr = a.asArray();
        out.writeInt(arr.length);
        for (char v : arr) {
            out.writeShort(v);
        }
    }

    /** @see #writePrimitiveArray(SerializedBooleanArray) */
    private void writePrimitiveArray(SerializedShortArray a) throws IOException {
        out.writeByte(TC_ARRAY);
        writeClassDesc(a.arrayType());
        assignHandle(a);
        short[] arr = a.asArray();
        out.writeInt(arr.length);
        for (short v : arr) {
            out.writeShort(v);
        }
    }

    /** @see #writePrimitiveArray(SerializedBooleanArray) */
    private void writePrimitiveArray(SerializedIntArray a) throws IOException {
        out.writeByte(TC_ARRAY);
        writeClassDesc(a.arrayType());
        assignHandle(a);
        int[] arr = a.asArray();
        out.writeInt(arr.length);
        for (int v : arr) {
            out.writeInt(v);
        }
    }

    /** @see #writePrimitiveArray(SerializedBooleanArray) */
    private void writePrimitiveArray(SerializedLongArray a) throws IOException {
        out.writeByte(TC_ARRAY);
        writeClassDesc(a.arrayType());
        assignHandle(a);
        long[] arr = a.asArray();
        out.writeInt(arr.length);
        for (long v : arr) {
            out.writeLong(v);
        }
    }

    /** @see #writePrimitiveArray(SerializedBooleanArray) */
    private void writePrimitiveArray(SerializedFloatArray a) throws IOException {
        out.writeByte(TC_ARRAY);
        writeClassDesc(a.arrayType());
        assignHandle(a);
        float[] arr = a.asArray();
        out.writeInt(arr.length);
        for (float v : arr) {
            out.writeInt(Float.floatToRawIntBits(v));
        }
    }

    /** @see #writePrimitiveArray(SerializedBooleanArray) */
    private void writePrimitiveArray(SerializedDoubleArray a) throws IOException {
        out.writeByte(TC_ARRAY);
        writeClassDesc(a.arrayType());
        assignHandle(a);
        double[] arr = a.asArray();
        out.writeInt(arr.length);
        for (double v : arr) {
            out.writeLong(Double.doubleToRawLongBits(v));
        }
    }

    // ---- TC_CLASS writing ----

    /**
     * Write a Class value: TC_CLASS + class desc + handle.
     * This is used when a {@link Class} object appears as a serialized value
     * (not as part of an object's structural class descriptor).
     */
    private void writeClassObject(SerializedClass c) throws IOException {
        out.writeByte(TC_CLASS);
        writeClassDesc(c);
        // assign a separate handle for the Class *value* (distinct from the class desc handle)
        nextHandle++;
    }

    // ---- Stream data / annotation contents ----

    /**
     * Write annotation contents: a sequence of block data (from OfBytes) and objects
     * (from OfObjects). The caller is responsible for writing the terminating TC_ENDBLOCKDATA.
     */
    private void writeAnnotationContents(List<StreamData> streamData) throws IOException {
        for (StreamData sd : streamData) {
            if (sd instanceof StreamData.OfBytes bytes) {
                byte[] data = bytes.getBytes();
                if (data.length > 0) {
                    writeBlockDataDirect(data);
                }
            } else if (sd instanceof StreamData.OfObjects objects) {
                for (int i = 0; i < objects.size(); i++) {
                    writeSerialized0(objects.getObject(i));
                }
            }
        }
    }

    /**
     * Write a byte array as a single block data element with the appropriate header.
     * Unlike the block data buffering used by the DataOutput methods, this writes
     * the block directly to the output stream with an explicit header.
     */
    private void writeBlockDataDirect(byte[] data) throws IOException {
        if (data.length <= 255) {
            out.writeByte(TC_BLOCKDATA);
            out.writeByte(data.length);
        } else {
            out.writeByte(TC_BLOCKDATALONG);
            out.writeInt(data.length);
        }
        out.write(data);
    }

    // ---- Block data mode management ----

    /**
     * Enter block data mode (for DataOutput method calls).
     * Subsequent byte writes are buffered until the buffer is full or
     * block data mode is exited.
     */
    private void enterBlockDataMode() {
        blockDataMode = true;
    }

    /**
     * Exit block data mode, flushing any buffered block data to the output stream.
     */
    private void exitBlockDataMode() throws IOException {
        if (blockDataMode) {
            drainBlockBuffer();
            blockDataMode = false;
        }
    }

    /**
     * Flush the block data buffer to the output stream with the appropriate
     * TC_BLOCKDATA or TC_BLOCKDATALONG header.
     */
    private void drainBlockBuffer() throws IOException {
        if (blockPos == 0) {
            return;
        }
        if (blockPos <= 255) {
            out.writeByte(TC_BLOCKDATA);
            out.writeByte(blockPos);
        } else {
            out.writeByte(TC_BLOCKDATALONG);
            out.writeInt(blockPos);
        }
        out.write(blockBuf, 0, blockPos);
        blockPos = 0;
    }

    // ---- Handle management ----

    /**
     * Assign a new handle to the given Serialized node.
     *
     * @param s the Serialized node to assign a handle to (not {@code null})
     * @return the assigned handle number
     */
    private int assignHandle(Serialized s) {
        int h = nextHandle++;
        handles.put(s, h);
        return h;
    }

    /**
     * If the given Serialized node has already been written, emit TC_REFERENCE
     * and return {@code true}. Otherwise return {@code false}.
     */
    private boolean writeReferenceIfSeen(Serialized s) throws IOException {
        if (handles.containsKey(s)) {
            out.writeByte(TC_REFERENCE);
            out.writeInt(handles.get(s) + BASE_WIRE_HANDLE);
            return true;
        }
        return false;
    }

    // ---- Synthetic superclass descriptors ----

    /**
     * {@return the synthetic class descriptor for {@code java.lang.Enum}, lazily created}
     * Used as the superclass descriptor for enum type class descriptors.
     */
    private SerializedEnumClass getEnumSuperDesc() {
        SerializedEnumClass desc = enumSuperDesc;
        if (desc == null) {
            desc = new SerializedEnumClass(ConstantDescs.CD_Enum, SerializedKnownClassLoader.forBootClassLoader(), 0L);
            enumSuperDesc = desc;
        }
        return desc;
    }

}
