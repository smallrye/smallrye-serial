package io.smallrye.serial.impl.providers;

import java.io.Externalizable;
import java.io.IOException;
import java.io.InvalidClassException;

import io.smallrye.serial.Serialized;
import io.smallrye.serial.SerializedExternalizable;
import io.smallrye.serial.SerializedExternalizableClass;
import io.smallrye.serial.impl.CapturedObjectInput;
import io.smallrye.serial.impl.ReadUtil;
import io.smallrye.serial.spi.ObjectDeserializer;

/**
 * Deserializer that handles {@link SerializedExternalizable} instances.
 */
public final class ExternalizableDeserializer implements ObjectDeserializer {
    /**
     * Construct a new instance.
     */
    public ExternalizableDeserializer() {
    }

    public Object deserialize(final Context ctxt, final Serialized serialized) throws IOException, ClassNotFoundException {
        if (serialized instanceof SerializedExternalizable ext) {
            SerializedExternalizableClass serClazz = ext.serializedClass();
            Class<?> clazz = ctxt.deserializeClass(serClazz);
            if (Externalizable.class.isAssignableFrom(clazz)) {
                Externalizable instance = ReadUtil.newExternalizableInstance(clazz.asSubclass(Externalizable.class));
                ctxt.preSetObject(instance);
                instance.readExternal(new CapturedObjectInput(ctxt, ext.data()));
                return instance;
            } else {
                throw new InvalidClassException("Externalized " + clazz + " is not locally Externalizable");
            }
        } else {
            return ctxt.next();
        }
    }

    public int priority() {
        return PRIORITY_EXTERNALIZABLE;
    }
}
