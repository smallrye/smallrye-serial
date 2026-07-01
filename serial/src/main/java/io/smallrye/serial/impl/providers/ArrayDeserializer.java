package io.smallrye.serial.impl.providers;

import java.io.IOException;
import java.lang.reflect.Array;

import io.smallrye.serial.Serialized;
import io.smallrye.serial.SerializedBooleanArray;
import io.smallrye.serial.SerializedByteArray;
import io.smallrye.serial.SerializedCharArray;
import io.smallrye.serial.SerializedDoubleArray;
import io.smallrye.serial.SerializedFloatArray;
import io.smallrye.serial.SerializedIntArray;
import io.smallrye.serial.SerializedLongArray;
import io.smallrye.serial.SerializedObjectArray;
import io.smallrye.serial.SerializedShortArray;
import io.smallrye.serial.spi.ObjectDeserializer;

/**
 * Deserializer that handles array types (both primitive and object arrays).
 * <p>
 * Primitive arrays are deserialized from their corresponding {@code Serialized*Array} types.
 * Object arrays are deserialized from {@link SerializedObjectArray} with each element deserialized recursively.
 */
public final class ArrayDeserializer implements ObjectDeserializer {
    /**
     * Construct a new instance.
     */
    public ArrayDeserializer() {
    }

    /**
     * {@inheritDoc}
     */
    public Object deserialize(final Context ctxt, final Serialized serialized) throws IOException, ClassNotFoundException {
        if (serialized instanceof SerializedBooleanArray a) {
            return a.asArray();
        } else if (serialized instanceof SerializedByteArray a) {
            return a.asArray();
        } else if (serialized instanceof SerializedCharArray a) {
            return a.asArray();
        } else if (serialized instanceof SerializedShortArray a) {
            return a.asArray();
        } else if (serialized instanceof SerializedIntArray a) {
            return a.asArray();
        } else if (serialized instanceof SerializedLongArray a) {
            return a.asArray();
        } else if (serialized instanceof SerializedFloatArray a) {
            return a.asArray();
        } else if (serialized instanceof SerializedDoubleArray a) {
            return a.asArray();
        } else if (serialized instanceof SerializedObjectArray a) {
            Class<?> arrayClass = ctxt.deserializeClass(a.arrayType());
            Class<?> componentType = arrayClass.getComponentType();
            int length = a.length();
            Object[] result = (Object[]) Array.newInstance(componentType, length);
            ctxt.preSetObject(result);
            for (int i = 0; i < length; i++) {
                result[i] = ctxt.deserialize(a.get(i));
            }
            return result;
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
