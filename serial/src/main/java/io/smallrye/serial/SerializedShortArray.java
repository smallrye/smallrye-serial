package io.smallrye.serial;

import io.smallrye.common.constraint.Assert;

/**
 * The serialized representation of a {@code short[]} instance.
 */
public final class SerializedShortArray extends SerializedArray {
    private final SerializedArrayClass arrayType;
    private final short[] array;

    /**
     * Construct a new instance using {@link SerializedArrayClass#SHORT_ARRAY} as the array type.
     *
     * @param array the array (must not be {@code null})
     */
    public SerializedShortArray(final short[] array) {
        this(SerializedArrayClass.SHORT_ARRAY, array);
    }

    /**
     * Construct a new instance.
     *
     * @param arrayType the class descriptor for the array type (must not be {@code null};
     *        must have a {@code short} component type)
     * @param array the array (must not be {@code null})
     * @throws IllegalArgumentException if the array type's component type is not {@code short}
     */
    public SerializedShortArray(final SerializedArrayClass arrayType, final short[] array) {
        Assert.checkNotNullParam("arrayType", arrayType);
        if (arrayType.componentType() != SerializedPrimitiveClass.SHORT) {
            throw new IllegalArgumentException("Array type descriptor must have short component type");
        }
        this.arrayType = arrayType;
        this.array = array.clone();
    }

    /**
     * {@inheritDoc}
     */
    public int length() {
        return array.length;
    }

    /**
     * {@return the array corresponding to this serialized representation (not {@code null})}
     */
    public short[] asArray() {
        return array.clone();
    }

    /**
     * {@return the class descriptor for the array type (not {@code null})}
     */
    public SerializedArrayClass arrayType() {
        return arrayType;
    }
}
