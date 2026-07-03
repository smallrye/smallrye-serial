package io.smallrye.serial;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * Tests for round-trip serialization and deserialization of {@link Class} objects and class loaders.
 */
class ClassAndClassLoaderTest {

    private final SerialContext ctx = SerialContext.builder().addDefaultProviders().build();
    private final Serializer ser = ctx.createSerializer();
    private final Deserializer des = ctx.createDeserializer();

    @Test
    void serializableClassRoundTrip() throws IOException, ClassNotFoundException {
        Serialized serialized = ser.serialize(java.util.ArrayList.class);
        assertInstanceOf(SerializedSerializableClass.class, serialized);
        assertSame(java.util.ArrayList.class, des.deserialize(serialized));
    }

    @Test
    void nonSerializableClassRoundTrip() throws IOException, ClassNotFoundException {
        Serialized serialized = ser.serialize(Thread.class);
        assertInstanceOf(SerializedNonSerializableClass.class, serialized);
        assertSame(Thread.class, des.deserialize(serialized));
    }

    @Test
    void enumClassRoundTrip() throws IOException, ClassNotFoundException {
        Serialized serialized = ser.serialize(Thread.State.class);
        assertInstanceOf(SerializedEnumClass.class, serialized);
        assertSame(Thread.State.class, des.deserialize(serialized));
    }

    @Test
    void arrayClassRoundTrip() throws IOException, ClassNotFoundException {
        Serialized serialized = ser.serialize(int[].class);
        assertInstanceOf(SerializedArrayClass.class, serialized);
        SerializedArrayClass sac = (SerializedArrayClass) serialized;
        assertInstanceOf(SerializedPrimitiveClass.class, sac.componentType());
        assertSame(int[].class, des.deserialize(serialized));
    }

    @Test
    void multiDimArrayClassRoundTrip() throws IOException, ClassNotFoundException {
        Serialized serialized = ser.serialize(String[][].class);
        assertInstanceOf(SerializedArrayClass.class, serialized);
        SerializedArrayClass outer = (SerializedArrayClass) serialized;
        assertInstanceOf(SerializedArrayClass.class, outer.componentType());
        assertSame(String[][].class, des.deserialize(serialized));
    }

    @Test
    void specialSerializableStringRoundTrip() throws IOException, ClassNotFoundException {
        Serialized serialized = ser.serialize(String.class);
        assertSame(SerializedSpecialSerializableClass.STRING, serialized);
        assertSame(String.class, des.deserialize(serialized));
    }

    @Test
    void specialSerializableEnumRoundTrip() throws IOException, ClassNotFoundException {
        Serialized serialized = ser.serialize(Enum.class);
        assertSame(SerializedSpecialSerializableClass.ENUM, serialized);
        assertSame(Enum.class, des.deserialize(serialized));
    }

    @Test
    void appClassLoaderRoundTrip() throws IOException, ClassNotFoundException {
        ClassLoader appCl = ClassLoader.getSystemClassLoader();
        Serialized serialized = ser.serialize(appCl);
        assertInstanceOf(SerializedKnownClassLoader.class, serialized);
        assertSame(appCl, des.deserialize(serialized));
    }

    @Test
    void platformClassLoaderRoundTrip() throws IOException, ClassNotFoundException {
        ClassLoader platformCl = ClassLoader.getPlatformClassLoader();
        Serialized serialized = ser.serialize(platformCl);
        assertInstanceOf(SerializedKnownClassLoader.class, serialized);
        assertSame(platformCl, des.deserialize(serialized));
    }

    @Test
    void bootClassLoaderRoundTrip() throws IOException, ClassNotFoundException {
        // String is loaded by the boot class loader (null)
        Serialized serialized = ser.serialize(String.class);
        assertInstanceOf(SerializedSpecialSerializableClass.class, serialized);
        SerializedClass sc = (SerializedClass) serialized;
        assertSame(SerializedKnownClassLoader.forBootClassLoader(), sc.classLoader());
        assertSame(String.class, des.deserialize(serialized));
    }

    @Test
    void nullClassLoaderRoundTrip() throws IOException, ClassNotFoundException {
        Serialized serialized = ser.serialize(null);
        assertSame(SerializedNull.INSTANCE, serialized);
        assertNull(des.deserialize(serialized));
    }
}
