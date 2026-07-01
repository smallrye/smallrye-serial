package io.smallrye.serial;

import java.lang.constant.ClassDesc;

/**
 * The serialized representation of a class that does not implement {@link java.io.Serializable}.
 * Non-serializable class descriptors carry only a class descriptor and class loader.
 */
public final class SerializedNonSerializableClass extends SerializedClass {

    /**
     * Construct a new instance.
     *
     * @param classDesc the class descriptor (must not be {@code null})
     * @param classLoader the serialized class loader (must not be {@code null} but may be {@link SerializedNull#INSTANCE})
     */
    public SerializedNonSerializableClass(final ClassDesc classDesc, final Serialized classLoader) {
        super(classDesc, classLoader);
    }
}
