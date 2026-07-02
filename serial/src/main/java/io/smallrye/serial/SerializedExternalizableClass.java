package io.smallrye.serial;

import java.lang.constant.ClassDesc;

/**
 * The serialized representation of a class that implements {@link java.io.Externalizable}.
 * Externalizable class descriptors carry a serial version UID but no field layout,
 * since the object's data is entirely determined by its
 * {@link java.io.Externalizable#writeExternal(java.io.ObjectOutput)} method.
 * <p>
 * An externalizable class may extend a {@link java.io.Serializable} class, in which case
 * the serialization stream carries the serializable superclass chain as the
 * {@linkplain #superClass() superclass descriptor}.
 */
public final class SerializedExternalizableClass extends SerializedVersionedClass implements HasSuperClass {

    private final SerializedClass superClass;

    /**
     * Construct a new instance.
     *
     * @param classDesc the class descriptor (must not be {@code null})
     * @param classLoader the serialized class loader (must not be {@code null} but may be {@link SerializedNull#INSTANCE})
     * @param uid the serial version UID
     * @param superClass the superclass descriptor in the serialization hierarchy, or {@code null} if there is none
     */
    public SerializedExternalizableClass(final ClassDesc classDesc, final Serialized classLoader, final long uid,
            final SerializedClass superClass) {
        super(classDesc, classLoader, uid);
        this.superClass = superClass;
    }

    /**
     * {@return the superclass descriptor in the serialization hierarchy, or {@code null}
     * if there is no serializable superclass}
     */
    @Override
    public SerializedClass superClass() {
        return superClass;
    }
}
