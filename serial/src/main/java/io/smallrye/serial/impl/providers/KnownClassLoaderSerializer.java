package io.smallrye.serial.impl.providers;

import java.io.IOException;

import io.smallrye.serial.Serialized;
import io.smallrye.serial.SerializedKnownClassLoader;
import io.smallrye.serial.spi.ObjectSerializer;

public final class KnownClassLoaderSerializer implements ObjectSerializer {
    /**
     * Construct a new instance.
     */
    public KnownClassLoaderSerializer() {
    }

    public Serialized serialize(final Context ctxt, final Object object) throws IOException {
        if (object == ClassLoader.getPlatformClassLoader()) {
            return SerializedKnownClassLoader.forPlatformClassLoader();
        } else if (object == ClassLoader.getSystemClassLoader()) {
            return SerializedKnownClassLoader.forAppClassLoader();
        } else {
            return ctxt.next();
        }
    }

    public int priority() {
        return PRIORITY_CLASS_LOADER;
    }
}
