package io.smallrye.serial.impl.providers;

import java.io.IOException;
import java.io.ObjectStreamClass;

import io.smallrye.serial.Serialized;
import io.smallrye.serial.SerializedArrayClass;
import io.smallrye.serial.SerializedClass;
import io.smallrye.serial.impl.Primitive;
import io.smallrye.serial.impl.Util;
import io.smallrye.serial.spi.ObjectSerializer;

/**
 * Serializer that handles array {@link Class} objects.
 */
public final class ArrayClassSerializer implements ObjectSerializer {
    /** Construct a new instance. */
    public ArrayClassSerializer() {
    }

    public Serialized serialize(final Context ctxt, final Object object) throws IOException {
        if (object instanceof Class<?> clazz && clazz.isArray()) {
            Class<?> compType = clazz.getComponentType();
            Primitive primitive = Primitive.forClass(compType);
            if (primitive != null) {
                return switch (primitive) {
                    case BOOLEAN -> SerializedArrayClass.BOOLEAN_ARRAY;
                    case BYTE -> SerializedArrayClass.BYTE_ARRAY;
                    case CHAR -> SerializedArrayClass.CHAR_ARRAY;
                    case SHORT -> SerializedArrayClass.SHORT_ARRAY;
                    case INT -> SerializedArrayClass.INT_ARRAY;
                    case LONG -> SerializedArrayClass.LONG_ARRAY;
                    case FLOAT -> SerializedArrayClass.FLOAT_ARRAY;
                    case DOUBLE -> SerializedArrayClass.DOUBLE_ARRAY;
                    case VOID -> throw new IllegalArgumentException("void[] is not serializable");
                };
            }
            Serialized classLoader = ClassSerializerUtil.serializeClassLoader(clazz, ctxt);
            SerializedClass componentType = ctxt.serialize(compType, SerializedClass.class);
            return new SerializedArrayClass(Util.classDesc(clazz), classLoader,
                    ObjectStreamClass.lookup(clazz).getSerialVersionUID(), componentType);
        }
        return ctxt.next();
    }

    public int priority() {
        return PRIORITY_ARRAY_CLASS;
    }
}
