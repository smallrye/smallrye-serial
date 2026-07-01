package io.smallrye.serial.impl.providers;

import java.io.IOException;
import java.lang.reflect.Array;

import io.smallrye.serial.Serialized;
import io.smallrye.serial.SerializedArray;
import io.smallrye.serial.SerializedObjectArray;
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
        if (serialized instanceof SerializedObjectArray a) {
            Class<?> arrayClass = ctxt.deserializeClass(a.arrayType());
            Class<?> componentType = arrayClass.getComponentType();
            int length = a.length();
            Object[] result = (Object[]) Array.newInstance(componentType, length);
            ctxt.preSetObject(result);
            for (int i = 0; i < length; i++) {
                result[i] = ctxt.deserialize(a.get(i));
            }
            return result;
        } else if (serialized instanceof SerializedArray a) {
            return a.asArray();
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
