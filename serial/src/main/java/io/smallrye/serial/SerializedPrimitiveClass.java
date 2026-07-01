package io.smallrye.serial;

import java.lang.constant.ClassDesc;

/**
 * The serialized representation of a primitive type or {@code void}.
 * <p>
 * Primitive types are not serializable, but they appear as component types
 * of primitive array class descriptors. This class provides singleton constants
 * for each primitive type and {@code void}, avoiding the need to create
 * placeholder class descriptors.
 */
public final class SerializedPrimitiveClass extends SerializedClass {

    /** The serialized class descriptor for {@code boolean}. */
    public static final SerializedPrimitiveClass BOOLEAN = new SerializedPrimitiveClass(ClassDesc.ofDescriptor("Z"));

    /** The serialized class descriptor for {@code byte}. */
    public static final SerializedPrimitiveClass BYTE = new SerializedPrimitiveClass(ClassDesc.ofDescriptor("B"));

    /** The serialized class descriptor for {@code char}. */
    public static final SerializedPrimitiveClass CHAR = new SerializedPrimitiveClass(ClassDesc.ofDescriptor("C"));

    /** The serialized class descriptor for {@code short}. */
    public static final SerializedPrimitiveClass SHORT = new SerializedPrimitiveClass(ClassDesc.ofDescriptor("S"));

    /** The serialized class descriptor for {@code int}. */
    public static final SerializedPrimitiveClass INT = new SerializedPrimitiveClass(ClassDesc.ofDescriptor("I"));

    /** The serialized class descriptor for {@code long}. */
    public static final SerializedPrimitiveClass LONG = new SerializedPrimitiveClass(ClassDesc.ofDescriptor("J"));

    /** The serialized class descriptor for {@code float}. */
    public static final SerializedPrimitiveClass FLOAT = new SerializedPrimitiveClass(ClassDesc.ofDescriptor("F"));

    /** The serialized class descriptor for {@code double}. */
    public static final SerializedPrimitiveClass DOUBLE = new SerializedPrimitiveClass(ClassDesc.ofDescriptor("D"));

    /** The serialized class descriptor for {@code void}. */
    public static final SerializedPrimitiveClass VOID = new SerializedPrimitiveClass(ClassDesc.ofDescriptor("V"));

    private SerializedPrimitiveClass(final ClassDesc classDesc) {
        super(classDesc, SerializedNull.INSTANCE);
    }

    /**
     * Return the singleton for the given primitive class descriptor.
     *
     * @param classDesc a primitive class descriptor (must not be {@code null})
     * @return the matching singleton (not {@code null})
     * @throws IllegalArgumentException if the class descriptor is not a primitive type
     */
    public static SerializedPrimitiveClass of(ClassDesc classDesc) {
        return of(classDesc.descriptorString().charAt(0));
    }

    /**
     * Return the singleton for the given type code character.
     *
     * @param typeCode the JVM type code character ({@code 'Z'}, {@code 'B'}, {@code 'C'},
     *        {@code 'S'}, {@code 'I'}, {@code 'J'}, {@code 'F'}, {@code 'D'}, or {@code 'V'})
     * @return the matching singleton (not {@code null})
     * @throws IllegalArgumentException if the type code is not a recognized primitive type code
     */
    public static SerializedPrimitiveClass of(char typeCode) {
        return switch (typeCode) {
            case 'Z' -> BOOLEAN;
            case 'B' -> BYTE;
            case 'C' -> CHAR;
            case 'S' -> SHORT;
            case 'I' -> INT;
            case 'J' -> LONG;
            case 'F' -> FLOAT;
            case 'D' -> DOUBLE;
            case 'V' -> VOID;
            default -> throw new IllegalArgumentException("Not a primitive type code: " + typeCode);
        };
    }
}
