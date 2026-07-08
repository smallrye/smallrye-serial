package io.smallrye.serial;

import io.smallrye.common.constraint.Assert;

/**
 * The serialized representation of a {@code char[]} instance.
 */
public final class SerializedCharArray extends SerializedArray {
    private final SerializedArrayClass arrayType;
    private final char[] array;

    /**
     * Construct a new instance using {@link SerializedArrayClass#CHAR_ARRAY} as the array type.
     *
     * @param array the array (must not be {@code null})
     */
    public SerializedCharArray(final char[] array) {
        this(SerializedArrayClass.CHAR_ARRAY, array);
    }

    /**
     * Construct a new instance.
     *
     * @param arrayType the class descriptor for the array type (must not be {@code null};
     *        must have a {@code char} component type)
     * @param array the array (must not be {@code null})
     * @throws IllegalArgumentException if the array type's component type is not {@code char}
     */
    public SerializedCharArray(final SerializedArrayClass arrayType, final char[] array) {
        Assert.checkNotNullParam("arrayType", arrayType);
        if (arrayType.componentType() != SerializedPrimitiveClass.CHAR) {
            throw new IllegalArgumentException("Array type descriptor must have char component type");
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
