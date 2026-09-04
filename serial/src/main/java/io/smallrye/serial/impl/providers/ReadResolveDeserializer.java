package io.smallrye.serial.impl.providers;

import java.io.IOException;

import io.smallrye.serial.Serialized;
import io.smallrye.serial.impl.DeserializerContextImpl;
import io.smallrye.serial.impl.ReadUtil;
import io.smallrye.serial.spi.ObjectDeserializer;

/**
 * Deserializer that handles {@code readResolve} method invocations.
 */
public final class ReadResolveDeserializer implements ObjectDeserializer {

    /**
     * Construct a new instance.
     */
    public ReadResolveDeserializer() {
    }

    public Object deserialize(final Context ctxt, final Serialized serialized) throws IOException, ClassNotFoundException {
        Object deserialized = ctxt.next();
        if (deserialized != null) {
            DeserializerContextImpl ctxtImpl = (DeserializerContextImpl) ctxt;
            if (ReadUtil.hasReadResolve(ctxtImpl, deserialized.getClass())) {
                deserialized = ReadUtil.readResolve(ctxtImpl, deserialized);
            }
        }
        return deserialized;
    }

    public int priority() {
        return PRIORITY_REPLACE;
    }
}
