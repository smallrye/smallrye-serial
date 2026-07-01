package io.smallrye.serial;

import java.lang.constant.ClassDesc;

import io.smallrye.common.constraint.Assert;

/**
 * A descriptor for a single field in a serialized class's stream field layout.
 * <p>
 * Each field has a {@linkplain #name() name}, a {@linkplain #type() type descriptor},
 * and an {@linkplain #offset() offset} into the appropriate data buffer.
 * Primitive fields are stored at byte offsets within a primitive byte buffer;
 * object (reference) fields are stored at slot offsets within an object reference array.
 *
 * @param name the field name (not {@code null})
 * @param type the field's type descriptor (not {@code null})
 * @param offset the field's offset in the appropriate buffer (primitive byte offset or object slot index)
 */
public record SerialField(String name, ClassDesc type, int offset) {

    /**
     * Compact constructor that validates parameters.
     */
    public SerialField {
        Assert.checkNotNullParam("name", name);
        Assert.checkNotNullParam("type", type);
        Assert.checkMinimumParameter("offset", 0, offset);
    }

    /**
     * {@return {@code true} if this field holds a primitive value, {@code false} if it holds an object reference}
     */
    public boolean isPrimitive() {
        return type.isPrimitive();
    }

    /**
     * {@return the single-character type code for this field's type}
     * For primitive fields, this is one of {@code B}, {@code C}, {@code D}, {@code F},
     * {@code I}, {@code J}, {@code S}, or {@code Z}.
     * For object fields, this is {@code L}.
     * For array fields, this is {@code [}.
     */
    public char typeCode() {
        return type.descriptorString().charAt(0);
    }

    /**
     * {@return the byte size of this field's primitive type, or 0 if this is not a primitive field}
     */
    public int primitiveSize() {
        return switch (typeCode()) {
            case 'B', 'Z' -> 1;
            case 'C', 'S' -> 2;
            case 'I', 'F' -> 4;
            case 'J', 'D' -> 8;
            default -> 0;
        };
    }
}
