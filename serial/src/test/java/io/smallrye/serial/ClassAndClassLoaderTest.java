package io.smallrye.serial;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * Tests for round-trip serialization and deserialization of {@link Class} objects and class loaders.
 */
class ClassAndClassLoaderTest {

    private final SerialContext ctx = SerialContext.builder().addDefaultProviders().build();

    @Test
    void serializableClassRoundTrip() throws IOException, ClassNotFoundException {
        Serialized serialized = ctx.serialize(java.util.ArrayList.class);
        assertInstanceOf(SerializedSerializableClass.class, serialized);
        assertSame(java.util.ArrayList.class, ctx.deserialize(serialized));
    }

    @Test
    void nonSerializableClassRoundTrip() throws IOException, ClassNotFoundException {
        Serialized serialized = ctx.serialize(Thread.class);
        assertInstanceOf(SerializedNonSerializableClass.class, serialized);
        assertSame(Thread.class, ctx.deserialize(serialized));
    }

    @Test
    void enumClassRoundTrip() throws IOException, ClassNotFoundException {
        Serialized serialized = ctx.serialize(Thread.State.class);
        assertInstanceOf(SerializedEnumClass.class, serialized);
        assertSame(Thread.State.class, ctx.deserialize(serialized));
    }

    @Test
    void arrayClassRoundTrip() throws IOException, ClassNotFoundException {
        Serialized serialized = ctx.serialize(int[].class);
        assertInstanceOf(SerializedArrayClass.class, serialized);
        SerializedArrayClass sac = (SerializedArrayClass) serialized;
        assertInstanceOf(SerializedPrimitiveClass.class, sac.componentType());
        assertSame(int[].class, ctx.deserialize(serialized));
    }

    @Test
    void multiDimArrayClassRoundTrip() throws IOException, ClassNotFoundException {
        Serialized serialized = ctx.serialize(String[][].class);
        assertInstanceOf(SerializedArrayClass.class, serialized);
        SerializedArrayClass outer = (SerializedArrayClass) serialized;
        assertInstanceOf(SerializedArrayClass.class, outer.componentType());
        assertSame(String[][].class, ctx.deserialize(serialized));
    }

    @Test
    void specialSerializableStringRoundTrip() throws IOException, ClassNotFoundException {
        Serialized serialized = ctx.serialize(String.class);
        assertSame(SerializedSpecialSerializableClass.STRING, serialized);
        assertSame(String.class, ctx.deserialize(serialized));
    }

    @Test
    void specialSerializableEnumRoundTrip() throws IOException, ClassNotFoundException {
        Serialized serialized = ctx.serialize(Enum.class);
        assertSame(SerializedSpecialSerializableClass.ENUM, serialized);
        assertSame(Enum.class, ctx.deserialize(serialized));
    }

    @Test
    void appClassLoaderRoundTrip() throws IOException, ClassNotFoundException {
        ClassLoader appCl = ClassLoader.getSystemClassLoader();
        Serialized serialized = ctx.serialize(appCl);
        assertInstanceOf(SerializedBuiltInClassLoader.class, serialized);
        assertSame(appCl, ctx.deserialize(serialized));
    }

    @Test
    void platformClassLoaderRoundTrip() throws IOException, ClassNotFoundException {
        ClassLoader platformCl = ClassLoader.getPlatformClassLoader();
        Serialized serialized = ctx.serialize(platformCl);
        assertInstanceOf(SerializedBuiltInClassLoader.class, serialized);
        assertSame(platformCl, ctx.deserialize(serialized));
    }

    @Test
    void bootClassLoaderRoundTrip() throws IOException, ClassNotFoundException {
        // String is loaded by the boot class loader (null)
        Serialized serialized = ctx.serialize(String.class);
        assertInstanceOf(SerializedSpecialSerializableClass.class, serialized);
        SerializedClass sc = (SerializedClass) serialized;
        assertSame(SerializedBuiltInClassLoader.forBootClassLoader(), sc.classLoader());
        assertSame(String.class, ctx.deserialize(serialized));
    }

    @Test
    void nullClassLoaderRoundTrip() throws IOException, ClassNotFoundException {
        Serialized serialized = ctx.serialize(null);
        assertSame(SerializedNull.INSTANCE, serialized);
        assertNull(ctx.deserialize(serialized));
    }
}
