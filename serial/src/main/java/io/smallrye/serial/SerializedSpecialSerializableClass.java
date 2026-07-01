package io.smallrye.serial;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;

/**
 * The serialized representation of a class that implements {@link java.io.Serializable} but uses
 * special serialization semantics outside of the normal {@link java.io.Serializable Serializable} mechanism.
 * Examples include {@link Enum} (whose instances are serialized by name) and {@link String}
 * (which has a dedicated wire format).
 * <p>
 * Singleton constants are provided for the known special classes.
 */
public final class SerializedSpecialSerializableClass extends SerializedVersionedClass {

    /**
     * The serialized representation of {@link Enum java.lang.Enum}.
     */
    public static final SerializedSpecialSerializableClass ENUM = new SerializedSpecialSerializableClass(ConstantDescs.CD_Enum,
            SerializedBuiltInClassLoader.forBootClassLoader(), 0L);

    /**
     * The serialized representation of {@link String java.lang.String}.
     */
    public static final SerializedSpecialSerializableClass STRING = new SerializedSpecialSerializableClass(
            ConstantDescs.CD_String, SerializedBuiltInClassLoader.forBootClassLoader(), -6849794470754667710L);

    /**
     * Construct a new instance.
     *
     * @param classDesc the class descriptor (must not be {@code null})
     * @param classLoader the serialized class loader (must not be {@code null})
     * @param uid the serial version UID
     */
    private SerializedSpecialSerializableClass(final ClassDesc classDesc, final Serialized classLoader, final long uid) {
        super(classDesc, classLoader, uid);
    }
}
