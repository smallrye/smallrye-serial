package io.smallrye.serial;

import io.smallrye.common.constraint.Assert;

/**
 * The serialized representation of an {@code int[]} instance.
 */
public final class SerializedIntArray extends SerializedArray {
    private final SerializedArrayClass arrayType;
    private final int[] array;

    /**
     * Construct a new instance using {@link SerializedArrayClass#INT_ARRAY} as the array type.
     *
     * @param array the array (must not be {@code null})
     */
    public SerializedIntArray(final int[] array) {
        this(SerializedArrayClass.INT_ARRAY, array);
    }

    /**
     * Construct a new instance.
     *
     * @param arrayType the class descriptor for the array type (must not be {@code null};
     *        must have an {@code int} component type)
     * @param array the array (must not be {@code null})
     * @throws IllegalArgumentException if the array type's component type is not {@code int}
     */
    public SerializedIntArray(final SerializedArrayClass arrayType, final int[] array) {
        Assert.checkNotNullParam("arrayType", arrayType);
        if (arrayType.componentType() != SerializedPrimitiveClass.INT) {
            throw new IllegalArgumentException("Array type descriptor must have int component type");
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
    public int[] asArray() {
        return array.clone();
    }

    /**
     * {@return the class descriptor for the array type (not {@code null})}
     */
    public SerializedArrayClass arrayType() {
        return arrayType;
    }
}
