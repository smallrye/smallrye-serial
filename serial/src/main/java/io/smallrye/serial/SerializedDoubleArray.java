package io.smallrye.serial;

import io.smallrye.common.constraint.Assert;

/**
 * The serialized representation of a {@code double[]} instance.
 */
public final class SerializedDoubleArray extends SerializedArray {
    private final SerializedArrayClass arrayType;
    private final double[] array;

    /**
     * Construct a new instance using {@link SerializedArrayClass#DOUBLE_ARRAY} as the array type.
     *
     * @param array the array (must not be {@code null})
     */
    public SerializedDoubleArray(final double[] array) {
        this(SerializedArrayClass.DOUBLE_ARRAY, array);
    }

    /**
     * Construct a new instance.
     *
     * @param arrayType the class descriptor for the array type (must not be {@code null};
     *        must have a {@code double} component type)
     * @param array the array (must not be {@code null})
     * @throws IllegalArgumentException if the array type's component type is not {@code double}
     */
    public SerializedDoubleArray(final SerializedArrayClass arrayType, final double[] array) {
        Assert.checkNotNullParam("arrayType", arrayType);
        if (arrayType.componentType() != SerializedPrimitiveClass.DOUBLE) {
            throw new IllegalArgumentException("Array type descriptor must have double component type");
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
    public double[] asArray() {
        return array.clone();
    }

    /**
     * {@return the class descriptor for the array type (not {@code null})}
     */
    public SerializedArrayClass arrayType() {
        return arrayType;
    }
}
