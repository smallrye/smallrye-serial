package io.smallrye.serial.stream;

import java.io.IOException;

import io.smallrye.serial.Serialized;
import io.smallrye.serial.SerializedKnownClassLoader;

/**
 * A callback for reading class annotation data from a serialization stream
 * and producing a class loader representation for the class descriptor being constructed.
 * <p>
 * In the Java serialization protocol, class annotations are written by
 * {@link java.io.ObjectOutputStream#annotateClass(Class)} and
 * {@link java.io.ObjectOutputStream#annotateProxyClass(Class)}.
 * They appear immediately after a class descriptor's field definitions
 * and before the {@code TC_ENDBLOCKDATA} marker. The typical use case for
 * annotation data is to convey class loading context (e.g. a codebase URL).
 * <p>
 * The callback receives a {@link SerialInput} so that it can read primitive
 * values (via {@link java.io.DataInput} methods) and {@link Serialized} objects
 * (via {@link SerialInput#readSerialized()}) from the annotation data. Any
 * unconsumed annotation data is automatically skipped after the callback returns.
 * <p>
 * The return value is used as the class loader for the class descriptor being
 * constructed. A {@code null} return is treated as
 * {@link SerializedKnownClassLoader#forUnspecifiedClassLoader()}.
 *
 * @see ClassAnnotationWriter
 */
@FunctionalInterface
public interface ClassAnnotationReader {

    /**
     * Read a class annotation from the stream and return a class loader representation
     * for the class descriptor being constructed.
     * <p>
     * The reader should consume annotation data using the provided {@link SerialInput}.
     * It is not required to consume all data; any remaining annotation data up to the
     * {@code TC_ENDBLOCKDATA} marker is automatically skipped by the caller.
     *
     * @param reader the serial input positioned at the start of annotation data (not {@code null})
     * @return the class loader to use for the class descriptor, or {@code null} to use
     *         {@link SerializedKnownClassLoader#forUnspecifiedClassLoader()}
     * @throws IOException if an I/O error occurs while reading
     */
    Serialized read(SerialInput reader) throws IOException;
}
