package io.smallrye.serial;

import io.smallrye.common.constraint.Assert;

/**
 * The serialized representation of a {@code long[]} instance.
 */
public final class SerializedLongArray extends SerializedArray {
    private final SerializedArrayClass arrayType;
    private final long[] array;

    /**
     * Construct a new instance.
     *
     * @param arrayType the class descriptor for the array type (must not be {@code null})
     * @param array the array (must not be {@code null})
     */
    public SerializedLongArray(final SerializedArrayClass arrayType, final long[] array) {
        this.arrayType = Assert.checkNotNullParam("arrayType", arrayType);
        this.array = array.clone();
    }

    /**
     * {@return the array corresponding to this serialized representation (not {@code null})}
     */
    public long[] asArray() {
        return array.clone();
    }

    /**
     * {@return the class descriptor for the array type (not {@code null})}
     */
    public SerializedArrayClass arrayType() {
        return arrayType;
    }
}
