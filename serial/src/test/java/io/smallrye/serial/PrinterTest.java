package io.smallrye.serial;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link Printer} utility.
 */
class PrinterTest {

    private final SerialContext ctx = SerialContext.builder().addDefaultProviders().build();

    // ---- Leaf types ----

    @Test
    void printNull() {
        assertEquals("null", Printer.print(SerializedNull.INSTANCE));
    }

    @Test
    void printString() {
        assertEquals("\"hello\"", Printer.print(new SerializedString("hello")));
    }

    @Test
    void printStringWithEscapes() {
        assertEquals("\"line1\\nline2\\t\\\\end\"",
                Printer.print(new SerializedString("line1\nline2\t\\end")));
    }

    @Test
    void printBuiltInClassLoaders() {
        assertEquals("boot class loader",
                Printer.print(SerializedBuiltInClassLoader.forBootClassLoader()));
        assertEquals("platform class loader",
                Printer.print(SerializedBuiltInClassLoader.forPlatformClassLoader()));
        assertEquals("app class loader",
                Printer.print(SerializedBuiltInClassLoader.forAppClassLoader()));
    }

    @Test
    void printPrimitiveClass() {
        assertEquals("int", Printer.print(SerializedPrimitiveClass.INT));
        assertEquals("boolean", Printer.print(SerializedPrimitiveClass.BOOLEAN));
    }

    // ---- Class descriptors ----

    @Test
    void printEnumClass() throws IOException {
        Serialized serialized = ctx.serialize(Thread.State.class);
        String output = Printer.print(serialized);
        assertTrue(output.contains("#1 enum class java.lang.Thread$State"), output);
        assertTrue(output.contains("serialVersionUID:"), output);
        assertTrue(output.contains("classLoader:"), output);
    }

    @Test
    void printSerializableClassWithFields() {
        SerializedSerializableClass sc = SerializedSerializableClass.builder()
                .name("com.example.Point")
                .classLoader(SerializedBuiltInClassLoader.forAppClassLoader())
                .uid(1L)
                .addField("x", int.class)
                .addField("y", int.class)
                .build();

        String output = Printer.print(sc);
        assertTrue(output.contains("serializable class com.example.Point"), output);
        assertTrue(output.contains("serialVersionUID: 1L"), output);
        assertTrue(output.contains("fields {"), output);
        assertTrue(output.contains("int x"), output);
        assertTrue(output.contains("int y"), output);
    }

    @Test
    void printSerializableClassWithSuperClass() {
        SerializedSerializableClass superClass = SerializedSerializableClass.builder()
                .name("com.example.Base")
                .classLoader(SerializedBuiltInClassLoader.forAppClassLoader())
                .uid(1L)
                .addField("id", int.class)
                .build();

        SerializedSerializableClass subClass = SerializedSerializableClass.builder()
                .name("com.example.Sub")
                .classLoader(SerializedBuiltInClassLoader.forAppClassLoader())
                .uid(2L)
                .superClass(superClass)
                .addField("name", String.class)
                .build();

        String output = Printer.print(subClass);
        assertTrue(output.contains("serializable class com.example.Sub"), output);
        assertTrue(output.contains("superClass: #"), output);
        assertTrue(output.contains("serializable class com.example.Base"), output);
    }

    // ---- Object instances ----

    @Test
    void printSerializableWithPrimitiveFields() {
        SerializedSerializableClass sc = SerializedSerializableClass.builder()
                .name("com.example.Pair")
                .classLoader(SerializedBuiltInClassLoader.forAppClassLoader())
                .uid(1L)
                .addField("x", int.class)
                .addField("y", int.class)
                .build();

        // fields are sorted by name: x (offset 0), y (offset 4)
        byte[] primData = new byte[8];
        // x = 42 (big-endian)
        primData[0] = 0;
        primData[1] = 0;
        primData[2] = 0;
        primData[3] = 42;
        // y = 7 (big-endian)
        primData[4] = 0;
        primData[5] = 0;
        primData[6] = 0;
        primData[7] = 7;

        SerializedSerializable obj = new SerializedSerializable(sc, List.of(
                new SerialData(sc,
                        StreamData.of(primData),
                        StreamData.OfObjects.EMPTY,
                        List.of())));

        String output = Printer.print(obj);
        assertTrue(output.contains("serializable com.example.Pair"), output);
        assertTrue(output.contains("int x = 42"), output);
        assertTrue(output.contains("int y = 7"), output);
    }

    @Test
    void printSerializableWithObjectField() {
        SerializedSerializableClass sc = SerializedSerializableClass.builder()
                .name("com.example.Named")
                .classLoader(SerializedBuiltInClassLoader.forAppClassLoader())
                .uid(1L)
                .addField("name", String.class)
                .build();

        SerializedSerializable obj = new SerializedSerializable(sc, List.of(
                new SerialData(sc,
                        StreamData.OfBytes.EMPTY,
                        StreamData.of(new SerializedString("hello")),
                        List.of())));

        String output = Printer.print(obj);
        assertTrue(output.contains("java.lang.String name = \"hello\""), output);
    }

    @Test
    void printEnum() throws IOException {
        Serialized serialized = ctx.serialize(Thread.State.NEW);
        String output = Printer.print(serialized);
        assertTrue(output.contains("enum java.lang.Thread$State"), output);
        assertTrue(output.contains("constantName: \"NEW\""), output);
    }

    // ---- Arrays ----

    @Test
    void printIntArray() {
        SerializedArrayClass arrayClass = new SerializedArrayClass(
                ClassDesc.ofDescriptor("[I"),
                SerializedBuiltInClassLoader.forBootClassLoader(),
                0L,
                SerializedPrimitiveClass.INT);

        SerializedIntArray arr = new SerializedIntArray(arrayClass, new int[] { 1, 2, 3 });
        String output = Printer.print(arr);
        assertTrue(output.contains("int[3]"), output);
        assertTrue(output.contains("1,"), output);
        assertTrue(output.contains("2,"), output);
        assertTrue(output.contains("3,"), output);
    }

    @Test
    void printEmptyIntArray() {
        SerializedArrayClass arrayClass = new SerializedArrayClass(
                ClassDesc.ofDescriptor("[I"),
                SerializedBuiltInClassLoader.forBootClassLoader(),
                0L,
                SerializedPrimitiveClass.INT);

        SerializedIntArray arr = new SerializedIntArray(arrayClass, new int[0]);
        String output = Printer.print(arr);
        assertTrue(output.contains("int[0] {}"), output);
    }

    @Test
    void printByteArrayHexDump() {
        SerializedArrayClass arrayClass = new SerializedArrayClass(
                ClassDesc.ofDescriptor("[B"),
                SerializedBuiltInClassLoader.forBootClassLoader(),
                0L,
                SerializedPrimitiveClass.BYTE);

        SerializedByteArray arr = new SerializedByteArray(arrayClass,
                new byte[] { 0x48, 0x65, 0x6c, 0x6c, 0x6f });
        String output = Printer.print(arr);
        assertTrue(output.contains("byte[5]"), output);
        assertTrue(output.contains("00000000"), output);
        assertTrue(output.contains("48 65 6c 6c 6f"), output);
        assertTrue(output.contains("|Hello|"), output);
    }

    @Test
    void printCharArrayHexDump() {
        SerializedArrayClass arrayClass = new SerializedArrayClass(
                ClassDesc.ofDescriptor("[C"),
                SerializedBuiltInClassLoader.forBootClassLoader(),
                0L,
                SerializedPrimitiveClass.CHAR);

        SerializedCharArray arr = new SerializedCharArray(arrayClass,
                new char[] { 'H', 'i', '!' });
        String output = Printer.print(arr);
        assertTrue(output.contains("char[3]"), output);
        assertTrue(output.contains("0048 0069 0021"), output);
        assertTrue(output.contains("|Hi!|"), output);
    }

    @Test
    void printObjectArray() throws IOException {
        Serialized serialized = ctx.serialize(new String[] { "a", "b" });
        String output = Printer.print(serialized);
        assertTrue(output.contains("java.lang.String[2]"), output);
        assertTrue(output.contains("[0] = \"a\""), output);
        assertTrue(output.contains("[1] = \"b\""), output);
    }

    // ---- Circular references ----

    @Test
    void printCircularReference() {
        SerializedSerializableClass sc = SerializedSerializableClass.builder()
                .name("com.example.Node")
                .classLoader(SerializedBuiltInClassLoader.forAppClassLoader())
                .uid(1L)
                .addField("next", ClassDesc.of("com.example.Node"))
                .build();

        // create a node whose "next" field is null (can't make a true cycle with immutable constructors)
        SerializedSerializable node = new SerializedSerializable(sc, List.of(
                new SerialData(sc,
                        StreamData.OfBytes.EMPTY,
                        StreamData.of(new Serialized[] { SerializedNull.INSTANCE }),
                        List.of())));

        // We can't easily make a true cycle with immutable constructors,
        // so instead test that the same class descriptor gets a back-reference
        String output = Printer.print(node);
        // the class descriptor appears first under "class:" and gets label #2
        // verify that the format contains labels
        assertTrue(output.contains("#1 serializable com.example.Node"), output);
        assertTrue(output.contains("#2 serializable class com.example.Node"), output);
    }

    @Test
    void printSharedClassDescriptor() {
        SerializedSerializableClass sc = SerializedSerializableClass.builder()
                .name("com.example.Item")
                .classLoader(SerializedBuiltInClassLoader.forAppClassLoader())
                .uid(1L)
                .addField("value", int.class)
                .build();

        byte[] primFour = { 0, 0, 0, 1 };
        byte[] primSeven = { 0, 0, 0, 7 };

        SerializedSerializable obj1 = new SerializedSerializable(sc, List.of(
                new SerialData(sc, StreamData.of(primFour), StreamData.OfObjects.EMPTY, List.of())));
        SerializedSerializable obj2 = new SerializedSerializable(sc, List.of(
                new SerialData(sc, StreamData.of(primSeven), StreamData.OfObjects.EMPTY, List.of())));

        // when printed together, the second object should back-reference the shared class descriptor
        Printer printer = Printer.builder().build();
        String output = printer.printAll(obj1, obj2);
        assertTrue(output.contains("<ref #"), "expected back-reference for shared class descriptor: " + output);
    }

    // ---- Builder configuration ----

    @Test
    void builderCustomIndent() {
        SerializedSerializableClass sc = SerializedSerializableClass.builder()
                .name("com.example.Foo")
                .classLoader(SerializedBuiltInClassLoader.forAppClassLoader())
                .uid(0L)
                .build();

        Printer printer = Printer.builder().indent(2).build();
        String output = printer.printOne(sc);
        // 2-space indent means lines inside the block start with exactly 2 spaces
        assertTrue(output.contains("\n  serialVersionUID:"), output);
    }

    @Test
    void builderHideClassLoader() {
        SerializedSerializableClass sc = SerializedSerializableClass.builder()
                .name("com.example.Foo")
                .classLoader(SerializedBuiltInClassLoader.forAppClassLoader())
                .uid(0L)
                .build();

        Printer printer = Printer.builder().showClassLoader(false).build();
        String output = printer.printOne(sc);
        assertFalse(output.contains("classLoader:"), output);
    }

    @Test
    void builderMaxDepth() {
        SerializedSerializableClass sc = SerializedSerializableClass.builder()
                .name("com.example.Foo")
                .classLoader(SerializedBuiltInClassLoader.forAppClassLoader())
                .uid(0L)
                .addField("value", int.class)
                .build();

        Printer printer = Printer.builder().maxDepth(1).build();
        String output = printer.printOne(sc);
        // at depth 1, the class descriptor should show its body, but nested class loader should truncate
        assertTrue(output.contains("serialVersionUID:"), output);
    }

    // ---- Type name utility ----

    @Test
    void typeNamePrimitive() {
        assertEquals("int", Printer.typeName(ConstantDescs.CD_int));
        assertEquals("boolean", Printer.typeName(ConstantDescs.CD_boolean));
        assertEquals("void", Printer.typeName(ConstantDescs.CD_void));
    }

    @Test
    void typeNameReference() {
        assertEquals("java.lang.String", Printer.typeName(ConstantDescs.CD_String));
    }

    @Test
    void typeNameArray() {
        assertEquals("int[]", Printer.typeName(ClassDesc.ofDescriptor("[I")));
        assertEquals("java.lang.String[][]", Printer.typeName(ClassDesc.ofDescriptor("[[Ljava/lang/String;")));
    }

    // ---- Integration: full round-trip ----

    @Test
    void printSerializedArrayList() throws IOException {
        java.util.ArrayList<String> list = new java.util.ArrayList<>();
        list.add("alpha");
        list.add("beta");
        Serialized serialized = ctx.serialize(list);
        String output = Printer.print(serialized);
        // should contain the class name, field data, and the string values
        assertTrue(output.contains("java.util.ArrayList"), output);
        assertTrue(output.contains("\"alpha\""), output);
        assertTrue(output.contains("\"beta\""), output);
    }
}
