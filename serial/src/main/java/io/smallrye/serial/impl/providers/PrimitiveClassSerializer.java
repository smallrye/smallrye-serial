package io.smallrye.serial.impl.providers;

import java.io.IOException;

import io.smallrye.serial.Serialized;
import io.smallrye.serial.SerializedPrimitiveClass;
import io.smallrye.serial.impl.Util;
import io.smallrye.serial.spi.ObjectSerializer;

/**
 * Serializer that handles primitive {@link Class} objects.
 */
public final class PrimitiveClassSerializer implements ObjectSerializer {
    /** Construct a new instance. */
    public PrimitiveClassSerializer() {
    }

    public Serialized serialize(final Context ctxt, final Object object) throws IOException {
        if (object instanceof Class<?> clazz && clazz.isPrimitive()) {
            return SerializedPrimitiveClass.of(Util.classDesc(clazz));
        }
        return ctxt.next();
    }

    public int priority() {
        return PRIORITY_PRIMITIVE_CLASS;
    }
}
