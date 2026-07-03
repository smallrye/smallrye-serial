package io.smallrye.serial.impl;

import java.io.IOException;
import java.io.NotSerializableException;
import java.util.List;

import io.smallrye.serial.Serialized;
import io.smallrye.serial.spi.ObjectDeserializer;

/**
 * The per-call deserialization context, providing chain-of-responsibility
 * delegation through the configured deserializer providers.
 * <p>
 * Each instance tracks its position in the provider chain for a single
 * serialized representation being deserialized.
 */
public final class DeserializerContextImpl implements ObjectDeserializer.Context {
    private final DeserializerImpl session;
    private final Serialized serialized;
    private int current;

    /**
     * Construct a new instance.
     *
     * @param session the deserializer session (must not be {@code null})
     * @param serialized the serialized representation being deserialized (must not be {@code null})
     */
    DeserializerContextImpl(final DeserializerImpl session, final Serialized serialized) {
        this.session = session;
        this.serialized = serialized;
    }

    /**
     * {@inheritDoc}
     */
    public void preSetObject(final Object obj) {
        session.preSetObject(serialized, obj);
    }

    /**
     * {@inheritDoc}
     */
    public Object next() throws IOException, ClassNotFoundException {
        List<ObjectDeserializer> deserializers = session.context().deserializers();
        if (current == deserializers.size()) {
            throw new NotSerializableException("No deserializer available for " + serialized.getClass().getName());
        }
        try {
            Object instance = deserializers.get(current++).deserialize(this, serialized);
            preSetObject(instance);
            return instance;
        } finally {
            current--;
        }
    }

    /**
     * {@inheritDoc}
     */
    public Object deserialize(final Serialized serialized) throws IOException, ClassNotFoundException {
        return session.deserialize(serialized);
    }

    /**
     * {@inheritDoc}
     */
    public boolean hasDeserialized(final Serialized serialized) {
        return session.hasDeserialized(serialized);
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
