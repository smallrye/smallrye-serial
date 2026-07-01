package io.smallrye.serial.impl;

import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteOrder;

import io.smallrye.serial.SerializedObjectArray;
import io.smallrye.serial.SerializedProxyClass;
import io.smallrye.serial.SerializedProxyObject;
import io.smallrye.serial.SerializedRecord;
import io.smallrye.serial.SerializedSerializable;
import io.smallrye.serial.spi.ObjectSerializer;

public final class Util {
    private Util() {
    }

    /**
     * A thread-safe cache mapping {@link Class} instances to their corresponding {@link ClassDesc}.
     * Uses {@link ClassValue} for efficient, GC-friendly per-class caching.
     */
    private static final ClassValue<ClassDesc> CLASS_DESC_CACHE = new ClassValue<>() {
        @Override
        protected ClassDesc computeValue(Class<?> type) {
            return type.describeConstable().orElseThrow();
        }
    };

    /**
     * Get the {@link ClassDesc} for the given class, using a cached value if available.
     *
     * @param clazz the class (must not be {@code null})
     * @return the class descriptor (not {@code null})
     */
    public static ClassDesc classDesc(Class<?> clazz) {
        return CLASS_DESC_CACHE.get(clazz);
    }

    /**
     * Convert a {@link Class#getName()}-format class name to a {@link ClassDesc}.
     * Regular class names like {@code "java.lang.String"} pass through {@link ClassDesc#of(String)}.
     * Array names like {@code "[Ljava.lang.String;"} have dots replaced with slashes before
     * passing to {@link ClassDesc#ofDescriptor(String)}.
     *
     * @param className the class name in {@link Class#getName()} format (must not be {@code null})
     * @return the class descriptor (not {@code null})
     */
    public static ClassDesc classDescOfName(String className) {
        if (className.startsWith("[")) {
            return ClassDesc.ofDescriptor(className.replace('.', '/'));
        }
        return ClassDesc.of(className);
    }

    /**
     * Compare two {@link ClassDesc} instances for equality by comparing their descriptor strings.
     * This avoids the JDK's {@link ClassDesc#equals(Object)} implementation which has
     * performance issues in certain cases.
     *
     * @param a the first class descriptor (must not be {@code null})
     * @param b the second class descriptor (must not be {@code null})
     * @return {@code true} if the two descriptors represent the same type
     */
    public static boolean classDescEquals(ClassDesc a, ClassDesc b) {
        return a.descriptorString().equals(b.descriptorString());
    }

    public static final VarHandle BE16 = MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.BIG_ENDIAN)
            .withInvokeExactBehavior();
    public static final VarHandle BE32 = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN)
            .withInvokeExactBehavior();
    public static final VarHandle BE64 = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN)
            .withInvokeExactBehavior();

    private static final MethodHandle SERIALIZED_SERIALIZABLE_CTOR;
    private static final MethodHandle SERIALIZED_OBJECT_ARRAY_CTOR;
    private static final MethodHandle SERIALIZED_PROXY_OBJECT_CTOR;
    private static final MethodHandle SERIALIZED_RECORD_CTOR;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(SerializedSerializable.class, MethodHandles.lookup());
            SERIALIZED_SERIALIZABLE_CTOR = lookup.findConstructor(
                    SerializedSerializable.class,
                    MethodType.methodType(void.class, Object.class, ObjectSerializer.Context.class));
            lookup = MethodHandles.privateLookupIn(SerializedObjectArray.class, MethodHandles.lookup());
            SERIALIZED_OBJECT_ARRAY_CTOR = lookup.findConstructor(
                    SerializedObjectArray.class,
                    MethodType.methodType(void.class, Object[].class, ObjectSerializer.Context.class));
            lookup = MethodHandles.privateLookupIn(SerializedProxyObject.class, MethodHandles.lookup());
            SERIALIZED_PROXY_OBJECT_CTOR = lookup.findConstructor(
                    SerializedProxyObject.class,
                    MethodType.methodType(void.class, Object.class, SerializedProxyClass.class,
                            ObjectSerializer.Context.class));
            lookup = MethodHandles.privateLookupIn(SerializedRecord.class, MethodHandles.lookup());
            SERIALIZED_RECORD_CTOR = lookup.findConstructor(
                    SerializedRecord.class,
                    MethodType.methodType(void.class, Object.class, ObjectSerializer.Context.class));
        } catch (ReflectiveOperationException e) {
            throw asError(e);
        }
    }

    /**
     * Construct a new {@link SerializedSerializable} via method handle, bypassing the package-private constructor.
     *
     * @param object the object being serialized (must not be {@code null})
     * @param ctxt the serializer context (must not be {@code null})
     * @return the serialized representation (not {@code null})
     * @throws IOException if serialization fails
     */
    public static SerializedSerializable newSerializedSerializable(Object object, ObjectSerializer.Context ctxt)
            throws IOException {
        try {
            return (SerializedSerializable) SERIALIZED_SERIALIZABLE_CTOR.invoke(object, ctxt);
        } catch (IOException | RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw sneak(t);
        }
    }

    /**
     * Construct a new {@link SerializedObjectArray} via method handle, bypassing the package-private constructor.
     * This constructor pre-registers the array before serializing elements, handling circular references.
     *
     * @param source the source object array (must not be {@code null})
     * @param ctxt the serializer context (must not be {@code null})
     * @return the serialized representation (not {@code null})
     * @throws IOException if serialization fails
     */
    public static SerializedObjectArray newSerializedObjectArray(Object[] source, ObjectSerializer.Context ctxt)
            throws IOException {
        try {
            return (SerializedObjectArray) SERIALIZED_OBJECT_ARRAY_CTOR.invoke(source, ctxt);
        } catch (IOException | RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw sneak(t);
        }
    }

    /**
     * Construct a new {@link SerializedProxyObject} via method handle, bypassing the package-private constructor.
     * This constructor pre-registers the proxy object before serializing the invocation handler,
     * handling circular references.
     *
     * @param proxy the proxy instance (must not be {@code null})
     * @param proxyClass the proxy class descriptor (must not be {@code null})
     * @param ctxt the serializer context (must not be {@code null})
     * @return the serialized representation (not {@code null})
     * @throws IOException if serialization fails
     */
    public static SerializedProxyObject newSerializedProxyObject(Object proxy, SerializedProxyClass proxyClass,
            ObjectSerializer.Context ctxt) throws IOException {
        try {
            return (SerializedProxyObject) SERIALIZED_PROXY_OBJECT_CTOR.invoke(proxy, proxyClass, ctxt);
        } catch (IOException | RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw sneak(t);
        }
    }

    /**
     * Construct a new {@link SerializedRecord} via method handle, bypassing the package-private constructor.
     * This constructor pre-registers the record before serializing field data, handling circular references.
     *
     * @param record the record instance (must not be {@code null})
     * @param ctxt the serializer context (must not be {@code null})
     * @return the serialized representation (not {@code null})
     * @throws IOException if serialization fails
     */
    public static SerializedRecord newSerializedRecord(Object record, ObjectSerializer.Context ctxt) throws IOException {
        try {
            return (SerializedRecord) SERIALIZED_RECORD_CTOR.invoke(record, ctxt);
        } catch (IOException | RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw sneak(t);
        }
    }

    @SuppressWarnings("unchecked")
    public static <E extends Throwable> RuntimeException sneak(Throwable e) throws E {
        throw (E) e;
    }

    public static Error asError(final ReflectiveOperationException e) {
        Error error;
        if (e instanceof ClassNotFoundException) {
            error = new NoClassDefFoundError(e.getMessage());
        } else if (e instanceof IllegalAccessException) {
            error = new IllegalAccessError(e.getMessage());
        } else if (e instanceof InstantiationException) {
            error = new InstantiationError(e.getMessage());
        } else if (e instanceof InvocationTargetException) {
            throw sneak(e.getCause());
        } else if (e instanceof NoSuchFieldException) {
            error = new NoSuchFieldError(e.getMessage());
        } else if (e instanceof NoSuchMethodException) {
            error = new NoSuchMethodError(e.getMessage());
        } else {
            error = new LinkageError(e.toString());
        }
        error.setStackTrace(e.getStackTrace());
        throw error;
    }
}
