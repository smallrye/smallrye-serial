package io.smallrye.serial.impl.providers;

import static java.lang.invoke.MethodHandles.lookup;

import java.io.IOException;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

import io.smallrye.serial.SerialField;
import io.smallrye.serial.Serialized;
import io.smallrye.serial.SerializedSerializableClass;
import io.smallrye.serial.impl.Util;
import io.smallrye.serial.impl.WriteUtil;
import io.smallrye.serial.spi.ObjectSerializer;

/**
 * Serializer that handles {@link Serializable} {@link Class} objects (excluding enums,
 * externalizables, and records, which are handled by their own serializers).
 */
public final class SerializableClassSerializer implements ObjectSerializer {

    private static final MethodHandle newSerializedSerializableClass;

    static {
        try {
            newSerializedSerializableClass = MethodHandles.privateLookupIn(SerializedSerializableClass.class, lookup())
                    .findConstructor(SerializedSerializableClass.class,
                            MethodType.methodType(void.class, ClassDesc.class, Serialized.class,
                                    SerializedSerializableClass.class, List.class, int.class, int.class, long.class,
                                    boolean.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw Util.asError(e);
        }
    }

    /**
     * Construct a new instance.
     */
    public SerializableClassSerializer() {
    }

    /**
     * {@inheritDoc}
     */
    public Serialized serialize(final Context ctxt, final Object object) throws IOException {
        if (object instanceof Class<?> clazz) {
            ObjectStreamClass osc = ObjectStreamClass.lookup(clazz);
            if (osc != null) {
                Serialized classLoader = ClassSerializerUtil.serializeClassLoader(clazz, ctxt);
                ClassDesc cd = Util.classDesc(clazz);
                long uid = osc.getSerialVersionUID();
                SerialField[] fields = ClassSerializerUtil.computeFields(osc);
                SerializedSerializableClass superClass = null;
                Class<?> sup = clazz.getSuperclass();
                if (sup != null && Serializable.class.isAssignableFrom(sup)) {
                    superClass = ctxt.serialize(sup, SerializedSerializableClass.class);
                }
                try {
                    return (SerializedSerializableClass) newSerializedSerializableClass.invokeExact(
                            cd, classLoader, superClass, List.of(fields),
                            ClassSerializerUtil.computePrimitiveBufferSize(fields),
                            ClassSerializerUtil.computeObjectBufferSize(fields),
                            uid, WriteUtil.hasWriteObject(clazz));
                } catch (Error | RuntimeException e) {
                    throw e;
                } catch (Throwable e) {
                    throw Util.sneak(e);
                }
            }
        }
        return ctxt.next();
    }

    /**
     * {@inheritDoc}
     */
    public int priority() {
        return PRIORITY_SERIALIZABLE_CLASS;
    }
}
