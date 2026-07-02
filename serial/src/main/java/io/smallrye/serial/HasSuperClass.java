package io.smallrye.serial;

/**
 * A sealed interface for serialized class descriptors that carry a superclass
 * reference in the serialization stream.
 * <p>
 * In the Java serialization wire format, the {@code superClassDesc} grammar element
 * appears as part of normal class descriptors ({@code TC_CLASSDESC}) and proxy class
 * descriptors ({@code TC_PROXYCLASSDESC}). This interface provides uniform access to
 * that superclass information across the three descriptor types that carry it:
 * <ul>
 * <li>{@link SerializedSerializableClass} — the superclass is the nearest serializable
 * class in the inheritance chain</li>
 * <li>{@link SerializedExternalizableClass} — the superclass is the serializable
 * parent chain (externalizable classes may extend serializable classes)</li>
 * <li>{@link SerializedProxyClass} — the superclass is typically
 * {@code java.lang.reflect.Proxy}</li>
 * </ul>
 *
 * @see SerializedSerializableClass#superClass()
 * @see SerializedExternalizableClass#superClass()
 * @see SerializedProxyClass#superClass()
 */
public sealed interface HasSuperClass
        permits SerializedSerializableClass, SerializedExternalizableClass, SerializedProxyClass {

    /**
     * {@return the superclass descriptor in the serialization hierarchy, or {@code null}
     * if there is no superclass (corresponding to {@code TC_NULL} in the wire format)}
     */
    SerializedClass superClass();
}
