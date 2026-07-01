package io.smallrye.serial.impl.providers;

import java.io.IOException;

import io.smallrye.serial.Serialized;
import io.smallrye.serial.SerializedBuiltInClassLoader;
import io.smallrye.serial.spi.ObjectSerializer;

public final class BuiltInClassLoaderSerializer implements ObjectSerializer {
    /**
     * Construct a new instance.
     */
    public BuiltInClassLoaderSerializer() {
    }

    public Serialized serialize(final Context ctxt, final Object object) throws IOException {
        if (object == ClassLoader.getPlatformClassLoader()) {
            return SerializedBuiltInClassLoader.forPlatformClassLoader();
        } else if (object == ClassLoader.getSystemClassLoader()) {
            return SerializedBuiltInClassLoader.forAppClassLoader();
        } else {
            return ctxt.next();
        }
    }

    public int priority() {
        return PRIORITY_CLASS_LOADER;
    }
}
