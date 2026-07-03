package io.smallrye.serial.impl.providers;

import java.io.IOException;

import io.smallrye.serial.Serialized;
import io.smallrye.serial.SerializedArrayClass;
import io.smallrye.serial.SerializedBooleanArray;
import io.smallrye.serial.SerializedByteArray;
import io.smallrye.serial.SerializedCharArray;
import io.smallrye.serial.SerializedDoubleArray;
import io.smallrye.serial.SerializedFloatArray;
import io.smallrye.serial.SerializedIntArray;
import io.smallrye.serial.SerializedLongArray;
import io.smallrye.serial.SerializedShortArray;
import io.smallrye.serial.impl.Util;
import io.smallrye.serial.spi.ObjectSerializer;

/**
 * Serializer that handles array objects (both primitive and object arrays).
 * <p>
 * Primitive arrays are serialized as their corresponding {@code Serialized*Array} types.
 * Object arrays are serialized as {@link io.smallrye.serial.SerializedObjectArray SerializedObjectArray}
 * with each element serialized recursively.
 */
public final class ArraySerializer implements ObjectSerializer {
    /**
     * Construct a new instance.
     */
    public ArraySerializer() {
    }

    /**
     * {@inheritDoc}
     */
    public Serialized serialize(final Context ctxt, final Object object) throws IOException {
        Class<?> clazz = object.getClass();
        if (clazz.isArray()) {
            SerializedArrayClass arrayType = ctxt.serialize(clazz, SerializedArrayClass.class);
            return switch (clazz.descriptorString().charAt(1)) {
                case 'Z' -> new SerializedBooleanArray(arrayType, (boolean[]) object);
                case 'B' -> new SerializedByteArray(arrayType, (byte[]) object);
                case 'C' -> new SerializedCharArray(arrayType, (char[]) object);
                case 'S' -> new SerializedShortArray(arrayType, (short[]) object);
                case 'I' -> new SerializedIntArray(arrayType, (int[]) object);
                case 'J' -> new SerializedLongArray(arrayType, (long[]) object);
                case 'F' -> new SerializedFloatArray(arrayType, (float[]) object);
                case 'D' -> new SerializedDoubleArray(arrayType, (double[]) object);
                default -> Util.newSerializedObjectArray((Object[]) object, ctxt);
            };
        } else {
            return ctxt.next();
        }
    }

    /**
     * {@inheritDoc}
     */
    public int priority() {
        return PRIORITY_ARRAY;
    }
}
