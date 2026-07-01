package io.smallrye.serial.stream;

import java.io.DataOutput;
import java.io.IOException;

import io.smallrye.serial.Serialized;

/**
 * A stream interface for writing serialized data as a sequence of primitive
 * values and {@link Serialized} object graphs.
 * <p>
 * This interface extends {@link DataOutput} with the ability to write
 * {@link Serialized} objects, providing a type-safe alternative to
 * {@link java.io.ObjectOutput} for use with structural serialization
 * (where object graphs are represented as {@link Serialized} instances
 * rather than live Java objects).
 * <p>
 * Primitive values written through the {@link DataOutput} methods are
 * accumulated in block data segments on the wire. Writing a {@link Serialized}
 * object flushes any pending block data before emitting the object.
 *
 * @see SerialInput
 * @see SerialStreamWriter
 */
public interface SerialOutput extends DataOutput {

    /**
     * Write a {@link Serialized} object graph to the stream.
     * <p>
     * Any buffered block data is flushed before the object is written.
     * If the same object has been written before, a back-reference may
     * be emitted instead.
     *
     * @param serialized the serialized object to write (must not be {@code null})
     * @throws IOException if an I/O error occurs
     */
    void writeSerialized(Serialized serialized) throws IOException;

    /**
     * Flush any buffered block data and the underlying stream.
     *
     * @throws IOException if an I/O error occurs
     */
    void flush() throws IOException;
}
