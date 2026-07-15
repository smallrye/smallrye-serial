package io.smallrye.serial.impl.providers;

import java.io.IOException;
import java.io.ObjectStreamClass;

import io.smallrye.serial.Serialized;
import io.smallrye.serial.SerializedClass;
import io.smallrye.serial.SerializedEnumClass;
import io.smallrye.serial.impl.Util;
import io.smallrye.serial.spi.ObjectSerializer;

/**
 * Serializer that handles enum {@link Class} objects.
 */
public final class EnumClassSerializer implements ObjectSerializer {
    /** Construct a new instance. */
    public EnumClassSerializer() {
    }

    public Serialized serialize(final Context ctxt, final Object object) throws IOException {
        if (object instanceof Class<?> clazz && Enum.class.isAssignableFrom(clazz)) {
            Serialized classLoader = ClassSerializerUtil.serializeClassLoader(clazz, ctxt);
            long uid = ObjectStreamClass.lookupAny(clazz).getSerialVersionUID();
            SerializedClass superClass = ctxt.serialize(clazz.getSuperclass(), SerializedClass.class);
            return new SerializedEnumClass(Util.classDesc(clazz), classLoader, uid, superClass);
        }
        return ctxt.next();
    }

    public int priority() {
        return PRIORITY_ENUM_CLASS;
    }
}
