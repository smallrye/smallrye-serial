package io.smallrye.serial.impl.providers;

import java.io.IOException;

import io.smallrye.serial.Serialized;
import io.smallrye.serial.impl.SerializerContextImpl;
import io.smallrye.serial.impl.WriteUtil;
import io.smallrye.serial.spi.ObjectSerializer;

/**
 * Serializer that handles {@code writeReplace} method invocations.
 */
public final class WriteReplaceSerializer implements ObjectSerializer {

    /**
     * Construct a new instance.
     */
    public WriteReplaceSerializer() {
    }

    public Serialized serialize(final Context ctxt, final Object object) throws IOException {
        if (object != null) {
            SerializerContextImpl ctxtImpl = (SerializerContextImpl) ctxt;
            if (WriteUtil.hasWriteReplace(ctxtImpl, object.getClass())) {
                Object replaced = WriteUtil.writeReplace(ctxtImpl, object);
                if (replaced != object) {
                    return ctxt.serialize(replaced);
                }
            }
        }
        return ctxt.next();
    }

    public int priority() {
        return PRIORITY_REPLACE;
    }
}
