package io.smallrye.serial;

import io.smallrye.common.constraint.Assert;

/**
 * The serialized representation of a {@code float[]} instance.
 */
public final class SerializedFloatArray extends SerializedArray {
    private final SerializedArrayClass arrayType;
    private final float[] array;

    /**
     * Construct a new instance using {@link SerializedArrayClass#FLOAT_ARRAY} as the array type.
     *
     * @param array the array (must not be {@code null})
     */
    public SerializedFloatArray(final float[] array) {
        this(SerializedArrayClass.FLOAT_ARRAY, array);
    }

    /**
     * Construct a new instance.
     *
     * @param arrayType the class descriptor for the array type (must not be {@code null};
     *        must have a {@code float} component type)
     * @param array the array (must not be {@code null})
     * @throws IllegalArgumentException if the array type's component type is not {@code float}
     */
    public SerializedFloatArray(final SerializedArrayClass arrayType, final float[] array) {
        Assert.checkNotNullParam("arrayType", arrayType);
        if (arrayType.componentType() != SerializedPrimitiveClass.FLOAT) {
            throw new IllegalArgumentException("Array type descriptor must have float component type");
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
    public float[] asArray() {
        return array.clone();
    }

    /**
     * {@return the class descriptor for the array type (not {@code null})}
     */
    public SerializedArrayClass arrayType() {
        return arrayType;
    }
}
