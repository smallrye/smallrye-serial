package io.smallrye.serial.spi;

/**
 * A prioritized serializer/deserializer.
 * <p>
 * Serializers and deserializers are executed in order from the highest priority to the lowest priority.
 * If two have the same priority, an unspecified (but consistent) ordering is performed involving the class name
 * and other information.
 */
public sealed interface Prioritized permits ObjectDeserializer, ObjectSerializer {
    /**
     * The priority of {@code readResolve}/{@code writeReplace} operations.
     */
    int PRIORITY_REPLACE = 20_000;

    /**
     * The priority of class loader serialization.
     */
    int PRIORITY_CLASS_LOADER = 19_000;

    /**
     * The priority of {@link Class} deserialization.
     */
    int PRIORITY_CLASS = 18_000;

    /**
     * The priority of primitive {@link Class} serialization.
     */
    int PRIORITY_PRIMITIVE_CLASS = 18_000;

    /**
     * The priority of special {@link Class} serialization ({@link String}, {@link Enum}).
     */
    int PRIORITY_SPECIAL_CLASS = 17_000;

    /**
     * The priority of array {@link Class} serialization.
     */
    int PRIORITY_ARRAY_CLASS = 16_000;

    /**
     * The priority of enum {@link Class} serialization.
     */
    int PRIORITY_ENUM_CLASS = 15_000;

    /**
     * The priority of {@link java.io.Externalizable Externalizable} {@link Class} serialization.
     */
    int PRIORITY_EXTERNALIZABLE_CLASS = 14_000;

    /**
     * The priority of record {@link Class} serialization.
     */
    int PRIORITY_RECORD_CLASS = 13_000;

    /**
     * The priority of {@link java.io.Serializable Serializable} {@link Class} serialization.
     */
    int PRIORITY_SERIALIZABLE_CLASS = 12_000;

    /**
     * The priority of non-serializable {@link Class} serialization (fallback).
     */
    int PRIORITY_NON_SERIALIZABLE_CLASS = 11_000;

    /**
     * The priority of basic type serialization, such as {@link String} and {@link Enum}.
     */
    int PRIORITY_BASIC = 10_000;

    /**
     * The priority of array serialization.
     */
    int PRIORITY_ARRAY = 9_000;

    /**
     * The priority of standard {@link java.io.Externalizable Externalizable} serialization.
     */
    int PRIORITY_EXTERNALIZABLE = 8_000;

    /**
     * The priority of standard {@link java.io.Serializable Serializable} serialization.
     */
    int PRIORITY_SERIALIZABLE = 7_000;

    /**
     * The default priority of user-provided serializers and deserializers.
     */
    int PRIORITY_USER = 0;

    /**
     * {@return the priority}
     * By default, {@link #PRIORITY_USER} is returned.
     */
    default int priority() {
        return PRIORITY_USER;
    }
}
