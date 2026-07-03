package io.smallrye.serial;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import io.smallrye.common.constraint.Assert;
import io.smallrye.serial.impl.CapturingObjectOutputStream;
import io.smallrye.serial.impl.WriteUtil;
import io.smallrye.serial.spi.ObjectSerializer;

/**
 * The serialized representation of a {@link java.io.Serializable} object instance.
 * This captures the per-class-level field data and any additional stream data
 * produced by custom {@code writeObject} methods.
 */
public final class SerializedSerializable extends Serialized {

    /**
     * Functional interface for lazily reading serialization data during construction.
     * Used by the wire-reader constructor to defer data reading until after this
     * instance has been registered in the handle table for circular reference support.
     */
    @FunctionalInterface
    public interface DataReader {
        /**
         * Read the per-class-level serialization data.
         *
         * @return the list of serial data entries, ordered from root to leaf (not {@code null})
         * @throws IOException if an I/O error occurs while reading
         */
        List<SerialData> read() throws IOException;
    }

    private final SerializedSerializableClass streamClass;
    private final List<SerialData> data;

    /**
     * Construct a new instance from pre-existing serialization data.
     * This constructor is intended for wire protocol readers and manual graph construction.
     *
     * @param streamClass the class descriptor for the serialized object (must not be {@code null})
     * @param data the per-class-level serialization data, ordered from root to leaf (must not be {@code null})
     */
    public SerializedSerializable(final SerializedSerializableClass streamClass, final List<SerialData> data) {
        this.streamClass = Assert.checkNotNullParam("streamClass", streamClass);
        this.data = List.copyOf(data);
    }

    /**
     * Construct a new instance from a wire stream, supporting circular references.
     * The {@code preSet} callback is invoked with {@code this} before reading data,
     * allowing the caller to register this instance in a handle table so that
     * back-references encountered during data reading resolve correctly.
     *
     * @param streamClass the class descriptor (must not be {@code null})
     * @param preSet callback to register this instance before data is read (must not be {@code null})
     * @param dataReader supplier of the per-class-level serialization data (must not be {@code null})
     * @throws IOException if an I/O error occurs while reading data
     */
    public SerializedSerializable(final SerializedSerializableClass streamClass,
            final Consumer<Serialized> preSet,
            final DataReader dataReader) throws IOException {
        this.streamClass = Assert.checkNotNullParam("streamClass", streamClass);
        Assert.checkNotNullParam("preSet", preSet).accept(this);
        this.data = List.copyOf(Assert.checkNotNullParam("dataReader", dataReader).read());
    }

    /**
     * Construct a new instance during serialization by capturing data from a live object.
     *
     * @param object the object being serialized (must not be {@code null})
     * @param ctxt the context (must not be {@code null})
     */
    @SuppressWarnings("unused") // accessed by method handle
    SerializedSerializable(final Object object, final ObjectSerializer.Context ctxt) throws IOException {
        this.streamClass = ctxt.serialize(object.getClass(), SerializedSerializableClass.class);
        ctxt.preSetSerialized(this);
        this.data = List.copyOf(buildData(ctxt, object, object.getClass(), streamClass, new ArrayList<>()));
    }

    /**
     * {@return the class descriptor for this serialized object (not {@code null})}
     */
    public SerializedSerializableClass serializedClass() {
        return streamClass;
    }

    /**
     * {@return the per-class-level serialization data, ordered from root to leaf (not {@code null})}
     */
    public List<SerialData> data() {
        return data;
    }

    /**
     * Find the serialization data for a specific class level by class name.
     *
     * @param className the fully qualified class name (must not be {@code null})
     * @return the serialization data for that class level, or {@code null} if not found
     */
    public SerialData dataFor(String className) {
        Assert.checkNotNullParam("className", className);
        for (SerialData d : data) {
            if (d.serializedClass().name().equals(className)) {
                return d;
            }
        }
        return null;
    }

    /**
     * Find the serialization data for a specific class level by local class.
     * The lookup is performed by matching the class name.
     *
     * @param clazz the local class (must not be {@code null})
     * @return the serialization data for that class level, or {@code null} if not found
     */
    public SerialData dataFor(Class<?> clazz) {
        return dataFor(Assert.checkNotNullParam("clazz", clazz).getName());
    }

    /**
     * Find the serialization data for a specific class level by serialized class descriptor.
     * The lookup is performed by identity comparison against the {@link SerialData#serializedClass()} reference.
     *
     * @param serializedClass the serialized class descriptor (must not be {@code null})
     * @return the serialization data for that class level, or {@code null} if not found
     */
    public SerialData dataFor(SerializedClass serializedClass) {
        Assert.checkNotNullParam("serializedClass", serializedClass);
        for (SerialData d : data) {
            if (d.serializedClass() == serializedClass) {
                return d;
            }
        }
        return null;
    }

    /**
     * Walk the class hierarchy from root to leaf, producing a {@link SerialData} entry for each serializable
     * class level. The local class hierarchy ({@code clazz}) and remote stream class hierarchy
     * ({@code streamClass}) are walked in parallel to handle mismatches.
     */
    private List<SerialData> buildData(ObjectSerializer.Context context, Object object, Class<?> clazz,
            SerializedSerializableClass streamClass, List<SerialData> data) throws IOException {
        if (streamClass == null) {
            // no remote class available, skip the local class
            if (clazz != null) {
                buildData(context, object, clazz.getSuperclass(), null, data);
            }
            return data;
        } else {
            if (clazz == null) {
                buildData(context, object, null, streamClass.superClass(), data);
                // no local class available, write defaults
                int pbs = streamClass.primitiveBufferSize();
                int obs = streamClass.objectBufferSize();
                data.add(new SerialData(
                        streamClass,
                        pbs == 0 ? StreamData.OfBytes.EMPTY : StreamData.of(new byte[pbs]),
                        obs == 0 ? StreamData.OfObjects.EMPTY
                                : StreamData.of(Collections.nCopies(obs, SerializedNull.INSTANCE)),
                        List.of()));
                return data;
            } else {
                buildData(context, object, clazz.getSuperclass(), streamClass.superClass(), data);
                try (CapturingObjectOutputStream oos = new CapturingObjectOutputStream(context, clazz, object,
                        streamClass)) {
                    if (WriteUtil.hasWriteObject(clazz)) {
                        WriteUtil.writeObject(clazz, object, oos);
                    } else {
                        WriteUtil.defaultWriteObject(clazz, object, oos);
                    }
                    oos.close();
                    data.add(new SerialData(streamClass, oos.primitiveFieldData(), oos.objectFieldData(), oos.streamData()));
                    return data;
                }
            }
        }
    }
}
