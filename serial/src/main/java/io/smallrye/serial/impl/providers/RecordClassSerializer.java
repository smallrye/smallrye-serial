package io.smallrye.serial.impl.providers;

import java.io.IOException;
import java.io.ObjectStreamClass;
import java.util.List;

import io.smallrye.serial.SerialField;
import io.smallrye.serial.Serialized;
import io.smallrye.serial.SerializedRecordClass;
import io.smallrye.serial.impl.Util;
import io.smallrye.serial.spi.ObjectSerializer;

/**
 * Serializer that handles record {@link Class} objects.
 */
public final class RecordClassSerializer implements ObjectSerializer {
    /** Construct a new instance. */
    public RecordClassSerializer() {
    }

    public Serialized serialize(final Context ctxt, final Object object) throws IOException {
        if (object instanceof Class<?> clazz && clazz.isRecord()) {
            Serialized classLoader = ClassSerializerUtil.serializeClassLoader(clazz, ctxt);
            ObjectStreamClass osc = ObjectStreamClass.lookupAny(clazz);
            long uid = osc.getSerialVersionUID();
            SerialField[] fields = ClassSerializerUtil.computeRecordFields(clazz);
            return new SerializedRecordClass(Util.classDesc(clazz), classLoader, uid, List.of(fields),
                    ClassSerializerUtil.computePrimitiveBufferSize(fields),
                    ClassSerializerUtil.computeObjectBufferSize(fields));
        }
        return ctxt.next();
    }

    public int priority() {
        return PRIORITY_RECORD_CLASS;
    }
}
