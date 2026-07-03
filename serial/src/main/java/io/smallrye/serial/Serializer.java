package io.smallrye.serial;

import java.io.IOException;
import java.io.InvalidObjectException;

/**
 * A serializer converts some kind of object reference into a serialized representation of it.
 */
public interface Serializer {
    /**
     * Serialize the given object reference into a serialized representation.
     *
     * @param object the object reference to serialize
     * @return the serialized representation (must not be {@code null})
     * @throws IOException if serialization failed due to an I/O error
     */
    Serialized serialize(Object object) throws IOException;

    /**
     * Serialize the given object reference and check that the result is of the expected type.
     *
     * @param object the object reference to serialize
     * @param expectedType the expected type of the serialized representation (must not be {@code null})
     * @param <S> the expected serialized type
     * @return the serialized representation (not {@code null})
     * @throws InvalidObjectException if the serialized representation is not of the expected type
     * @throws IOException if serialization failed due to an I/O error
     */
    default <S extends Serialized> S serialize(Object object, Class<S> expectedType) throws IOException {
        Serialized result = serialize(object);
        if (!expectedType.isInstance(result)) {
            throw new InvalidObjectException(
                    "Expected " + expectedType.getSimpleName() + " but got " + result.getClass().getSimpleName());
        }
        return expectedType.cast(result);
    }

    /**
     * {@return {@code true} if this serializer has previously serialized the given object, or {@code false} if it has not}
     *
     * @param object the object
     */
    boolean hasSerialized(Object object);
}
