package io.smallrye.serial;

import java.lang.constant.ClassDesc;

/**
 * The serialized representation of an {@code enum} class.
 * Enum class descriptors carry a serial version UID (always 0 per the serialization specification)
 * but no field layout, since enum constants are serialized by name only.
 * <p>
 * In the Java serialization wire format, every enum type class descriptor has
 * {@link java.lang.Enum} as its superclass. This is modeled by the {@link #superClass()}
 * reference, which is typically {@link SerializedSpecialSerializableClass#ENUM}.
 */
public final class SerializedEnumClass extends SerializedVersionedClass implements HasSuperClass {

    private final SerializedClass superClass;

    /**
     * Construct a new instance with {@link SerializedSpecialSerializableClass#ENUM} as the superclass.
     *
     * @param classDesc the class descriptor (must not be {@code null})
     * @param classLoader the serialized class loader (must not be {@code null} but may be {@link SerializedNull#INSTANCE})
     * @param uid the serial version UID
     */
    public SerializedEnumClass(final ClassDesc classDesc, final Serialized classLoader, final long uid) {
        this(classDesc, classLoader, uid, SerializedSpecialSerializableClass.ENUM);
    }

    /**
     * Construct a new instance with an explicit superclass descriptor.
     *
     * @param classDesc the class descriptor (must not be {@code null})
     * @param classLoader the serialized class loader (must not be {@code null} but may be {@link SerializedNull#INSTANCE})
     * @param uid the serial version UID
     * @param superClass the superclass descriptor (typically {@link SerializedSpecialSerializableClass#ENUM})
     */
    public SerializedEnumClass(final ClassDesc classDesc, final Serialized classLoader, final long uid,
            final SerializedClass superClass) {
        super(classDesc, classLoader, uid);
        this.superClass = superClass;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SerializedClass superClass() {
        return superClass;
    }
}
