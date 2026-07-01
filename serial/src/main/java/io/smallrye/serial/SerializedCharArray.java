package io.smallrye.serial;

import io.smallrye.common.constraint.Assert;

/**
 * The serialized representation of a {@code char[]} instance.
 */
public final class SerializedCharArray extends SerializedArray {
    private final SerializedArrayClass arrayType;
    private final char[] array;

    /**
     * Construct a new instance.
     *
     * @param arrayType the class descriptor for the array type (must not be {@code null})
     * @param array the array (must not be {@code null})
     */
    public SerializedCharArray(final SerializedArrayClass arrayType, final char[] array) {
        this.arrayType = Assert.checkNotNullParam("arrayType", arrayType);
        this.array = array.clone();
    }

    /**
     * {@return the array corresponding to this serialized representation (not {@code null})}
     */
    public char[] asArray() {
        return array.clone();
    }

    /**
     * {@return the class descriptor for the array type (not {@code null})}
     */
    public SerializedArrayClass arrayType() {
        return arrayType;
    }
}
