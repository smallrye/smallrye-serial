package io.smallrye.serial.impl;

import java.io.IOException;
import java.util.IdentityHashMap;

import io.smallrye.serial.Serialized;
import io.smallrye.serial.SerializedNull;
import io.smallrye.serial.Serializer;

/**
 * A non-thread-safe serializer session that maintains an identity map
 * of previously serialized objects.
 * <p>
 * Each call to {@link #serialize(Object)} walks the configured provider
 * chain via a fresh {@link SerializerContextImpl}.
 */
public final class SerializerImpl implements Serializer {
    private final SerialContextImpl context;
    private final IdentityHashMap<Object, Serialized> serializedObjects = new IdentityHashMap<>();

    /**
     * Construct a new instance.
     *
     * @param context the configured serial context (must not be {@code null})
     */
    SerializerImpl(final SerialContextImpl context) {
        this.context = context;
    }

    /**
     * {@inheritDoc}
     */
    public Serialized serialize(final Object object) throws IOException {
        if (object == null) {
            return SerializedNull.INSTANCE;
        }
        Serialized serialized = serializedObjects.get(object);
        if (serialized != null) {
            return serialized;
        }
        serialized = new SerializerContextImpl(this, object).next();
        serializedObjects.put(object, serialized);
        return serialized;
    }

    /**
     * {@inheritDoc}
     */
    public boolean hasSerialized(final Object object) {
        return object == null || serializedObjects.containsKey(object);
    }

    /**
     * Pre-set the serialized representation of an object before serialization is complete,
     * allowing circular references to be resolved.
     *
     * @param object the object being serialized (must not be {@code null})
     * @param serialized the interim serialized representation (must not be {@code null})
     */
    void preSetSerialized(final Object object, final Serialized serialized) {
        serializedObjects.put(object, serialized);
    }

    /**
     * {@return the configured serial context (not {@code null})}
     */
    SerialContextImpl context() {
        return context;
    }
}
