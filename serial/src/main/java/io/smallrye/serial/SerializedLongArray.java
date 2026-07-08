package io.smallrye.serial;

import io.smallrye.common.constraint.Assert;

/**
 * The serialized representation of a {@code long[]} instance.
 */
public final class SerializedLongArray extends SerializedArray {
    private final SerializedArrayClass arrayType;
    private final long[] array;

    /**
     * Construct a new instance using {@link SerializedArrayClass#LONG_ARRAY} as the array type.
     *
     * @param array the array (must not be {@code null})
     */
    public SerializedLongArray(final long[] array) {
        this(SerializedArrayClass.LONG_ARRAY, array);
    }

    /**
     * Construct a new instance.
     *
     * @param arrayType the class descriptor for the array type (must not be {@code null};
     *        must have a {@code long} component type)
     * @param array the array (must not be {@code null})
     * @throws IllegalArgumentException if the array type's component type is not {@code long}
     */
    public SerializedLongArray(final SerializedArrayClass arrayType, final long[] array) {
        Assert.checkNotNullParam("arrayType", arrayType);
        if (arrayType.componentType() != SerializedPrimitiveClass.LONG) {
            throw new IllegalArgumentException("Array type descriptor must have long component type");
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
