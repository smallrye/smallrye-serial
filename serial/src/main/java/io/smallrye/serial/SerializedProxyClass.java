package io.smallrye.serial;

import java.util.List;

import io.smallrye.common.constraint.Assert;

/**
 * The serialized representation of a proxy class descriptor.
 * This corresponds to the {@code TC_PROXYCLASSDESC} grammar element in the
 * Java Object Serialization Stream Protocol.
 * <p>
 * Unlike {@link SerializedClass}, a proxy class descriptor is defined entirely
 * by its list of interface names and class loader rather than by fields and a
 * serial version UID.
 * <p>
 * The {@linkplain #superClass() superclass} is typically the class descriptor for
 * {@code java.lang.reflect.Proxy}, but a custom {@link java.io.ObjectOutputStream}
 * could write a different superclass.
 */
public final class SerializedProxyClass extends Serialized implements HasSuperClass {
    private final List<String> interfaceNames;
    private final Serialized classLoader;
    private final SerializedClass superClass;

    /**
     * Construct a new instance.
     *
     * @param interfaceNames the list of interface names implemented by the proxy class (must not be {@code null})
     * @param classLoader the serialized class loader that can define the proxy class
     *        (must not be {@code null}; may be {@link SerializedNull#INSTANCE})
     * @param superClass the superclass descriptor (typically {@code java.lang.reflect.Proxy}),
     *        or {@code null} if there is none
     */
    public SerializedProxyClass(final List<String> interfaceNames, final Serialized classLoader,
            final SerializedClass superClass) {
        this.interfaceNames = List.copyOf(Assert.checkNotNullParam("interfaceNames", interfaceNames));
        this.classLoader = Assert.checkNotNullParam("classLoader", classLoader);
        this.superClass = superClass;
    }

    /**
     * {@return the list of interface names implemented by the proxy class (not {@code null})}
     */
    public List<String> interfaceNames() {
        return interfaceNames;
    }

    /**
     * {@return the serialized class loader for the proxy class (not {@code null})}
     */
    public Serialized classLoader() {
        return classLoader;
    }

    /**
     * {@return the superclass descriptor (typically {@code java.lang.reflect.Proxy}),
     * or {@code null} if there is none}
     */
    @Override
    public SerializedClass superClass() {
        return superClass;
    }
}
