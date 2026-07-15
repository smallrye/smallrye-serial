package io.smallrye.serial.impl.providers;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectStreamClass;
import java.io.Serializable;

import io.smallrye.serial.Serialized;
import io.smallrye.serial.SerializedClass;
import io.smallrye.serial.SerializedExternalizableClass;
import io.smallrye.serial.impl.Util;
import io.smallrye.serial.spi.ObjectSerializer;

/**
 * Serializer that handles {@link Externalizable} {@link Class} objects.
 */
public final class ExternalizableClassSerializer implements ObjectSerializer {
    /** Construct a new instance. */
    public ExternalizableClassSerializer() {
    }

    public Serialized serialize(final Context ctxt, final Object object) throws IOException {
        if (object instanceof Class<?> clazz && Externalizable.class.isAssignableFrom(clazz)) {
            Serialized classLoader = ClassSerializerUtil.serializeClassLoader(clazz, ctxt);
            long uid = ObjectStreamClass.lookupAny(clazz).getSerialVersionUID();
            SerializedClass superClass = null;
            Class<?> sup = clazz.getSuperclass();
            if (sup != null && Serializable.class.isAssignableFrom(sup)) {
                superClass = ctxt.serialize(sup, SerializedClass.class);
            }
            return new SerializedExternalizableClass(Util.classDesc(clazz), classLoader, uid, superClass);
        }
        return ctxt.next();
    }

    public int priority() {
        return PRIORITY_EXTERNALIZABLE_CLASS;
    }
}
