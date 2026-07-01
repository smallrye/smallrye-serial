package io.smallrye.serial;

import java.io.Externalizable;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import io.smallrye.common.constraint.Assert;
import io.smallrye.serial.impl.CapturingObjectOutput;
import io.smallrye.serial.spi.ObjectSerializer;

/**
 * The serialized representation of an {@link Externalizable} object.
 * The object's data is captured as a sequence of {@link StreamData} blocks
 * produced by the object's {@link Externalizable#writeExternal(java.io.ObjectOutput)} method.
 */
public final class SerializedExternalizable extends Serialized {

    /**
     * Functional interface for lazily reading stream data during construction.
     * Used by the wire-reader constructor to defer data reading until after this
     * instance has been registered in the handle table for circular reference support.
     */
    @FunctionalInterface
    public interface StreamDataReader {
        /**
         * Read the stream data for this externalizable object.
         *
         * @return the list of stream data entries (not {@code null})
         * @throws IOException if an I/O error occurs while reading
         */
        List<StreamData> read() throws IOException;
    }

    private final SerializedExternalizableClass serializedClass;
    private final List<StreamData> data;

    /**
     * Construct a new instance from pre-existing data.
     * This constructor is intended for wire protocol readers and manual graph construction.
     *
     * @param serializedClass the class descriptor for the externalizable object (must not be {@code null})
     * @param data the stream data produced by the object's {@code writeExternal} method (must not be {@code null})
     */
    public SerializedExternalizable(final SerializedExternalizableClass serializedClass, final List<StreamData> data) {
        this.serializedClass = serializedClass;
        this.data = List.copyOf(data);
    }

    /**
     * Construct a new instance from a wire stream, supporting circular references.
     * The {@code preSet} callback is invoked with {@code this} before reading data,
     * allowing the caller to register this instance in a handle table so that
     * back-references encountered during data reading resolve correctly.
     *
     * @param serializedClass the class descriptor (must not be {@code null})
     * @param preSet callback to register this instance before data is read (must not be {@code null})
     * @param dataReader supplier of the stream data (must not be {@code null})
     * @throws IOException if an I/O error occurs while reading data
     */
    public SerializedExternalizable(final SerializedExternalizableClass serializedClass,
            final Consumer<Serialized> preSet,
            final StreamDataReader dataReader) throws IOException {
        this.serializedClass = Assert.checkNotNullParam("serializedClass", serializedClass);
        Assert.checkNotNullParam("preSet", preSet).accept(this);
        this.data = List.copyOf(Assert.checkNotNullParam("dataReader", dataReader).read());
    }

    /**
     * Construct a new instance during serialization by capturing data from a live externalizable object.
     *
     * @param ctxt the serializer context (must not be {@code null})
     * @param ext the externalizable object (must not be {@code null})
     * @throws IOException if the object's {@code writeExternal} method throws an I/O error
     */
    public SerializedExternalizable(final ObjectSerializer.Context ctxt, final Externalizable ext) throws IOException {
        serializedClass = (SerializedExternalizableClass) ctxt.serialize(ext.getClass());
        ctxt.preSetSerialized(this);
        try (CapturingObjectOutput oo = new CapturingObjectOutput(ctxt)) {
            ext.writeExternal(oo);
            oo.close();
            data = oo.streamData();
        }
    }

    /**
     * {@return the class descriptor for the externalizable object (not {@code null})}
     */
    public SerializedExternalizableClass serializedClass() {
        return serializedClass;
    }

    /**
     * {@return the stream data for this object (not {@code null})}
     */
    public List<StreamData> data() {
        return data;
    }
}
