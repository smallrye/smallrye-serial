package io.smallrye.serial;

import io.smallrye.common.constraint.Assert;

/**
 * The serialized representation of a {@code boolean[]} instance.
 */
public final class SerializedBooleanArray extends SerializedArray {
    private final SerializedArrayClass arrayType;
    private final boolean[] array;

    /**
     * Construct a new instance using {@link SerializedArrayClass#BOOLEAN_ARRAY} as the array type.
     *
     * @param array the array (must not be {@code null})
     */
    public SerializedBooleanArray(final boolean[] array) {
        this(SerializedArrayClass.BOOLEAN_ARRAY, array);
    }

    /**
     * Construct a new instance.
     *
     * @param arrayType the class descriptor for the array type (must not be {@code null};
     *        must have a {@code boolean} component type)
     * @param array the array (must not be {@code null})
     * @throws IllegalArgumentException if the array type's component type is not {@code boolean}
     */
    public SerializedBooleanArray(final SerializedArrayClass arrayType, final boolean[] array) {
        Assert.checkNotNullParam("arrayType", arrayType);
        if (arrayType.componentType() != SerializedPrimitiveClass.BOOLEAN) {
            throw new IllegalArgumentException("Array type descriptor must have boolean component type");
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
