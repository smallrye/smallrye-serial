package io.smallrye.serial.impl.providers;

import java.io.Externalizable;
import java.io.IOException;

import io.smallrye.serial.Serialized;
import io.smallrye.serial.SerializedExternalizable;
import io.smallrye.serial.spi.ObjectSerializer;

/**
 * Serializer that handles {@link Externalizable} objects.
 */
public final class ExternalizableSerializer implements ObjectSerializer {
    /**
     * Construct a new instance.
     */
    public ExternalizableSerializer() {
    }

    public Serialized serialize(final Context ctxt, final Object object) throws IOException {
        if (object instanceof Externalizable ext) {
            return new SerializedExternalizable(ctxt, ext);
        } else {
            return ctxt.next();
        }
    }

    public int priority() {
        return PRIORITY_EXTERNALIZABLE;
    }
}
