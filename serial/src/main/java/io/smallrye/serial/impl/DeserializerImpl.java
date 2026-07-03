package io.smallrye.serial.impl;

import java.io.IOException;
import java.util.IdentityHashMap;

import io.smallrye.common.constraint.Assert;
import io.smallrye.serial.Deserializer;
import io.smallrye.serial.Serialized;
import io.smallrye.serial.SerializedNull;

/**
 * A non-thread-safe deserializer session that maintains an identity map
 * of previously deserialized objects.
 * <p>
 * Each call to {@link #deserialize(Serialized)} walks the configured provider
 * chain via a fresh {@link DeserializerContextImpl}.
 */
public final class DeserializerImpl implements Deserializer {
    private final SerialContextImpl context;
    private final IdentityHashMap<Serialized, Object> deserializedObjects = new IdentityHashMap<>();

    /**
     * Construct a new instance.
     *
     * @param context the configured serial context (must not be {@code null})
     */
    DeserializerImpl(final SerialContextImpl context) {
        this.context = context;
    }

    /**
     * {@inheritDoc}
     */
    public Object deserialize(final Serialized serialized) throws IOException, ClassNotFoundException {
        Assert.checkNotNullParam("serialized", serialized);
        if (serialized instanceof SerializedNull) {
            return null;
        }
        if (deserializedObjects.containsKey(serialized)) {
            return deserializedObjects.get(serialized);
        }
        Object obj = new DeserializerContextImpl(this, serialized).next();
        deserializedObjects.put(serialized, obj);
        return obj;
    }

    /**
     * {@inheritDoc}
     */
    public boolean hasDeserialized(final Serialized serialized) {
        return deserializedObjects.containsKey(serialized);
    }

    /**
     * Pre-set the deserialized object for a serialized representation before deserialization
     * is complete, allowing circular references to be resolved.
     *
     * @param serialized the serialized representation (must not be {@code null})
     * @param object the interim deserialized object (must not be {@code null})
     */
    void preSetObject(final Serialized serialized, final Object object) {
        deserializedObjects.put(serialized, object);
    }

    /**
     * {@return the configured serial context (not {@code null})}
     */
    SerialContextImpl context() {
        return context;
    }
}
