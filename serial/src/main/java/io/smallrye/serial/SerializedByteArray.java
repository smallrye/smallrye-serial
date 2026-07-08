package io.smallrye.serial;

import io.smallrye.common.constraint.Assert;

/**
 * The serialized representation of a {@code byte[]} instance.
 */
public final class SerializedByteArray extends SerializedArray {
    private final SerializedArrayClass arrayType;
    private final byte[] array;

    /**
     * Construct a new instance using {@link SerializedArrayClass#BYTE_ARRAY} as the array type.
     *
     * @param array the array (must not be {@code null})
     */
    public SerializedByteArray(final byte[] array) {
        this(SerializedArrayClass.BYTE_ARRAY, array);
    }

    /**
     * Construct a new instance.
     *
     * @param arrayType the class descriptor for the array type (must not be {@code null};
     *        must have a {@code byte} component type)
     * @param array the array (must not be {@code null})
     * @throws IllegalArgumentException if the array type's component type is not {@code byte}
     */
    public SerializedByteArray(final SerializedArrayClass arrayType, final byte[] array) {
        Assert.checkNotNullParam("arrayType", arrayType);
        if (arrayType.componentType() != SerializedPrimitiveClass.BYTE) {
            throw new IllegalArgumentException("Array type descriptor must have byte component type");
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
    public byte[] asArray() {
        return array.clone();
    }

    /**
     * {@return the class descriptor for the array type (not {@code null})}
     */
    public SerializedArrayClass arrayType() {
        return arrayType;
    }
}
