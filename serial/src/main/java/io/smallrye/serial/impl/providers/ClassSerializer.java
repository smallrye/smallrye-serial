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
import io.smallrye.serial.SerializedClass;
import io.smallrye.serial.SerializedEnumClass;
import io.smallrye.serial.SerializedExternalizableClass;
import io.smallrye.serial.SerializedKnownClassLoader;
import io.smallrye.serial.SerializedNonSerializableClass;
import io.smallrye.serial.SerializedPrimitiveClass;
import io.smallrye.serial.SerializedRecordClass;
import io.smallrye.serial.SerializedSerializableClass;
import io.smallrye.serial.SerializedSpecialSerializableClass;
import io.smallrye.serial.impl.Primitive;
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
                    ? SerializedKnownClassLoader.forBootClassLoader()
                    : ctxt.serialize(cl);

            if (clazz.isArray()) {
                Class<?> compType = clazz.getComponentType();
                Primitive primitive = Primitive.forClass(compType);
                if (primitive != null) {
                    return switch (primitive) {
                        case BOOLEAN -> SerializedArrayClass.BOOLEAN_ARRAY;
                        case BYTE -> SerializedArrayClass.BYTE_ARRAY;
                        case CHAR -> SerializedArrayClass.CHAR_ARRAY;
                        case SHORT -> SerializedArrayClass.SHORT_ARRAY;
                        case INT -> SerializedArrayClass.INT_ARRAY;
                        case LONG -> SerializedArrayClass.LONG_ARRAY;
                        case FLOAT -> SerializedArrayClass.FLOAT_ARRAY;
                        case DOUBLE -> SerializedArrayClass.DOUBLE_ARRAY;
                        case VOID -> throw new IllegalArgumentException("void[] is not serializable");
                    };
                }
                SerializedClass componentType = ctxt.serialize(compType, SerializedClass.class);
                return new SerializedArrayClass(cd, classLoader, ObjectStreamClass.lookup(clazz).getSerialVersionUID(),
                        componentType);
            }

            ObjectStreamClass osc = ObjectStreamClass.lookup(clazz);
            if (osc == null) {
                return new SerializedNonSerializableClass(cd, classLoader);
            }

            long uid = osc.getSerialVersionUID();

            if (Enum.class.isAssignableFrom(clazz)) {
                SerializedClass superClass = ctxt.serialize(clazz.getSuperclass(), SerializedClass.class);
                return new SerializedEnumClass(cd, classLoader, uid, superClass);
            }

            if (Externalizable.class.isAssignableFrom(clazz)) {
                SerializedClass superClass = null;
                Class<?> sup = clazz.getSuperclass();
                if (sup != null && Serializable.class.isAssignableFrom(sup)) {
                    superClass = ctxt.serialize(sup, SerializedClass.class);
                }
                return new SerializedExternalizableClass(cd, classLoader, uid, superClass);
            }

            if (clazz.isRecord()) {
                // the JDK does not assign offsets to ObjectStreamField objects for record classes,
                // so we compute our own sequential layout
                SerialField[] fields = computeRecordFields(osc);
                return new SerializedRecordClass(cd, classLoader, uid, List.of(fields), computePrimitiveBufferSize(fields),
                        computeObjectBufferSize(fields));
            }

            // fielded class (regular serializable) — compute field layout
            SerialField[] fields = computeFields(osc);

            // regular Serializable — walk superclass chain
            SerializedSerializableClass superClass = null;
            Class<?> sup = clazz.getSuperclass();
            if (sup != null && Serializable.class.isAssignableFrom(sup)) {
                superClass = ctxt.serialize(sup, SerializedSerializableClass.class);
            }
            try {
                return (SerializedSerializableClass) newSerializedSerializableClass.invokeExact(
                        cd, classLoader, superClass, List.of(fields), computePrimitiveBufferSize(fields),
                        computeObjectBufferSize(fields), uid, WriteUtil.hasWriteObject(clazz));
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
        Arrays.sort(jdkFields, Comparator.comparing(ObjectStreamField::getName));
        SerialField[] result = new SerialField[jdkFields.length];
        for (int i = 0; i < jdkFields.length; i++) {
            result[i] = new SerialField(jdkFields[i].getName(),
                    ClassDesc.ofDescriptor(jdkFields[i].getType().descriptorString()),
                    jdkFields[i].getOffset());
        }
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
        Arrays.sort(jdkFields, Comparator.comparing(ObjectStreamField::getName));
        SerialField[] result = new SerialField[jdkFields.length];
        // assign offsets in name-sorted order
        int primOffset = 0, objOffset = 0;
        for (int i = 0; i < jdkFields.length; i++) {
            final ObjectStreamField jdkField = jdkFields[i];
            ClassDesc type = Util.classDesc(jdkField.getType());
            int offset;
            if (type.isPrimitive()) {
                offset = primOffset;
                primOffset += Util.primitiveSizeOf(type.descriptorString().charAt(0));
            } else {
                offset = objOffset++;
            }
            result[i] = new SerialField(jdkField.getName(), type, offset);
        }
        return result;
    }

    /**
     * Compute the primitive buffer size from an array of fields with valid offsets.
     *
     * @param fields the fields to compute sizes from
     * @return the primitive buffer size
     */
    private static int computePrimitiveBufferSize(SerialField[] fields) {
        int size = 0;
        for (SerialField field : fields) {
            if (field.isPrimitive()) {
                int end = field.primitiveSize() + field.offset();
                if (end > size) {
                    size = end;
                }
            }
        }
        return size;
    }

    /**
     * Compute the object buffer size from an array of fields with valid offsets.
     *
     * @param fields the fields to compute sizes from
     * @return the object buffer size
     */
    private static int computeObjectBufferSize(SerialField[] fields) {
        int size = 0;
        for (SerialField field : fields) {
            if (!field.isPrimitive()) {
                int end = field.offset() + 1;
                if (end > size) {
                    size = end;
                }
            }
        }
        return size;
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
