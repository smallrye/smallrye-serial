package io.smallrye.serial;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.Serializable;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for correct handling of mismatches between the class data list and the
 * class descriptor hierarchy in {@link SerializedSerializable}.
 * <p>
 * These tests exercise gap handling (hierarchy levels with no corresponding data),
 * extra data rejection, and out-of-order data rejection.
 */
class HierarchyMismatchTest {

    private final SerialContext ctx = SerialContext.builder().addDefaultProviders().build();
    private final Deserializer des = ctx.createDeserializer();

    // ---- Test fixtures ----

    /**
     * A simple serializable base class with one int field.
     */
    public static class Base implements Serializable {
        private static final long serialVersionUID = 1L;
        int baseValue;

        /** No-arg constructor. */
        public Base() {
        }
    }

    /**
     * A subclass adding an int field, for two-level hierarchy tests.
     */
    public static class Sub extends Base {
        private static final long serialVersionUID = 2L;
        int subValue;

        /** No-arg constructor. */
        public Sub() {
        }
    }

    // ---- Gap tests ----

    @Test
    void gapAtBaseLevel() throws IOException, ClassNotFoundException {
        // Build class descriptors for a two-level hierarchy: Base → Sub
        SerializedSerializableClass baseClass = SerializedSerializableClass.builder()
                .name(Base.class.getName())
                .classLoader(SerializedKnownClassLoader.forAppClassLoader())
                .uid(1L)
                .addField("baseValue", int.class)
                .build();
        SerializedSerializableClass subClass = SerializedSerializableClass.builder()
                .name(Sub.class.getName())
                .classLoader(SerializedKnownClassLoader.forAppClassLoader())
                .uid(2L)
                .superClass(baseClass)
                .addField("subValue", int.class)
                .build();

        // Provide data only for the leaf (Sub), leaving a gap at the base level
        byte[] subPrimData = new byte[4];
        subPrimData[0] = 0;
        subPrimData[1] = 0;
        subPrimData[2] = 0;
        subPrimData[3] = 99;
        SerialData subData = new SerialData(subClass,
                StreamData.of(subPrimData),
                StreamData.OfObjects.EMPTY,
                List.of());
        SerializedSerializable serialized = new SerializedSerializable(subClass, List.of(subData));

        Sub result = (Sub) des.deserialize(serialized);
        assertEquals(0, result.baseValue, "base level should have default value (gap)");
        assertEquals(99, result.subValue, "sub level should have the provided value");
    }

    @Test
    void gapAtLeafLevel() throws IOException, ClassNotFoundException {
        // Build class descriptors for a two-level hierarchy: Base → Sub
        SerializedSerializableClass baseClass = SerializedSerializableClass.builder()
                .name(Base.class.getName())
                .classLoader(SerializedKnownClassLoader.forAppClassLoader())
                .uid(1L)
                .addField("baseValue", int.class)
                .build();
        SerializedSerializableClass subClass = SerializedSerializableClass.builder()
                .name(Sub.class.getName())
                .classLoader(SerializedKnownClassLoader.forAppClassLoader())
                .uid(2L)
                .superClass(baseClass)
                .addField("subValue", int.class)
                .build();

        // Provide data only for the base level, leaving a gap at the leaf (Sub)
        byte[] basePrimData = new byte[4];
        basePrimData[0] = 0;
        basePrimData[1] = 0;
        basePrimData[2] = 0;
        basePrimData[3] = 42;
        SerialData baseData = new SerialData(baseClass,
                StreamData.of(basePrimData),
                StreamData.OfObjects.EMPTY,
                List.of());
        SerializedSerializable serialized = new SerializedSerializable(subClass, List.of(baseData));

        Sub result = (Sub) des.deserialize(serialized);
        assertEquals(42, result.baseValue, "base level should have the provided value");
        assertEquals(0, result.subValue, "sub level should have default value (gap)");
    }

    // ---- Extra data test ----

    @Test
    void extraDataRejected() throws IOException {
        // Build a single-level class descriptor
        SerializedSerializableClass baseClass = SerializedSerializableClass.builder()
                .name(Base.class.getName())
                .classLoader(SerializedKnownClassLoader.forAppClassLoader())
                .uid(1L)
                .addField("baseValue", int.class)
                .build();

        // Build a bogus class descriptor for the extra data
        SerializedSerializableClass bogusClass = SerializedSerializableClass.builder()
                .name("com.example.Bogus")
                .classLoader(SerializedKnownClassLoader.forAppClassLoader())
                .uid(999L)
                .build();

        SerialData baseData = new SerialData(baseClass,
                StreamData.of(new byte[4]),
                StreamData.OfObjects.EMPTY,
                List.of());
        SerialData extraData = new SerialData(bogusClass,
                StreamData.OfBytes.EMPTY,
                StreamData.OfObjects.EMPTY,
                List.of());

        // Data list has an entry for a class not in the descriptor chain
        SerializedSerializable serialized = new SerializedSerializable(baseClass,
                List.of(baseData, extraData));

        assertThrows(InvalidObjectException.class, () -> des.deserialize(serialized),
                "should reject unconsumed extra data");
    }

    // ---- Out-of-order data test ----

    @Test
    void outOfOrderDataRejected() throws IOException {
        // Build class descriptors for a two-level hierarchy: Base → Sub
        SerializedSerializableClass baseClass = SerializedSerializableClass.builder()
                .name(Base.class.getName())
                .classLoader(SerializedKnownClassLoader.forAppClassLoader())
                .uid(1L)
                .addField("baseValue", int.class)
                .build();
        SerializedSerializableClass subClass = SerializedSerializableClass.builder()
                .name(Sub.class.getName())
                .classLoader(SerializedKnownClassLoader.forAppClassLoader())
                .uid(2L)
                .superClass(baseClass)
                .addField("subValue", int.class)
                .build();

        SerialData baseData = new SerialData(baseClass,
                StreamData.of(new byte[4]),
                StreamData.OfObjects.EMPTY,
                List.of());
        SerialData subData = new SerialData(subClass,
                StreamData.of(new byte[4]),
                StreamData.OfObjects.EMPTY,
                List.of());

        // Data list is in leaf-to-root order (wrong — should be root-to-leaf)
        SerializedSerializable serialized = new SerializedSerializable(subClass,
                List.of(subData, baseData));

        assertThrows(InvalidObjectException.class, () -> des.deserialize(serialized),
                "should reject out-of-order class data");
    }

    // ---- Printer tests ----

    @Test
    void printerFlagsExtraData() throws IOException {
        SerializedSerializableClass baseClass = SerializedSerializableClass.builder()
                .name("com.example.Base")
                .classLoader(SerializedKnownClassLoader.forAppClassLoader())
                .uid(1L)
                .addField("x", int.class)
                .build();

        SerializedSerializableClass bogusClass = SerializedSerializableClass.builder()
                .name("com.example.Unexpected")
                .classLoader(SerializedKnownClassLoader.forAppClassLoader())
                .uid(999L)
                .build();

        SerialData baseData = new SerialData(baseClass,
                StreamData.of(new byte[4]),
                StreamData.OfObjects.EMPTY,
                List.of());
        SerialData extraData = new SerialData(bogusClass,
                StreamData.OfBytes.EMPTY,
                StreamData.OfObjects.EMPTY,
                List.of());

        SerializedSerializable serialized = new SerializedSerializable(baseClass,
                List.of(baseData, extraData));

        String output = Printer.print(serialized);
        assertTrue(output.contains("<unexpected data for com.example.Unexpected>"), output);
    }

    @Test
    void printerHandlesGapAtBaseLevel() {
        SerializedSerializableClass baseClass = SerializedSerializableClass.builder()
                .name("com.example.Base")
                .classLoader(SerializedKnownClassLoader.forAppClassLoader())
                .uid(1L)
                .addField("x", int.class)
                .build();
        SerializedSerializableClass subClass = SerializedSerializableClass.builder()
                .name("com.example.Sub")
                .classLoader(SerializedKnownClassLoader.forAppClassLoader())
                .uid(2L)
                .superClass(baseClass)
                .addField("y", int.class)
                .build();

        // only provide data for the leaf level (gap at base)
        byte[] subPrimData = new byte[4];
        subPrimData[3] = 7;
        SerialData subData = new SerialData(subClass,
                StreamData.of(subPrimData),
                StreamData.OfObjects.EMPTY,
                List.of());
        SerializedSerializable serialized = new SerializedSerializable(subClass, List.of(subData));

        String output = Printer.print(serialized);
        // the class descriptor chain will mention Base, but there should be no data block for it;
        // specifically, there should be no "int x =" line (Base's field) in the output
        assertFalse(output.contains("int x ="), output);
        assertTrue(output.contains("int y = 7"), output);
    }

    @Test
    void printerFlagsOutOfOrderData() {
        SerializedSerializableClass baseClass = SerializedSerializableClass.builder()
                .name("com.example.Base")
                .classLoader(SerializedKnownClassLoader.forAppClassLoader())
                .uid(1L)
                .addField("x", int.class)
                .build();
        SerializedSerializableClass subClass = SerializedSerializableClass.builder()
                .name("com.example.Sub")
                .classLoader(SerializedKnownClassLoader.forAppClassLoader())
                .uid(2L)
                .superClass(baseClass)
                .addField("y", int.class)
                .build();

        SerialData baseData = new SerialData(baseClass,
                StreamData.of(new byte[4]),
                StreamData.OfObjects.EMPTY,
                List.of());
        SerialData subData = new SerialData(subClass,
                StreamData.of(new byte[4]),
                StreamData.OfObjects.EMPTY,
                List.of());

        // data in leaf-to-root order (wrong)
        SerializedSerializable serialized = new SerializedSerializable(subClass,
                List.of(subData, baseData));

        String output = Printer.print(serialized);
        // both entries should be flagged as unexpected since neither matches in order
        assertTrue(output.contains("<unexpected data for"), output);
    }
}
