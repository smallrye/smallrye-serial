package io.smallrye.serial;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

import io.smallrye.common.constraint.Assert;
import io.smallrye.serial.impl.SerialContextImpl;
import io.smallrye.serial.impl.providers.ArrayClassSerializer;
import io.smallrye.serial.impl.providers.ArrayDeserializer;
import io.smallrye.serial.impl.providers.ArraySerializer;
import io.smallrye.serial.impl.providers.ClassDeserializer;
import io.smallrye.serial.impl.providers.EnumClassSerializer;
import io.smallrye.serial.impl.providers.EnumDeserializer;
import io.smallrye.serial.impl.providers.EnumSerializer;
import io.smallrye.serial.impl.providers.ExternalizableClassSerializer;
import io.smallrye.serial.impl.providers.ExternalizableDeserializer;
import io.smallrye.serial.impl.providers.ExternalizableSerializer;
import io.smallrye.serial.impl.providers.KnownClassLoaderDeserializer;
import io.smallrye.serial.impl.providers.KnownClassLoaderSerializer;
import io.smallrye.serial.impl.providers.NonSerializableClassSerializer;
import io.smallrye.serial.impl.providers.PrimitiveClassSerializer;
import io.smallrye.serial.impl.providers.ProxyDeserializer;
import io.smallrye.serial.impl.providers.ProxySerializer;
import io.smallrye.serial.impl.providers.ReadResolveDeserializer;
import io.smallrye.serial.impl.providers.RecordClassSerializer;
import io.smallrye.serial.impl.providers.RecordDeserializer;
import io.smallrye.serial.impl.providers.RecordSerializer;
import io.smallrye.serial.impl.providers.SerializableClassSerializer;
import io.smallrye.serial.impl.providers.SerializableDeserializer;
import io.smallrye.serial.impl.providers.SerializableSerializer;
import io.smallrye.serial.impl.providers.SpecialClassSerializer;
import io.smallrye.serial.impl.providers.StringDeserializer;
import io.smallrye.serial.impl.providers.StringSerializer;
import io.smallrye.serial.impl.providers.WriteReplaceSerializer;
import io.smallrye.serial.spi.ObjectDeserializer;
import io.smallrye.serial.spi.ObjectSerializer;
import io.smallrye.serial.spi.Prioritized;

/**
 * A configured serialization context that holds the set of serialization and
 * deserialization providers.
 * <p>
 * A serial context is thread-safe and may be shared across threads.
 * Individual {@link Serializer} and {@link Deserializer} instances created
 * from this context are not thread-safe and should be used from a single thread
 * (or with external synchronization).
 * <p>
 * Use {@link #builder()} to create and configure a new instance.
 */
public sealed interface SerialContext permits SerialContextImpl {

    /**
     * {@return a new builder for configuring a serial context}
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new serializer that uses this context's configured providers.
     * The returned serializer maintains its own identity map for tracking
     * previously serialized objects.
     *
     * @return a new serializer (not {@code null})
     */
    Serializer createSerializer();

    /**
     * Create a new deserializer that uses this context's configured providers.
     * The returned deserializer maintains its own identity map for tracking
     * previously deserialized objects.
     *
     * @return a new deserializer (not {@code null})
     */
    Deserializer createDeserializer();

    /**
     * A builder for configuring and creating {@link SerialContext} instances.
     */
    final class Builder {
        private final List<ObjectSerializer> serializers = new ArrayList<>();
        private final List<ObjectDeserializer> deserializers = new ArrayList<>();

        Builder() {
        }

        /**
         * Add the default built-in providers for standard Java serialization support.
         * This includes providers for {@code writeReplace}/{@code readResolve},
         * {@link Class}, built-in class loaders, enums, strings, dynamic proxies,
         * arrays, {@link java.io.Externalizable}, and {@link java.io.Serializable}.
         *
         * @return this builder
         */
        public Builder addDefaultProviders() {
            serializers.add(new WriteReplaceSerializer());
            serializers.add(new PrimitiveClassSerializer());
            serializers.add(new SpecialClassSerializer());
            serializers.add(new ArrayClassSerializer());
            serializers.add(new EnumClassSerializer());
            serializers.add(new ExternalizableClassSerializer());
            serializers.add(new RecordClassSerializer());
            serializers.add(new SerializableClassSerializer());
            serializers.add(new NonSerializableClassSerializer());
            serializers.add(new KnownClassLoaderSerializer());
            serializers.add(new EnumSerializer());
            serializers.add(new StringSerializer());
            serializers.add(new ProxySerializer());
            serializers.add(new RecordSerializer());
            serializers.add(new ArraySerializer());
            serializers.add(new ExternalizableSerializer());
            serializers.add(new SerializableSerializer());

            deserializers.add(new ReadResolveDeserializer());
            deserializers.add(new ClassDeserializer());
            deserializers.add(new KnownClassLoaderDeserializer());
            deserializers.add(new EnumDeserializer());
            deserializers.add(new StringDeserializer());
            deserializers.add(new ProxyDeserializer());
            deserializers.add(new RecordDeserializer());
            deserializers.add(new ArrayDeserializer());
            deserializers.add(new ExternalizableDeserializer());
            deserializers.add(new SerializableDeserializer());
            return this;
        }

        /**
         * Discover and add providers from the given class loader using {@link ServiceLoader}.
         *
         * @param classLoader the class loader to search for providers (must not be {@code null})
         * @return this builder
         */
        public Builder addProvidersFrom(final ClassLoader classLoader) {
            Assert.checkNotNullParam("classLoader", classLoader);
            ServiceLoader.load(ObjectSerializer.class, classLoader).forEach(serializers::add);
            ServiceLoader.load(ObjectDeserializer.class, classLoader).forEach(deserializers::add);
            return this;
        }

        /**
         * Add a specific serializer instance.
         *
         * @param serializer the serializer to add (must not be {@code null})
         * @return this builder
         */
        public Builder addSerializer(final ObjectSerializer serializer) {
            serializers.add(Assert.checkNotNullParam("serializer", serializer));
            return this;
        }

        /**
         * Add a specific deserializer instance.
         *
         * @param deserializer the deserializer to add (must not be {@code null})
         * @return this builder
         */
        public Builder addDeserializer(final ObjectDeserializer deserializer) {
            deserializers.add(Assert.checkNotNullParam("deserializer", deserializer));
            return this;
        }

        /**
         * Build and return the configured {@link SerialContext}.
         * Providers are sorted by {@linkplain Prioritized#priority() priority} in descending order
         * (highest priority first).
         *
         * @return the new serial context (not {@code null})
         */
        public SerialContext build() {
            List<ObjectSerializer> sortedSerializers = new ArrayList<>(serializers);
            sortedSerializers.sort(Comparator.comparingInt(Prioritized::priority).reversed());
            List<ObjectDeserializer> sortedDeserializers = new ArrayList<>(deserializers);
            sortedDeserializers.sort(Comparator.comparingInt(Prioritized::priority).reversed());
            return new SerialContextImpl(List.copyOf(sortedSerializers), List.copyOf(sortedDeserializers));
        }
    }
}
