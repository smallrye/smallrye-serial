package io.smallrye.serial;

import io.smallrye.common.constraint.Assert;

/**
 * The serialized representation of a {@code boolean[]} instance.
 */
public final class SerializedBooleanArray extends SerializedArray {
    private final SerializedArrayClass arrayType;
    private final boolean[] array;

    /**
     * Construct a new instance.
     *
     * @param arrayType the class descriptor for the array type (must not be {@code null})
     * @param array the array (must not be {@code null})
     */
    public SerializedBooleanArray(final SerializedArrayClass arrayType, final boolean[] array) {
        this.arrayType = Assert.checkNotNullParam("arrayType", arrayType);
        this.array = array.clone();
    }

    /**
     * {@return the array corresponding to this serialized representation (not {@code null})}
     */
    public boolean[] asArray() {
        return array.clone();
    }

    /**
     * {@return the class descriptor for the array type (not {@code null})}
     */
    public SerializedArrayClass arrayType() {
        return arrayType;
    }
}
