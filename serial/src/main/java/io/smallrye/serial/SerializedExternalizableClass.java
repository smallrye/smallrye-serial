package io.smallrye.serial;

import java.lang.constant.ClassDesc;

/**
 * The serialized representation of a class that implements {@link java.io.Externalizable}.
 * Externalizable class descriptors carry a serial version UID but no field layout,
 * since the object's data is entirely determined by its
 * {@link java.io.Externalizable#writeExternal(java.io.ObjectOutput)} method.
 */
public final class SerializedExternalizableClass extends SerializedVersionedClass {

    /**
     * Construct a new instance.
     *
     * @param classDesc the class descriptor (must not be {@code null})
     * @param classLoader the serialized class loader (must not be {@code null} but may be {@link SerializedNull#INSTANCE})
     * @param uid the serial version UID
     */
    public SerializedExternalizableClass(final ClassDesc classDesc, final Serialized classLoader, final long uid) {
        super(classDesc, classLoader, uid);
    }
}
