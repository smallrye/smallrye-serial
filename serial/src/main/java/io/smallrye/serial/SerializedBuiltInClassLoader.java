package io.smallrye.serial;

/**
 * The serialized representation of a built-in class loader (boot, platform, or application).
 * These class loaders are singletons within a JVM, so they are represented as
 * singleton instances rather than by name.
 */
public final class SerializedBuiltInClassLoader extends Serialized {
    private static final SerializedBuiltInClassLoader BOOT = new SerializedBuiltInClassLoader(Kind.BOOT);
    private static final SerializedBuiltInClassLoader PLATFORM = new SerializedBuiltInClassLoader(Kind.PLATFORM);
    private static final SerializedBuiltInClassLoader APP = new SerializedBuiltInClassLoader(Kind.APP);

    private final Kind kind;

    private SerializedBuiltInClassLoader(final Kind kind) {
        this.kind = kind;
    }

    /**
     * {@return the kind of built-in class loader this instance represents (not {@code null})}
     */
    public Kind kind() {
        return kind;
    }

    /**
     * {@return the class loader represented by this serialized form, or {@code null} for the boot class loader}
     */
    public ClassLoader classLoader() {
        return kind.classLoader;
    }

    /**
     * {@return the singleton instance representing the boot (null) class loader}
     */
    public static SerializedBuiltInClassLoader forBootClassLoader() {
        return BOOT;
    }

    /**
     * {@return the singleton instance representing the platform class loader}
     */
    public static SerializedBuiltInClassLoader forPlatformClassLoader() {
        return PLATFORM;
    }

    /**
     * {@return the singleton instance representing the application (system) class loader}
     */
    public static SerializedBuiltInClassLoader forAppClassLoader() {
        return APP;
    }

    /**
     * The kind of built-in class loader.
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
        ;

        final ClassLoader classLoader;

        Kind(final ClassLoader classLoader) {
            this.classLoader = classLoader;
        }
    }
}
