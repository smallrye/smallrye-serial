package io.smallrye.serial;

import java.lang.constant.ClassDesc;
import java.util.List;

/**
 * The serialized representation of a {@code record} class.
 * Record class descriptors carry a stream field layout (one level only, no superclass chain),
 * and instances are reconstructed via the canonical constructor during deserialization.
 */
public final class SerializedRecordClass extends SerializedFieldedClass {

    /**
     * Construct a new instance.
     *
     * @param classDesc the class descriptor (must not be {@code null})
     * @param classLoader the serialized class loader (must not be {@code null} but may be {@link SerializedNull#INSTANCE})
     * @param uid the serial version UID
     * @param fields the stream fields, sorted by {@linkplain SerialField#name() name} (must not be {@code null})
     * @param primitiveBufferSize the size of the primitive buffer for this class
     * @param objectBufferSize the size of the object buffer for this class
     */
    public SerializedRecordClass(final ClassDesc classDesc, final Serialized classLoader, final long uid,
            final List<SerialField> fields, final int primitiveBufferSize, final int objectBufferSize) {
        super(classDesc, classLoader, uid, fields, primitiveBufferSize, objectBufferSize);
    }
}
