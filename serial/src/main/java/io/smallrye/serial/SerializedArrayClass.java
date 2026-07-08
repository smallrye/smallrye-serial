package io.smallrye.serial;

import java.io.ObjectStreamClass;
import java.lang.constant.ClassDesc;

import io.smallrye.common.constraint.Assert;

/**
 * The serialized representation of an array class.
 * Array class descriptors carry a reference to the component type's class descriptor
 * and a serial version UID, in addition to the class descriptor and class loader
 * inherited from {@link SerializedVersionedClass}.
 * <p>
 * Note: the Java serialization specification waives the requirement for matching
 * serial version UIDs on array classes, so the UID value is not significant for
 * compatibility purposes.
 */
public final class SerializedArrayClass extends SerializedVersionedClass {

    /** The array class descriptor for {@code boolean[]}. */
    public static final SerializedArrayClass BOOLEAN_ARRAY = forPrimitive(boolean[].class, SerializedPrimitiveClass.BOOLEAN);
    /** The array class descriptor for {@code byte[]}. */
    public static final SerializedArrayClass BYTE_ARRAY = forPrimitive(byte[].class, SerializedPrimitiveClass.BYTE);
    /** The array class descriptor for {@code char[]}. */
    public static final SerializedArrayClass CHAR_ARRAY = forPrimitive(char[].class, SerializedPrimitiveClass.CHAR);
    /** The array class descriptor for {@code short[]}. */
    public static final SerializedArrayClass SHORT_ARRAY = forPrimitive(short[].class, SerializedPrimitiveClass.SHORT);
    /** The array class descriptor for {@code int[]}. */
    public static final SerializedArrayClass INT_ARRAY = forPrimitive(int[].class, SerializedPrimitiveClass.INT);
    /** The array class descriptor for {@code long[]}. */
    public static final SerializedArrayClass LONG_ARRAY = forPrimitive(long[].class, SerializedPrimitiveClass.LONG);
    /** The array class descriptor for {@code float[]}. */
    public static final SerializedArrayClass FLOAT_ARRAY = forPrimitive(float[].class, SerializedPrimitiveClass.FLOAT);
    /** The array class descriptor for {@code double[]}. */
    public static final SerializedArrayClass DOUBLE_ARRAY = forPrimitive(double[].class, SerializedPrimitiveClass.DOUBLE);

    private static SerializedArrayClass forPrimitive(Class<?> arrayClass, SerializedPrimitiveClass componentType) {
        return new SerializedArrayClass(
                arrayClass.describeConstable().orElseThrow(),
                SerializedKnownClassLoader.forBootClassLoader(),
                ObjectStreamClass.lookup(arrayClass).getSerialVersionUID(),
                componentType);
    }

    private final SerializedClass componentType;

    /**
     * Construct a new instance.
     *
     * @param classDesc the class descriptor for the array type (must not be {@code null})
     * @param classLoader the serialized class loader (must not be {@code null} but may be {@link SerializedNull#INSTANCE})
     * @param uid the serial version UID (not checked for arrays per the serialization specification)
     * @param componentType the class descriptor of the array's component type (must not be {@code null})
     */
    public SerializedArrayClass(final ClassDesc classDesc, final Serialized classLoader, final long uid,
            final SerializedClass componentType) {
        super(classDesc, classLoader, uid);
        this.componentType = Assert.checkNotNullParam("componentType", componentType);
    }

    @Override
    final String computeName() {
        return descriptorString().replace('/', '.');
    }

    /**
     * {@return the class descriptor of the array's component type (not {@code null})}
     * For multi-dimensional arrays, this is itself a {@code SerializedArrayClass}.
     */
    public SerializedClass componentType() {
        return componentType;
    }

    /**
     * {@return the number of array dimensions}
     * For example, {@code String[]} has 1 dimension, {@code int[][]} has 2 dimensions.
     */
    public int dimensions() {
        int dims = 1;
        SerializedClass ct = componentType;
        while (ct instanceof SerializedArrayClass arr) {
            dims++;
            ct = arr.componentType;
        }
        return dims;
    }

    /**
     * {@return the innermost non-array component type (not {@code null})}
     * For example, for {@code String[][]}, this returns the descriptor for {@code String}.
     */
    public SerializedClass leafComponentType() {
        SerializedClass ct = componentType;
        while (ct instanceof SerializedArrayClass arr) {
            ct = arr.componentType;
        }
        return ct;
    }
}
