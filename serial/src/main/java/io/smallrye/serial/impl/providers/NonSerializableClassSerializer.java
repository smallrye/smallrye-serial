package io.smallrye.serial.impl.providers;

import java.io.IOException;

import io.smallrye.serial.Serialized;
import io.smallrye.serial.SerializedNonSerializableClass;
import io.smallrye.serial.impl.Util;
import io.smallrye.serial.spi.ObjectSerializer;

/**
 * Fallback serializer that handles non-serializable {@link Class} objects.
 */
public final class NonSerializableClassSerializer implements ObjectSerializer {
    /** Construct a new instance. */
    public NonSerializableClassSerializer() {
    }

    public Serialized serialize(final Context ctxt, final Object object) throws IOException {
        if (object instanceof Class<?> clazz) {
            Serialized classLoader = ClassSerializerUtil.serializeClassLoader(clazz, ctxt);
            return new SerializedNonSerializableClass(Util.classDesc(clazz), classLoader);
        }
        return ctxt.next();
    }

    public int priority() {
        return PRIORITY_NON_SERIALIZABLE_CLASS;
    }
}
