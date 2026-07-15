package io.smallrye.serial.impl.providers;

import java.io.IOException;

import io.smallrye.serial.Serialized;
import io.smallrye.serial.SerializedString;
import io.smallrye.serial.spi.ObjectDeserializer;

/**
 * Deserializer that handles {@link SerializedString} instances.
 * <p>
 * Produces the {@link String} value from the serialized representation.
 */
public final class StringDeserializer implements ObjectDeserializer {
    /**
     * Construct a new instance.
     */
    public StringDeserializer() {
    }

    /**
     * {@inheritDoc}
     */
    public Object deserialize(final Context ctxt, final Serialized serialized) throws IOException, ClassNotFoundException {
        if (serialized instanceof SerializedString ss) {
            return ss.string();
        } else {
            return ctxt.next();
        }
    }

    /**
     * {@inheritDoc}
     */
    public int priority() {
        return PRIORITY_STRING;
    }
}
