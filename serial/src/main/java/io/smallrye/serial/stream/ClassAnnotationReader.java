package io.smallrye.serial.stream;

import java.io.IOException;

import io.smallrye.serial.Serialized;

/**
 * A callback for reading class annotation data from a serialization stream.
 * <p>
 * In the Java serialization protocol, class annotations are written by
 * {@link java.io.ObjectOutputStream#annotateClass(Class)} and
 * {@link java.io.ObjectOutputStream#annotateProxyClass(Class)}.
 * They appear immediately after a class descriptor's field definitions
 * and before the {@code TC_ENDBLOCKDATA} marker.
 * <p>
 * The callback receives a {@link SerialInput} so that it can read primitive
 * values (via {@link java.io.DataInput} methods) and {@link Serialized} objects
 * (via {@link SerialInput#readSerialized()}) from the annotation data. Any
 * unconsumed annotation data is automatically skipped after the callback returns.
 * <p>
 * The default behavior is to skip all annotation content (return {@code null}),
 * which matches what the standard {@link java.io.ObjectInputStream} does.
 *
 * @see ClassAnnotationWriter
 */
@FunctionalInterface
public interface ClassAnnotationReader {

    /**
     * Read a class annotation from the stream.
     * <p>
     * The reader should consume annotation data using the provided {@link SerialInput}.
     * It is not required to consume all data; any remaining annotation data up to the
     * {@code TC_ENDBLOCKDATA} marker is automatically skipped by the caller.
     *
     * @param reader the serial input positioned at the start of annotation data (not {@code null})
     * @return the deserialized annotation, or {@code null} if no meaningful annotation exists
     * @throws IOException if an I/O error occurs while reading
     */
    Serialized read(SerialInput reader) throws IOException;
}
