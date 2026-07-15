package io.smallrye.serial.impl.providers;

import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;
import java.lang.constant.ClassDesc;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Comparator;

import io.smallrye.serial.SerialField;
import io.smallrye.serial.Serialized;
import io.smallrye.serial.SerializedKnownClassLoader;
import io.smallrye.serial.impl.Util;
import io.smallrye.serial.spi.ObjectSerializer;

/**
 * Shared utilities for class serializer implementations.
 */
final class ClassSerializerUtil {
    private ClassSerializerUtil() {
    }

    /**
     * Serialize the class loader for the given class.
     *
     * @param clazz the class whose class loader to serialize
     * @param ctxt the serializer context
     * @return the serialized class loader
     * @throws java.io.IOException if serialization fails
     */
    static Serialized serializeClassLoader(Class<?> clazz, ObjectSerializer.Context ctxt) throws java.io.IOException {
        ClassLoader cl = clazz.getClassLoader();
        return cl == null ? SerializedKnownClassLoader.forBootClassLoader() : ctxt.serialize(cl);
    }

    /**
     * Compute the stream fields for the given class descriptor, sorted by name.
     *
     * @param osc the object stream class descriptor
     * @return a sorted array of {@link SerialField} instances
     */
    static SerialField[] computeFields(ObjectStreamClass osc) {
        ObjectStreamField[] jdkFields = osc.getFields();
        Arrays.sort(jdkFields, Comparator.comparing(ObjectStreamField::getName));
        SerialField[] result = new SerialField[jdkFields.length];
        for (int i = 0; i < jdkFields.length; i++) {
            result[i] = new SerialField(jdkFields[i].getName(),
                    ClassDesc.ofDescriptor(jdkFields[i].getType().descriptorString()),
                    jdkFields[i].getOffset());
        }
        return result;
    }

    /**
     * Compute the stream fields for a record class, assigning sequential offsets and sorting by name.
     *
     * @param clazz the record class
     * @return a sorted array of {@link SerialField} instances with valid offsets
     */
    static SerialField[] computeRecordFields(Class<?> clazz) {
        RecordComponent[] components = clazz.getRecordComponents();
        Arrays.sort(components, Comparator.comparing(RecordComponent::getName));
        SerialField[] result = new SerialField[components.length];
        int primOffset = 0, objOffset = 0;
        for (int i = 0; i < components.length; i++) {
            final RecordComponent component = components[i];
            ClassDesc type = Util.classDesc(component.getType());
            int offset;
            if (type.isPrimitive()) {
                offset = primOffset;
                primOffset += Util.primitiveSizeOf(type.descriptorString().charAt(0));
            } else {
                offset = objOffset++;
            }
            result[i] = new SerialField(component.getName(), type, offset);
        }
        return result;
    }

    /**
     * Compute the primitive buffer size from an array of fields with valid offsets.
     *
     * @param fields the fields to compute sizes from
     * @return the primitive buffer size
     */
    static int computePrimitiveBufferSize(SerialField[] fields) {
        int size = 0;
        for (SerialField field : fields) {
            if (field.isPrimitive()) {
                int end = field.primitiveSize() + field.offset();
                if (end > size) {
                    size = end;
                }
            }
        }
        return size;
    }

    /**
     * Compute the object buffer size from an array of fields with valid offsets.
     *
     * @param fields the fields to compute sizes from
     * @return the object buffer size
     */
    static int computeObjectBufferSize(SerialField[] fields) {
        int size = 0;
        for (SerialField field : fields) {
            if (!field.isPrimitive()) {
                int end = field.offset() + 1;
                if (end > size) {
                    size = end;
                }
            }
        }
        return size;
    }
}
