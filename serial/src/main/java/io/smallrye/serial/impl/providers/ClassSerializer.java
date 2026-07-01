package io.smallrye.serial.impl.providers;

import static java.lang.invoke.MethodHandles.lookup;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import io.smallrye.serial.SerialField;
import io.smallrye.serial.Serialized;
import io.smallrye.serial.SerializedArrayClass;
import io.smallrye.serial.SerializedBuiltInClassLoader;
import io.smallrye.serial.SerializedClass;
import io.smallrye.serial.SerializedEnumClass;
import io.smallrye.serial.SerializedExternalizableClass;
import io.smallrye.serial.SerializedNonSerializableClass;
import io.smallrye.serial.SerializedPrimitiveClass;
import io.smallrye.serial.SerializedRecordClass;
import io.smallrye.serial.SerializedSerializableClass;
import io.smallrye.serial.SerializedSpecialSerializableClass;
import io.smallrye.serial.impl.Util;
import io.smallrye.serial.impl.WriteUtil;
import io.smallrye.serial.spi.ObjectSerializer;

/**
 * Serializer that handles {@link Class} objects by producing the appropriate
 * {@link SerializedClass} subtype based on the class's serialization characteristics.
 */
public final class ClassSerializer implements ObjectSerializer {
    /**
     * Construct a new instance.
     */
    public ClassSerializer() {
    }

    public Serialized serialize(final Context ctxt, final Object object) throws IOException {
        if (object instanceof Class<?> clazz) {
            ClassDesc cd = Util.classDesc(clazz);

            if (clazz.isPrimitive()) {
                return SerializedPrimitiveClass.of(cd);
            }

            if (clazz == Enum.class) {
                return SerializedSpecialSerializableClass.ENUM;
            }
            if (clazz == String.class) {
                return SerializedSpecialSerializableClass.STRING;
            }

            ClassLoader cl = clazz.getClassLoader();
            Serialized classLoader = cl == null
                    ? SerializedBuiltInClassLoader.forBootClassLoader()
                    : ctxt.serialize(cl);

            if (clazz.isArray()) {
                SerializedClass componentType = (SerializedClass) ctxt.serialize(clazz.getComponentType());
                return new SerializedArrayClass(cd, classLoader, 0L, componentType);
            }

            ObjectStreamClass osc = ObjectStreamClass.lookup(clazz);
            if (osc == null) {
                return new SerializedNonSerializableClass(cd, classLoader);
            }

            long uid = osc.getSerialVersionUID();

            if (clazz.isEnum()) {
                return new SerializedEnumClass(cd, classLoader, uid);
            }

            if (Externalizable.class.isAssignableFrom(clazz)) {
                return new SerializedExternalizableClass(cd, classLoader, uid);
            }

            if (clazz.isRecord()) {
                // the JDK does not assign offsets to ObjectStreamField objects for record classes,
                // so we compute our own sequential layout
                SerialField[] fields = computeRecordFields(osc);
                int[] bufferSizes = computeBufferSizes(fields);
                return new SerializedRecordClass(cd, classLoader, uid, List.of(fields), bufferSizes[0], bufferSizes[1]);
            }

            // fielded class (regular serializable) — compute field layout
            SerialField[] fields = computeFields(osc);
            int[] bufferSizes = computeBufferSizes(fields);

            // regular Serializable — walk superclass chain
            SerializedSerializableClass superClass = null;
            Class<?> sup = clazz.getSuperclass();
            if (sup != null && Serializable.class.isAssignableFrom(sup)) {
                superClass = (SerializedSerializableClass) ctxt.serialize(sup);
            }
            try {
                return (SerializedSerializableClass) newSerializedSerializableClass.invokeExact(
                        cd, classLoader, superClass, List.of(fields), bufferSizes[0], bufferSizes[1], uid,
                        WriteUtil.hasWriteObject(clazz));
            } catch (Error | RuntimeException e) {
                throw e;
            } catch (Throwable e) {
                throw Util.sneak(e);
            }
        } else {
            return ctxt.next();
        }
    }

    /**
     * Compute the stream fields for the given class descriptor, sorted by name.
     * Bridges from JDK {@link ObjectStreamField} (which carries pre-assigned offsets for
     * regular serializable classes) to the project's decoupled field representation.
     *
     * @param osc the object stream class descriptor
     * @return a sorted array of {@link SerialField} instances
     */
    private static SerialField[] computeFields(ObjectStreamClass osc) {
        ObjectStreamField[] jdkFields = osc.getFields();
        SerialField[] result = new SerialField[jdkFields.length];
        for (int i = 0; i < jdkFields.length; i++) {
            result[i] = toSerialField(jdkFields[i]);
        }
        Arrays.sort(result, Comparator.comparing(SerialField::name));
        return result;
    }

    /**
     * Compute the stream fields for a record class, assigning sequential offsets and sorting by name.
     * The JDK does not assign offsets to {@link ObjectStreamField} objects for record classes,
     * so we compute our own sequential layout.
     *
     * @param osc the object stream class descriptor for a record class
     * @return a sorted array of {@link SerialField} instances with valid offsets
     */
    private static SerialField[] computeRecordFields(ObjectStreamClass osc) {
        // first pass: sort by name and assign offsets
        ObjectStreamField[] jdkFields = osc.getFields();
        SerialField[] result = new SerialField[jdkFields.length];
        // build unsorted entries, then sort by name, then assign offsets
        String[] names = new String[jdkFields.length];
        ClassDesc[] types = new ClassDesc[jdkFields.length];
        for (int i = 0; i < jdkFields.length; i++) {
            names[i] = jdkFields[i].getName();
            types[i] = ClassDesc.ofDescriptor(jdkFields[i].getType().descriptorString());
        }
        // create index array sorted by name
        Integer[] indices = new Integer[jdkFields.length];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = i;
        }
        Arrays.sort(indices, Comparator.comparing(idx -> names[idx]));
        // assign offsets in name-sorted order
        int primOffset = 0, objOffset = 0;
        for (int i = 0; i < indices.length; i++) {
            int idx = indices[i];
            ClassDesc type = types[idx];
            int offset;
            if (type.isPrimitive()) {
                offset = primOffset;
                primOffset += switch (type.descriptorString().charAt(0)) {
                    case 'B', 'Z' -> 1;
                    case 'C', 'S' -> 2;
                    case 'I', 'F' -> 4;
                    case 'J', 'D' -> 8;
                    default -> throw new IllegalStateException("unexpected primitive descriptor: " + type);
                };
            } else {
                offset = objOffset++;
            }
            result[i] = new SerialField(names[idx], type, offset);
        }
        return result;
    }

    /**
     * Bridge a JDK {@link ObjectStreamField} to a {@link SerialField}.
     * Uses the field's existing offset and derives the type descriptor from the live {@link Class}.
     *
     * @param osf the JDK field descriptor (must not be {@code null})
     * @return the corresponding {@link SerialField} (not {@code null})
     */
    private static SerialField toSerialField(ObjectStreamField osf) {
        ClassDesc type = ClassDesc.ofDescriptor(osf.getType().descriptorString());
        return new SerialField(osf.getName(), type, osf.getOffset());
    }

    /**
     * Compute the primitive and object buffer sizes from an array of fields with valid offsets.
     *
     * @param fields the fields to compute sizes from
     * @return a two-element array: {@code [primitiveBufferSize, objectBufferSize]}
     */
    private static int[] computeBufferSizes(SerialField[] fields) {
        int po = 0, oo = 0;
        for (SerialField field : fields) {
            if (field.isPrimitive()) {
                int end = field.primitiveSize() + field.offset();
                if (end > po) {
                    po = end;
                }
            } else {
                int end = field.offset() + 1;
                if (end > oo) {
                    oo = end;
                }
            }
        }
        return new int[] { po, oo };
    }

    public int priority() {
        return PRIORITY_CLASS;
    }

    private static final MethodHandle newSerializedSerializableClass;

    static {
        try {
            newSerializedSerializableClass = MethodHandles.privateLookupIn(SerializedSerializableClass.class, lookup())
                    .findConstructor(SerializedSerializableClass.class,
                            MethodType.methodType(void.class, ClassDesc.class, Serialized.class,
                                    SerializedSerializableClass.class, List.class, int.class, int.class, long.class,
                                    boolean.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw Util.asError(e);
        }
    }
}
