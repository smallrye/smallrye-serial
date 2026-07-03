package io.smallrye.serial.impl;

import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.smallrye.serial.Deserializer;
import io.smallrye.serial.SerialContext;
import io.smallrye.serial.Serializer;
import io.smallrye.serial.spi.ObjectDeserializer;
import io.smallrye.serial.spi.ObjectSerializer;

/**
 * The implementation of a configured serial context.
 * <p>
 * This class holds the immutable, sorted lists of serialization and deserialization providers,
 * and a thread-safe class-local cache for computed per-class values.
 * Instances are created via {@link SerialContext#builder()}.
 * <p>
 * The outer class-local map uses a copy-on-write map since the number of
 * {@link ClassLocal} keys is small and bounded. Each inner per-class map uses
 * {@link ConcurrentHashMap} for efficient concurrent computation.
 */
public final class SerialContextImpl implements SerialContext {
    private static final VarHandle classLocalCacheHandle = ConstantBootstraps.fieldVarHandle(
            MethodHandles.lookup(), "classLocalCache", VarHandle.class,
            SerialContextImpl.class, Map.class).withInvokeExactBehavior();

    private final List<ObjectSerializer> serializers;
    private final List<ObjectDeserializer> deserializers;
    @SuppressWarnings({ "unused", "FieldMayBeFinal" }) // accessed via VarHandle
    private volatile Map<ClassLocal<?>, ConcurrentHashMap<Class<?>, Object>> classLocalCache = Map.of();

    /**
     * Construct a new instance.
     *
     * @param serializers the sorted, immutable list of serializers (must not be {@code null})
     * @param deserializers the sorted, immutable list of deserializers (must not be {@code null})
     */
    public SerialContextImpl(final List<ObjectSerializer> serializers, final List<ObjectDeserializer> deserializers) {
        this.serializers = serializers;
        this.deserializers = deserializers;
    }

    /**
     * {@inheritDoc}
     */
    public Serializer createSerializer() {
        return new SerializerImpl(this);
    }

    /**
     * {@inheritDoc}
     */
    public Deserializer createDeserializer() {
        return new DeserializerImpl(this);
    }

    /**
     * {@return the immutable list of configured serializers, sorted by priority (not {@code null})}
     */
    public List<ObjectSerializer> serializers() {
        return serializers;
    }

    /**
     * {@return the immutable list of configured deserializers, sorted by priority (not {@code null})}
     */
    public List<ObjectDeserializer> deserializers() {
        return deserializers;
    }

    /**
     * Compute and cache a per-class value for the lifetime of this context.
     * This method is thread-safe and may be called concurrently from multiple
     * serializer or deserializer sessions.
     * <p>
     * The outer map uses copy-on-write with a volatile read/CAS loop since the number
     * of {@link ClassLocal} keys is small. The inner per-class maps use
     * {@link ConcurrentHashMap#computeIfAbsent} for lock-free concurrent computation.
     *
     * @param local the class local key (must not be {@code null})
     * @param type the class to compute data for (must not be {@code null})
     * @param <T> the type of the cached value
     * @return the computed or cached value
     */
    @SuppressWarnings("unchecked")
    public <T> T classLocal(ClassLocal<T> local, Class<?> type) {
        ConcurrentHashMap<Class<?>, Object> perClassMap = getOrCreatePerClassMap(local);
        return (T) perClassMap.computeIfAbsent(type, local.compute());
    }

    /**
     * Get or create the per-class map for the given class-local key, using copy-on-write
     * on the outer map.
     * <p>
     * {@link ClassLocal} uses identity-based {@code equals}/{@code hashCode} (inherited from
     * {@link Object}), so all map types used here — {@link HashMap}, {@link Map#copyOf} — are
     * consistent in their lookup semantics.
     */
    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<Class<?>, Object> getOrCreatePerClassMap(ClassLocal<?> local) {
        // fast path: volatile read
        Map<ClassLocal<?>, ConcurrentHashMap<Class<?>, Object>> current = (Map<ClassLocal<?>, ConcurrentHashMap<Class<?>, Object>>) classLocalCacheHandle
                .getVolatile(this);
        ConcurrentHashMap<Class<?>, Object> perClassMap = current.get(local);
        if (perClassMap != null) {
            return perClassMap;
        }
        // slow path: copy-on-write
        ConcurrentHashMap<Class<?>, Object> newMap = new ConcurrentHashMap<>();
        for (;;) {
            HashMap<ClassLocal<?>, ConcurrentHashMap<Class<?>, Object>> updated = new HashMap<>(current);
            perClassMap = updated.putIfAbsent(local, newMap);
            if (perClassMap != null) {
                return perClassMap;
            }
            Map<ClassLocal<?>, ConcurrentHashMap<Class<?>, Object>> snapshot = Map.copyOf(updated);
            if (classLocalCacheHandle.compareAndSet(this, current, snapshot)) {
                return newMap;
            }
            // CAS failed; re-read and retry
            current = (Map<ClassLocal<?>, ConcurrentHashMap<Class<?>, Object>>) classLocalCacheHandle.getVolatile(this);
            perClassMap = current.get(local);
            if (perClassMap != null) {
                return perClassMap;
            }
        }
    }
}
