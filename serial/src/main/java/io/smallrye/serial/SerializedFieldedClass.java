package io.smallrye.serial;

import java.lang.constant.ClassDesc;
import java.util.List;

/**
 * Abstract base for class descriptors that carry a stream field layout.
 * This includes {@linkplain SerializedSerializableClass serializable} and
 * {@linkplain SerializedRecordClass record} classes.
 */
public abstract sealed class SerializedFieldedClass extends SerializedVersionedClass
        permits SerializedSerializableClass, SerializedRecordClass {

    private final List<SerialField> fields;
    private final int primitiveBufferSize;
    private final int objectBufferSize;

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
    SerializedFieldedClass(final ClassDesc classDesc, final Serialized classLoader, final long uid,
            final List<SerialField> fields, final int primitiveBufferSize, final int objectBufferSize) {
        super(classDesc, classLoader, uid);
        this.fields = List.copyOf(fields);
        this.primitiveBufferSize = primitiveBufferSize;
        this.objectBufferSize = objectBufferSize;
    }

    /**
     * Look up a stream field by name using binary search.
     *
     * @param name the field name (must not be {@code null})
     * @return the field descriptor, or {@code null} if no field with the given name exists
     */
    public SerialField streamField(String name) {
        // binary search on the name-sorted field list
        int lo = 0, hi = fields.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int cmp = fields.get(mid).name().compareTo(name);
            if (cmp < 0) {
                lo = mid + 1;
            } else if (cmp > 0) {
                hi = mid - 1;
            } else {
                return fields.get(mid);
            }
        }
        return null;
    }

    /**
     * {@return the serializable stream field layout for this class, sorted by name}
     * The returned list excludes any {@code transient} or {@code static} fields.
     */
    public List<SerialField> streamFields() {
        return fields;
    }

    /**
     * {@return the number of stream fields in this class's field layout}
     */
    public int streamFieldCount() {
        return fields.size();
    }

    /**
     * {@return the size of the primitive field buffer needed to serialize instances of this class}
     * Note that this does not include the size of any superclass or subclass in the hierarchy.
     */
    public int primitiveBufferSize() {
        return primitiveBufferSize;
    }

    /**
     * {@return the size of the object field buffer needed to serialize instances of this class}
     * Note that this does not include the size of any superclass or subclass in the hierarchy.
     */
    public int objectBufferSize() {
        return objectBufferSize;
    }
}
