package io.smallrye.serial.impl.providers;

import java.io.IOException;
import java.io.InvalidClassException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.RecordComponent;

import io.smallrye.serial.SerialData;
import io.smallrye.serial.SerialField;
import io.smallrye.serial.Serialized;
import io.smallrye.serial.SerializedRecord;
import io.smallrye.serial.SerializedRecordClass;
import io.smallrye.serial.impl.Util;
import io.smallrye.serial.spi.ObjectDeserializer;

/**
 * Deserializer that handles {@link SerializedRecord} instances by reconstructing
 * the record via its canonical constructor.
 */
public final class RecordDeserializer implements ObjectDeserializer {

    /**
     * Construct a new instance.
     */
    public RecordDeserializer() {
    }

    public Object deserialize(final Context ctxt, final Serialized serialized) throws IOException, ClassNotFoundException {
        if (serialized instanceof SerializedRecord rec) {
            SerializedRecordClass recClass = rec.recordClass();
            Class<?> clazz = ctxt.deserializeClass(recClass);
            if (!clazz.isRecord()) {
                throw new InvalidClassException(clazz.getName(), "expected a record class");
            }

            RecordComponent[] components = clazz.getRecordComponents();
            SerialData fieldData = rec.fieldData();

            // build the argument array for the canonical constructor
            Object[] args = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                RecordComponent comp = components[i];
                SerialField sf = recClass.streamField(comp.getName());
                if (sf == null) {
                    // component not present in the stream — use type default
                    args[i] = defaultValue(comp.getType());
                } else if (sf.isPrimitive()) {
                    args[i] = readPrimitive(fieldData, sf);
                } else {
                    args[i] = ctxt.deserialize(fieldData.objectFieldData().getObject(sf.offset()));
                }
            }

            // invoke the canonical constructor
            MethodHandle ctor = canonicalConstructor(clazz, components);
            try {
                Object record = ctor.invokeWithArguments(args);
                ctxt.preSetObject(record);
                return record;
            } catch (IOException | ClassNotFoundException | RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                throw Util.sneak(e);
            }
        } else {
            return ctxt.next();
        }
    }

    /**
     * Read a primitive field value from the field data, boxed for use as a constructor argument.
     */
    private static Object readPrimitive(SerialData fieldData, SerialField sf) {
        int offset = sf.offset();
        return switch (sf.typeCode()) {
            case 'Z' -> fieldData.primitiveFieldData().getBoolean(offset);
            case 'B' -> fieldData.primitiveFieldData().getByte(offset);
            case 'C' -> fieldData.primitiveFieldData().getChar(offset);
            case 'S' -> fieldData.primitiveFieldData().getShort(offset);
            case 'I' -> fieldData.primitiveFieldData().getInt(offset);
            case 'J' -> fieldData.primitiveFieldData().getLong(offset);
            case 'F' -> fieldData.primitiveFieldData().getFloat(offset);
            case 'D' -> fieldData.primitiveFieldData().getDouble(offset);
            default -> throw new IllegalStateException("Unknown primitive type code: " + sf.typeCode());
        };
    }

    /**
     * Return the default value for the given type (0, false, or null).
     */
    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return (char) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0f;
        }
        if (type == double.class) {
            return 0.0d;
        }
        return null;
    }

    private static final ClassValue<MethodHandle> canonicalCtors = new ClassValue<>() {
        protected MethodHandle computeValue(final Class<?> type) {
            RecordComponent[] components = type.getRecordComponents();
            Class<?>[] paramTypes = new Class<?>[components.length];
            for (int i = 0; i < components.length; i++) {
                paramTypes[i] = components[i].getType();
            }
            try {
                return MethodHandles.privateLookupIn(type, MethodHandles.lookup())
                        .findConstructor(type, MethodType.methodType(void.class, paramTypes))
                        .asType(MethodType.genericMethodType(paramTypes.length));
            } catch (ReflectiveOperationException e) {
                throw Util.asError(e);
            }
        }
    };

    /**
     * Get a cached method handle for the canonical constructor of the given record class.
     */
    private static MethodHandle canonicalConstructor(Class<?> clazz, RecordComponent[] components) {
        return canonicalCtors.get(clazz);
    }

    public int priority() {
        return PRIORITY_BASIC;
    }
}
