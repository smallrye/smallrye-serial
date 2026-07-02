package io.smallrye.serial.stream;

import java.io.DataInput;
import java.io.IOException;

import io.smallrye.serial.Serialized;

/**
 * A stream interface for reading serialized data as a sequence of primitive
 * values and {@link Serialized} object graphs.
 * <p>
 * This interface extends {@link DataInput} with the ability to read
 * {@link Serialized} objects and to perform stream-like operations
 * ({@link #read()}, {@link #skip(long)}, {@link #available()}),
 * providing a type-safe alternative to {@link java.io.ObjectInput}
 * for use with structural deserialization (where object graphs are
 * represented as {@link Serialized} instances rather than live Java objects).
 * <p>
 * Primitive values are read from block data segments on the wire.
 * Reading a {@link Serialized} object skips any remaining block data
 * in the current segment before reading the object.
 *
 * @see SerialOutput
 * @see SerialStreamReader
 */
public interface SerialInput extends DataInput {

    /**
     * Read the next {@link Serialized} object from the stream.
     * <p>
     * Any remaining block data in the current segment is skipped
     * before reading the object. Returns {@code null} if the end
     * of block data or stream has been reached.
     *
     * @return the deserialized object, or {@code null} at end of block data or stream
     * @throws IOException if an I/O error or protocol error occurs
     */
    Serialized readSerialized() throws IOException;

    /**
     * Read a single byte from the stream.
     * <p>
     * Unlike {@link DataInput#readByte()}, this method returns {@code -1}
     * at the end of block data instead of throwing an exception.
     *
     * @return the byte read (0–255), or {@code -1} at end of block data
     * @throws IOException if an I/O error occurs
     */
    int read() throws IOException;

    /**
     * Read bytes into the given array.
     * <p>
     * Reads up to {@code b.length} bytes from the current block data.
     * Returns the number of bytes actually read, or {@code -1} at end
     * of block data.
     *
     * @param b the buffer to read into (not {@code null})
     * @return the number of bytes read, or {@code -1} at end of block data
     * @throws IOException if an I/O error occurs
     */
    int read(byte[] b) throws IOException;

    /**
     * Read bytes into a portion of the given array.
     * <p>
     * Reads up to {@code len} bytes from the current block data into
     * {@code b} starting at offset {@code off}. Returns the number of
     * bytes actually read, or {@code -1} at end of block data.
     *
     * @param b the buffer to read into (not {@code null})
     * @param off the start offset in the buffer
     * @param len the maximum number of bytes to read
     * @return the number of bytes read, or {@code -1} at end of block data
     * @throws IOException if an I/O error occurs
     */
    int read(byte[] b, int off, int len) throws IOException;

    /**
     * Skip bytes in the block data.
     *
     * @param n the number of bytes to skip
     * @return the number of bytes actually skipped
     * @throws IOException if an I/O error occurs
     */
    long skip(long n) throws IOException;

    /**
     * Return the number of bytes available in the current block data segment
     * without blocking.
     * <p>
     * This is a conservative estimate: it reports only the bytes remaining
     * in the current segment and does not look ahead for additional segments.
     *
     * @return the number of bytes available
     * @throws IOException if an I/O error occurs
     */
    int available() throws IOException;
}
