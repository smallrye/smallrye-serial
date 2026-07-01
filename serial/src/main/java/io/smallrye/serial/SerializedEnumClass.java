package io.smallrye.serial;

import java.lang.constant.ClassDesc;

/**
 * The serialized representation of an {@code enum} class.
 * Enum class descriptors carry a serial version UID (always 0 per the serialization specification)
 * but no field layout, since enum constants are serialized by name only.
 */
public final class SerializedEnumClass extends SerializedVersionedClass {

    /**
     * Construct a new instance.
     *
     * @param classDesc the class descriptor (must not be {@code null})
     * @param classLoader the serialized class loader (must not be {@code null} but may be {@link SerializedNull#INSTANCE})
     * @param uid the serial version UID
     */
    public SerializedEnumClass(final ClassDesc classDesc, final Serialized classLoader, final long uid) {
        super(classDesc, classLoader, uid);
    }
}
