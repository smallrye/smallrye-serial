package io.smallrye.serial.impl.providers;

import java.io.IOException;

import io.smallrye.serial.Serialized;
import io.smallrye.serial.SerializedBuiltInClassLoader;
import io.smallrye.serial.spi.ObjectDeserializer;

public final class BuiltInClassLoaderDeserializer implements ObjectDeserializer {
    /**
     * Construct a new instance.
     */
    public BuiltInClassLoaderDeserializer() {
    }

    public Object deserialize(final Context ctxt, final Serialized serialized) throws IOException, ClassNotFoundException {
        if (serialized instanceof SerializedBuiltInClassLoader b) {
            return b.classLoader();
        } else {
            return ctxt.next();
        }
    }

    public int priority() {
        return PRIORITY_CLASS_LOADER;
    }
}
