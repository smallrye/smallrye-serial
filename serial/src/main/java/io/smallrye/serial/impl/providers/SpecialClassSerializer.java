package io.smallrye.serial.impl.providers;

import java.io.IOException;
import java.util.Map;

import io.smallrye.serial.Serialized;
import io.smallrye.serial.SerializedSpecialSerializableClass;
import io.smallrye.serial.spi.ObjectSerializer;

/**
 * Serializer that handles special {@link Class} objects ({@link String} and {@link Enum}).
 */
public final class SpecialClassSerializer implements ObjectSerializer {
    /** Construct a new instance. */
    public SpecialClassSerializer() {
    }

    private static final Map<Class<?>, SerializedSpecialSerializableClass> SPECIALS = Map.of(
            Enum.class, SerializedSpecialSerializableClass.ENUM,
            String.class, SerializedSpecialSerializableClass.STRING);

    public Serialized serialize(final Context ctxt, final Object object) throws IOException {
        if (object instanceof Class<?> clazz) {
            SerializedSpecialSerializableClass special = SPECIALS.get(clazz);
            if (special != null) {
                return special;
            }
        }
        return ctxt.next();
    }

    public int priority() {
        return PRIORITY_SPECIAL_CLASS;
    }
}
