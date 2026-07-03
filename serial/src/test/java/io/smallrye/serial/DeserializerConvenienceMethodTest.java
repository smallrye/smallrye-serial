package io.smallrye.serial;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * Tests for the convenience methods on {@link Deserializer}:
 * {@link Deserializer#deserialize(Serialized, Class)},
 * {@link Deserializer#deserializeClass(Serialized)}, and
 * {@link Deserializer#deserializeClass(Serialized, Class)}.
 */
class DeserializerConvenienceMethodTest {

    private final SerialContext ctx = SerialContext.builder().addDefaultProviders().build();
    private final Serializer ser = ctx.createSerializer();
    private final Deserializer des = ctx.createDeserializer();

    @Test
    void deserializeWithType() throws IOException, ClassNotFoundException {
        Serialized serialized = ser.serialize("hello");
        String result = des.deserialize(serialized, String.class);
        assertEquals("hello", result);
    }

    @Test
    void deserializeWithWrongTypeThrows() throws IOException {
        Serialized serialized = ser.serialize("hello");
        assertThrows(ClassCastException.class, () -> des.deserialize(serialized, Integer.class));
    }

    @Test
    void deserializeClass() throws IOException, ClassNotFoundException {
        Serialized serialized = ser.serialize(String.class);
        Class<?> result = des.deserializeClass(serialized);
        assertSame(String.class, result);
    }

    @Test
    void deserializeClassWithBound() throws IOException, ClassNotFoundException {
        Serialized serialized = ser.serialize(Integer.class);
        Class<? extends Number> result = des.deserializeClass(serialized, Number.class);
        assertSame(Integer.class, result);
    }

    @Test
    void deserializeClassWithWrongBoundThrows() throws IOException {
        Serialized serialized = ser.serialize(Integer.class);
        assertThrows(ClassCastException.class, () -> des.deserializeClass(serialized, String.class));
    }
}
