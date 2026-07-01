package io.smallrye.serial;

import java.lang.constant.ClassDesc;

/**
 * Abstract base for class descriptors that carry a serial version UID.
 * This includes {@linkplain SerializedExternalizableClass externalizable},
 * {@linkplain SerializedEnumClass enum}, {@linkplain SerializedFieldedClass fielded},
 * {@linkplain SerializedArrayClass array}, and
 * {@linkplain SerializedSpecialSerializableClass special serializable} classes.
 */
public abstract sealed class SerializedVersionedClass extends SerializedClass
        permits SerializedExternalizableClass, SerializedEnumClass, SerializedFieldedClass, SerializedArrayClass,
        SerializedSpecialSerializableClass {

    private final long uid;

    /**
     * Construct a new instance.
     *
     * @param classDesc the class descriptor (must not be {@code null})
     * @param classLoader the serialized class loader (must not be {@code null} but may be {@link SerializedNull#INSTANCE})
     * @param uid the serial version UID
     */
    SerializedVersionedClass(final ClassDesc classDesc, final Serialized classLoader, final long uid) {
        super(classDesc, classLoader);
        this.uid = uid;
    }

    /**
     * {@return the serial version UID of the stream class}
     */
    public long serialVersionUID() {
        return uid;
    }
}
