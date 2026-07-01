package io.smallrye.serial;

import java.io.IOException;
import java.util.function.Consumer;

import io.smallrye.common.constraint.Assert;

/**
 * The serialized form of a Java {@code enum} constant.
 */
public final class SerializedEnum extends Serialized {

    /**
     * Functional interface for lazily reading the enum constant name during construction.
     * Used by the wire-reader constructor to defer name reading until after this
     * instance has been registered in the handle table for circular reference support.
     */
    @FunctionalInterface
    public interface NameReader {
        /**
         * Read the serialized enum constant name.
         *
         * @return the serialized constant name (not {@code null})
         * @throws IOException if an I/O error occurs while reading
         */
        Serialized read() throws IOException;
    }

    private final SerializedEnumClass enumClass;
    private final Serialized constantName;

    /**
     * Construct a new instance.
     *
     * @param enumClass the class descriptor of the enum type (must not be {@code null})
     * @param constantName the serialized name of the enum constant (must not be {@code null})
     */
    public SerializedEnum(final SerializedEnumClass enumClass, final Serialized constantName) {
        this.enumClass = Assert.checkNotNullParam("enumClass", enumClass);
        this.constantName = Assert.checkNotNullParam("constantName", constantName);
    }

    /**
     * Construct a new instance from a wire stream, supporting circular references.
     * The {@code preSet} callback is invoked with {@code this} before reading the
     * constant name, allowing the caller to register this instance in a handle table
     * so that back-references encountered during name reading resolve correctly.
     *
     * @param enumClass the class descriptor (must not be {@code null})
     * @param preSet callback to register this instance before the name is read (must not be {@code null})
     * @param nameReader supplier of the serialized constant name (must not be {@code null})
     * @throws IOException if an I/O error occurs while reading
     */
    public SerializedEnum(final SerializedEnumClass enumClass,
            final Consumer<Serialized> preSet,
            final NameReader nameReader) throws IOException {
        this.enumClass = Assert.checkNotNullParam("enumClass", enumClass);
        Assert.checkNotNullParam("preSet", preSet).accept(this);
        this.constantName = Assert.checkNotNullParam("nameReader", nameReader).read();
    }

    /**
     * {@return the class descriptor of the enum type (not {@code null})}
     */
    public SerializedEnumClass enumClass() {
        return enumClass;
    }

    /**
     * {@return the serialized name of the enum constant (not {@code null})}
     */
    public Serialized constantName() {
        return constantName;
    }
}
