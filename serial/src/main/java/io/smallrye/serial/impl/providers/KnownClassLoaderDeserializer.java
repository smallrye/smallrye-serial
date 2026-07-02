package io.smallrye.serial.impl.providers;

import java.io.IOException;

import io.smallrye.serial.Serialized;
import io.smallrye.serial.SerializedKnownClassLoader;
import io.smallrye.serial.spi.ObjectDeserializer;

/**
 * Deserializer that handles {@link SerializedKnownClassLoader} instances,
 * resolving each kind to the appropriate JVM class loader.
 * <p>
 * The {@linkplain SerializedKnownClassLoader.Kind#UNSPECIFIED unspecified} kind
 * is resolved to the thread context class loader.
 */
public final class KnownClassLoaderDeserializer implements ObjectDeserializer {
    /**
     * Construct a new instance.
     */
    public KnownClassLoaderDeserializer() {
    }

    public Object deserialize(final Context ctxt, final Serialized serialized) throws IOException, ClassNotFoundException {
        if (serialized instanceof SerializedKnownClassLoader b) {
            return b.classLoader();
        } else {
            return ctxt.next();
        }
    }

    public int priority() {
        return PRIORITY_CLASS_LOADER;
    }
}
