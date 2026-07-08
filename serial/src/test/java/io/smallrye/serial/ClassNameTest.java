package io.smallrye.serial;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * Tests that {@link SerializedClass#name()} matches {@link Class#getName()} for
 * all class descriptor types: primitives, arrays, regular classes, and nested types.
 */
class ClassNameTest {

    private final SerialContext ctx = SerialContext.builder().addDefaultProviders().build();
    private final Serializer ser = ctx.createSerializer();

    // ---- Primitive types ----

    @Test
    void booleanName() {
        assertEquals(boolean.class.getName(), SerializedPrimitiveClass.BOOLEAN.name());
    }

    @Test
    void byteName() {
        assertEquals(byte.class.getName(), SerializedPrimitiveClass.BYTE.name());
    }

    @Test
    void charName() {
        assertEquals(char.class.getName(), SerializedPrimitiveClass.CHAR.name());
    }

    @Test
    void shortName() {
        assertEquals(short.class.getName(), SerializedPrimitiveClass.SHORT.name());
    }

    @Test
    void intName() {
        assertEquals(int.class.getName(), SerializedPrimitiveClass.INT.name());
    }

    @Test
    void longName() {
        assertEquals(long.class.getName(), SerializedPrimitiveClass.LONG.name());
    }

    @Test
    void floatName() {
        assertEquals(float.class.getName(), SerializedPrimitiveClass.FLOAT.name());
    }

    @Test
    void doubleName() {
        assertEquals(double.class.getName(), SerializedPrimitiveClass.DOUBLE.name());
    }

    @Test
    void voidName() {
        assertEquals(void.class.getName(), SerializedPrimitiveClass.VOID.name());
    }

    // ---- Primitive array types ----

    @Test
    void booleanArrayName() {
        assertEquals(boolean[].class.getName(), SerializedArrayClass.BOOLEAN_ARRAY.name());
    }

    @Test
    void byteArrayName() {
        assertEquals(byte[].class.getName(), SerializedArrayClass.BYTE_ARRAY.name());
    }

    @Test
    void charArrayName() {
        assertEquals(char[].class.getName(), SerializedArrayClass.CHAR_ARRAY.name());
    }

    @Test
    void shortArrayName() {
        assertEquals(short[].class.getName(), SerializedArrayClass.SHORT_ARRAY.name());
    }

    @Test
    void intArrayName() {
        assertEquals(int[].class.getName(), SerializedArrayClass.INT_ARRAY.name());
    }

    @Test
    void longArrayName() {
        assertEquals(long[].class.getName(), SerializedArrayClass.LONG_ARRAY.name());
    }

    @Test
    void floatArrayName() {
        assertEquals(float[].class.getName(), SerializedArrayClass.FLOAT_ARRAY.name());
    }

    @Test
    void doubleArrayName() {
        assertEquals(double[].class.getName(), SerializedArrayClass.DOUBLE_ARRAY.name());
    }

    // ---- Object array types ----

    @Test
    void stringArrayName() throws IOException {
        Serialized s = ser.serialize(String[].class);
        assertInstanceOf(SerializedArrayClass.class, s);
        assertEquals(String[].class.getName(), ((SerializedClass) s).name());
    }

    @Test
    void nestedObjectArrayName() throws IOException {
        Serialized s = ser.serialize(String[][].class);
        assertInstanceOf(SerializedArrayClass.class, s);
        assertEquals(String[][].class.getName(), ((SerializedClass) s).name());
    }

    @Test
    void intMultiDimArrayName() throws IOException {
        Serialized s = ser.serialize(int[][].class);
        assertInstanceOf(SerializedArrayClass.class, s);
        assertEquals(int[][].class.getName(), ((SerializedClass) s).name());
    }

    // ---- Reference types ----

    @Test
    void regularClassName() throws IOException {
        Serialized s = ser.serialize(String.class);
        assertInstanceOf(SerializedClass.class, s);
        assertEquals(String.class.getName(), ((SerializedClass) s).name());
    }

    @Test
    void innerClassName() throws IOException {
        Serialized s = ser.serialize(java.util.Map.Entry.class);
        assertInstanceOf(SerializedClass.class, s);
        assertEquals(java.util.Map.Entry.class.getName(), ((SerializedClass) s).name());
    }

    @Test
    void serializableClassName() throws IOException {
        Serialized s = ser.serialize(java.util.ArrayList.class);
        assertInstanceOf(SerializedSerializableClass.class, s);
        assertEquals(java.util.ArrayList.class.getName(), ((SerializedClass) s).name());
    }

    @Test
    void enumClassName() throws IOException {
        Serialized s = ser.serialize(Thread.State.class);
        assertInstanceOf(SerializedEnumClass.class, s);
        assertEquals(Thread.State.class.getName(), ((SerializedClass) s).name());
    }

    @Test
    void nonSerializableClassName() throws IOException {
        Serialized s = ser.serialize(Thread.class);
        assertInstanceOf(SerializedNonSerializableClass.class, s);
        assertEquals(Thread.class.getName(), ((SerializedClass) s).name());
    }
}
