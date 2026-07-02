package io.smallrye.serial.impl.providers;

import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.lang.invoke.MethodHandle;

import io.smallrye.serial.SerialContext;
import io.smallrye.serial.Serialized;
import io.smallrye.serial.SerializedRecord;
import io.smallrye.serial.impl.ClassLocal;
import io.smallrye.serial.impl.RecordFactoryGenerator;
import io.smallrye.serial.impl.RecordGetField;
import io.smallrye.serial.impl.Util;
import io.smallrye.serial.spi.ObjectDeserializer;

/**
 * Deserializer that handles {@link SerializedRecord} instances by reconstructing
 * the record via its canonical constructor using a generated hidden class.
 * <p>
 * A per-record-type hidden class is generated that reads field values from a
 * {@link ObjectInputStream.GetField} and invokes the canonical constructor
 * directly without boxing primitive values.
 */
public final class RecordDeserializer implements ObjectDeserializer {

    private static final ClassLocal<MethodHandle> FACTORIES = new ClassLocal<>(RecordFactoryGenerator::generateFactory);

    /**
     * Construct a new instance.
     */
    public RecordDeserializer() {
    }

    public Object deserialize(final Context ctxt, final Serialized serialized) throws IOException, ClassNotFoundException {
        if (serialized instanceof SerializedRecord rec) {
            Class<?> clazz = ctxt.deserializeClass(rec.recordClass());
            if (!clazz.isRecord()) {
                throw new InvalidClassException(clazz.getName(), "expected a record class");
            }

            MethodHandle factory = ((SerialContext.DeserializerContextImpl) ctxt).classLocal(FACTORIES, clazz);
            RecordGetField getField = new RecordGetField(rec.fieldData(), ctxt);
            try {
                return (Object) factory.invokeExact((ObjectInputStream.GetField) getField);
            } catch (IOException | ClassNotFoundException | RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                throw Util.sneak(e);
            }
        } else {
            return ctxt.next();
        }
    }

    public int priority() {
        return PRIORITY_BASIC;
    }
}
