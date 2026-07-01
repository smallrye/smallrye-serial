package io.smallrye.serial;

import java.lang.constant.ClassDesc;

import io.smallrye.common.constraint.Assert;

/**
 * The serialized representation of a {@link Class}.
 * <p>
 * This is the sealed base of the class descriptor hierarchy. Every class descriptor
 * carries a {@linkplain #classDesc() class descriptor} and a {@linkplain #classLoader() class loader}.
 * Subtypes add data specific to the serialization mechanism of the class they describe.
 *
 * @see SerializedNonSerializableClass
 * @see SerializedPrimitiveClass
 * @see SerializedVersionedClass
 */
public abstract sealed class SerializedClass extends Serialized
        permits SerializedNonSerializableClass, SerializedPrimitiveClass, SerializedVersionedClass {

    private final ClassDesc classDesc;
    private final Serialized classLoader;
    private String name;

    /**
     * Construct a new instance.
     *
     * @param classDesc the class descriptor (must not be {@code null})
     * @param classLoader the serialized class loader (must not be {@code null} but may be {@link SerializedNull#INSTANCE})
     */
    SerializedClass(final ClassDesc classDesc, final Serialized classLoader) {
        this.classDesc = Assert.checkNotNullParam("classDesc", classDesc);
        this.classLoader = Assert.checkNotNullParam("classLoader", classLoader);
    }

    /**
     * {@return the class descriptor for this serialized class (not {@code null})}
     */
    public ClassDesc classDesc() {
        return classDesc;
    }

    /**
     * {@return the name of the serialized class in {@link Class#getName()} format (not {@code null})}
     * The name is lazily computed from the {@linkplain #classDesc() class descriptor} and cached.
     */
    public String name() {
        String n = name;
        if (n == null) {
            name = n = computeName();
        }
        return n;
    }

    /**
     * Compute the {@link Class#getName()}-format name from the stored class descriptor.
     */
    private String computeName() {
        String desc = classDesc.descriptorString();
        if (classDesc.isArray()) {
            return desc.replace('/', '.');
        }
        if (classDesc.isPrimitive()) {
            return switch (desc.charAt(0)) {
                case 'B' -> "byte";
                case 'C' -> "char";
                case 'D' -> "double";
                case 'F' -> "float";
                case 'I' -> "int";
                case 'J' -> "long";
                case 'S' -> "short";
                case 'Z' -> "boolean";
                case 'V' -> "void";
                default -> throw new IllegalStateException();
            };
        }
        return desc.substring(1, desc.length() - 1).replace('/', '.');
    }

    /**
     * {@return the serialized representation of a class loader which can load this class (not {@code null})}
     */
    public Serialized classLoader() {
        return classLoader;
    }
}
