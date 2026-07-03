package io.smallrye.serial;

import io.smallrye.common.constraint.Assert;

/**
 * The serialized representation of an {@code int[]} instance.
 */
public final class SerializedIntArray extends SerializedArray {
    private final SerializedArrayClass arrayType;
    private final int[] array;

    /**
     * Construct a new instance.
     *
     * @param arrayType the class descriptor for the array type (must not be {@code null})
     * @param array the array (must not be {@code null})
     */
    public SerializedIntArray(final SerializedArrayClass arrayType, final int[] array) {
        this.arrayType = Assert.checkNotNullParam("arrayType", arrayType);
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
