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
     * The reader should consume all annotation data up to but not including
     * the {@code TC_ENDBLOCKDATA} marker. The caller handles the end marker.
     *
     * @param reader the stream reader positioned at the start of annotation data (not {@code null})
     * @return the deserialized annotation, or {@code null} if no meaningful annotation exists
     * @throws IOException if an I/O error occurs while reading
     */
    Serialized read(SerialStreamReader reader) throws IOException;
}
