package io.smallrye.serial.impl;

import java.io.IOException;
import java.io.NotSerializableException;
import java.util.List;

import io.smallrye.serial.Serialized;
import io.smallrye.serial.spi.ObjectSerializer;

/**
 * The per-call serialization context, providing chain-of-responsibility
 * delegation through the configured serializer providers.
 * <p>
 * Each instance tracks its position in the provider chain for a single
 * object being serialized.
 */
public final class SerializerContextImpl implements ObjectSerializer.Context {
    private final SerializerImpl session;
    private final Object object;
    private int current;

    /**
     * Construct a new instance.
     *
     * @param session the serializer session (must not be {@code null})
     * @param object the object being serialized (must not be {@code null})
     */
    SerializerContextImpl(final SerializerImpl session, final Object object) {
        this.session = session;
        this.object = object;
    }

    /**
     * {@inheritDoc}
     */
    public void preSetSerialized(final Serialized serialized) {
        session.preSetSerialized(object, serialized);
    }

    /**
     * {@inheritDoc}
     */
    public Serialized next() throws IOException {
        List<ObjectSerializer> serializers = session.context().serializers();
        if (current == serializers.size()) {
            throw new NotSerializableException(object.getClass().getName());
        }
        try {
            Serialized serialized = serializers.get(current++).serialize(this, object);
            preSetSerialized(serialized);
            return serialized;
        } finally {
            current--;
        }
    }

    /**
     * {@inheritDoc}
     */
    public Serialized serialize(final Object object) throws IOException {
        return session.serialize(object);
    }

    /**
     * {@inheritDoc}
     */
    public boolean hasSerialized(final Object object) {
        return session.hasSerialized(object);
    }

    /**
     * Compute and cache a per-class value for the lifetime of the enclosing serial context.
     *
     * @param local the class local key (must not be {@code null})
     * @param type the class to compute data for (must not be {@code null})
     * @param <T> the type of the cached value
     * @return the computed or cached value
     */
    public <T> T classLocal(ClassLocal<T> local, Class<?> type) {
        return session.context().classLocal(local, type);
    }
}
