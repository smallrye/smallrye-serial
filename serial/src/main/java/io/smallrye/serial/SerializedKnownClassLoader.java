package io.smallrye.serial;

/**
 * The serialized representation of a known class loader.
 * <p>
 * This includes the three built-in JVM class loaders (boot, platform, and application),
 * which are singletons within a JVM, as well as the {@linkplain #forUnspecifiedClassLoader()
 * unspecified} class loader, which represents a class loader that was not specified by the
 * serialization source (e.g. the Java serialization wire format does not encode class loader
 * information).
 */
public final class SerializedKnownClassLoader extends Serialized {
    private static final SerializedKnownClassLoader BOOT = new SerializedKnownClassLoader(Kind.BOOT);
    private static final SerializedKnownClassLoader PLATFORM = new SerializedKnownClassLoader(Kind.PLATFORM);
    private static final SerializedKnownClassLoader APP = new SerializedKnownClassLoader(Kind.APP);
    private static final SerializedKnownClassLoader UNSPECIFIED = new SerializedKnownClassLoader(Kind.UNSPECIFIED);

    private final Kind kind;

    private SerializedKnownClassLoader(final Kind kind) {
        this.kind = kind;
    }

    /**
     * {@return the kind of known class loader this instance represents (not {@code null})}
     */
    public Kind kind() {
        return kind;
    }

    /**
     * {@return the class loader represented by this serialized form}
     * For the {@linkplain Kind#BOOT boot} class loader, this returns {@code null}.
     * For the {@linkplain Kind#UNSPECIFIED unspecified} class loader, this returns the
     * {@linkplain Thread#getContextClassLoader() thread context class loader} of the calling thread.
     */
    public ClassLoader classLoader() {
        return kind.classLoader();
    }

    /**
     * {@return the singleton instance representing the boot (null) class loader}
     */
    public static SerializedKnownClassLoader forBootClassLoader() {
        return BOOT;
    }

    /**
     * {@return the singleton instance representing the platform class loader}
     */
    public static SerializedKnownClassLoader forPlatformClassLoader() {
        return PLATFORM;
    }

    /**
     * {@return the singleton instance representing the application (system) class loader}
     */
    public static SerializedKnownClassLoader forAppClassLoader() {
        return APP;
    }

    /**
     * {@return the singleton instance representing an unspecified class loader}
     * This is used when the serialization source does not encode class loader information,
     * such as the standard Java serialization wire format. The {@link #classLoader()} method
     * returns the thread context class loader of the calling thread.
     */
    public static SerializedKnownClassLoader forUnspecifiedClassLoader() {
        return UNSPECIFIED;
    }

    /**
     * The kind of known class loader.
     */
    public enum Kind {
        /**
         * The boot (null) class loader.
         */
        BOOT(null),
        /**
         * The platform class loader.
         */
        PLATFORM(ClassLoader.getPlatformClassLoader()),
        /**
         * The application (system) class loader.
         */
        APP(ClassLoader.getSystemClassLoader()),
        /**
         * An unspecified class loader.
         * This indicates that the serialization source did not specify a class loader.
         * The {@link #classLoader()} method returns the thread context class loader
         * of the calling thread.
         */
        UNSPECIFIED(null) {
            @Override
            ClassLoader classLoader() {
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                return cl != null ? cl : ClassLoader.getSystemClassLoader();
            }
        },
        ;

        private final ClassLoader classLoader;

        Kind(final ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        /**
         * {@return the class loader for this kind}
         */
        ClassLoader classLoader() {
            return classLoader;
        }
    }
}
