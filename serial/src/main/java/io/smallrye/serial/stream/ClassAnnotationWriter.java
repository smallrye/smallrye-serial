package io.smallrye.serial.stream;

import java.io.IOException;

import io.smallrye.serial.SerializedClass;

/**
 * A callback for writing class annotation data to a serialization stream.
 * <p>
 * In the Java serialization protocol, class annotations are written by
 * {@link java.io.ObjectOutputStream#annotateClass(Class)} and
 * {@link java.io.ObjectOutputStream#annotateProxyClass(Class)}.
 * They appear immediately after a class descriptor's field definitions
 * and before the {@code TC_ENDBLOCKDATA} marker.
 * <p>
 * The callback receives a {@link SerialOutput} so that it can write primitive
 * values (via {@link java.io.DataOutput} methods) and {@link io.smallrye.serial.Serialized}
 * objects (via {@link SerialOutput#writeSerialized}) to the annotation data.
 * <p>
 * The default behavior is to write nothing (the caller writes the
 * {@code TC_ENDBLOCKDATA} marker), which matches what the standard
 * {@link java.io.ObjectOutputStream} does.
 *
 * @see ClassAnnotationReader
 */
@FunctionalInterface
public interface ClassAnnotationWriter {

    /**
     * Write class annotation data to the stream.
     * <p>
     * The writer should write any annotation data but must not write
     * the {@code TC_ENDBLOCKDATA} marker; the caller handles that.
     *
     * @param classDesc the class descriptor being annotated (not {@code null})
     * @param writer the serial output to write annotation data to (not {@code null})
     * @throws IOException if an I/O error occurs while writing
     */
    void write(SerializedClass classDesc, SerialOutput writer) throws IOException;
}
