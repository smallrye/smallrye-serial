package io.smallrye.serial.stream;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import io.smallrye.serial.SerialContext;
import io.smallrye.serial.Serialized;
import io.smallrye.serial.SerializedBooleanArray;
import io.smallrye.serial.SerializedBuiltInClassLoader;
import io.smallrye.serial.SerializedByteArray;
import io.smallrye.serial.SerializedCharArray;
import io.smallrye.serial.SerializedDoubleArray;
import io.smallrye.serial.SerializedEnum;
import io.smallrye.serial.SerializedExternalizable;
import io.smallrye.serial.SerializedFloatArray;
import io.smallrye.serial.SerializedIntArray;
import io.smallrye.serial.SerializedLongArray;
import io.smallrye.serial.SerializedNull;
import io.smallrye.serial.SerializedObjectArray;
import io.smallrye.serial.SerializedSerializable;
import io.smallrye.serial.SerializedShortArray;
import io.smallrye.serial.SerializedString;
import io.smallrye.serial.spi.ObjectDeserializer;

/**
 * Tests for {@link SerialStreamWriter} and {@link SerialStreamReader}.
 * <p>
 * The tests cover three main validation strategies:
 * <ol>
 * <li><b>Writer correctness</b>: serialize → write → standard OIS → verify</li>
 * <li><b>Reader correctness</b>: standard OOS → read → inspect graph</li>
 * <li><b>Round-trip fidelity</b>: OOS → bytes → read → write → bytes → compare</li>
 * </ol>
 */
class SerialStreamRoundTripTest {

    private final SerialContext ctx = SerialContext.builder()
            .addDefaultProviders()
            // resolve built-in class loaders to the test class loader
            .addDeserializer(new ObjectDeserializer() {
                public Object deserialize(Context ctxt, Serialized serialized) throws IOException, ClassNotFoundException {
                    if (serialized instanceof SerializedBuiltInClassLoader) {
                        return SerialStreamRoundTripTest.class.getClassLoader();
                    }
                    return ctxt.next();
                }

                public int priority() {
                    return PRIORITY_CLASS_LOADER + 1;
                }
            })
            .build();

    // ---- Test fixtures ----

    /**
     * Simple serializable with two int fields.
     */
    public static class SimplePoint implements Serializable {
        private static final long serialVersionUID = 1L;
        int x;
        int y;

        /** No-arg constructor for deserialization. */
        public SimplePoint() {
        }

        SimplePoint(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    /**
     * Subclass adding a String field for testing inheritance hierarchies.
     */
    public static class NamedPoint extends SimplePoint {
        private static final long serialVersionUID = 1L;
        String name;

        /** No-arg constructor for deserialization. */
        public NamedPoint() {
        }

        NamedPoint(int x, int y, String name) {
            super(x, y);
            this.name = name;
        }
    }

    /**
     * Class with custom writeObject/readObject that writes extra stream data.
     */
    public static class CustomWriteObject implements Serializable {
        private static final long serialVersionUID = 1L;
        int value;
        transient String extra;

        /** No-arg constructor. */
        public CustomWriteObject() {
        }

        CustomWriteObject(int value, String extra) {
            this.value = value;
            this.extra = extra;
        }

        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
            out.writeUTF(extra);
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            extra = in.readUTF();
        }
    }

    /**
     * Externalizable test fixture.
     */
    public static class ExtPoint implements Externalizable {
        private static final long serialVersionUID = 1L;
        int x;
        int y;

        /** Required no-arg constructor. */
        public ExtPoint() {
        }

        ExtPoint(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeInt(x);
            out.writeInt(y);
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException {
            x = in.readInt();
            y = in.readInt();
        }
    }

    /**
     * Serializable invocation handler for proxy tests.
     */
    public static class SerializableHandler implements InvocationHandler, Serializable {
        private static final long serialVersionUID = 1L;
        String tag;

        /** No-arg constructor. */
        public SerializableHandler() {
        }

        SerializableHandler(String tag) {
            this.tag = tag;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return null;
        }
    }

    // ---- Helper methods ----

    /**
     * Serialize an object to bytes using standard JDK ObjectOutputStream.
     */
    private byte[] jdkSerialize(Object obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(obj);
        }
        return baos.toByteArray();
    }

    /**
     * Deserialize an object from bytes using standard JDK ObjectInputStream.
     */
    private Object jdkDeserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return ois.readObject();
        }
    }

    /**
     * Write a Serialized graph to bytes using our SerialStreamWriter.
     */
    private byte[] writeToBytes(Serialized serialized) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (SerialStreamWriter writer = SerialStreamWriter.builder(baos).build()) {
            writer.writeSerialized(serialized);
        }
        return baos.toByteArray();
    }

    /**
     * Read a Serialized graph from bytes using our SerialStreamReader.
     */
    private Serialized readFromBytes(byte[] bytes) throws IOException {
        try (SerialStreamReader reader = SerialStreamReader.builder(new ByteArrayInputStream(bytes)).build()) {
            return reader.readSerialized();
        }
    }

    // ======== Writer correctness: serialize → write → JDK OIS → verify ========

    @Test
    void writerSimpleSerializable() throws Exception {
        SimplePoint original = new SimplePoint(42, -7);
        Serialized graph = ctx.serialize(original);
        byte[] bytes = writeToBytes(graph);
        SimplePoint result = (SimplePoint) jdkDeserialize(bytes);
        assertEquals(42, result.x);
        assertEquals(-7, result.y);
    }

    @Test
    void writerInheritanceHierarchy() throws Exception {
        NamedPoint original = new NamedPoint(1, 2, "test");
        Serialized graph = ctx.serialize(original);
        byte[] bytes = writeToBytes(graph);
        NamedPoint result = (NamedPoint) jdkDeserialize(bytes);
        assertEquals(1, result.x);
        assertEquals(2, result.y);
        assertEquals("test", result.name);
    }

    @Test
    void writerCustomWriteObject() throws Exception {
        CustomWriteObject original = new CustomWriteObject(99, "hello");
        Serialized graph = ctx.serialize(original);
        byte[] bytes = writeToBytes(graph);
        CustomWriteObject result = (CustomWriteObject) jdkDeserialize(bytes);
        assertEquals(99, result.value);
        assertEquals("hello", result.extra);
    }

    @Test
    void writerEnum() throws Exception {
        Serialized graph = ctx.serialize(Thread.State.RUNNABLE);
        byte[] bytes = writeToBytes(graph);
        Object result = jdkDeserialize(bytes);
        assertSame(Thread.State.RUNNABLE, result);
    }

    @Test
    void writerExternalizable() throws Exception {
        ExtPoint original = new ExtPoint(10, 20);
        Serialized graph = ctx.serialize(original);
        byte[] bytes = writeToBytes(graph);
        ExtPoint result = (ExtPoint) jdkDeserialize(bytes);
        assertEquals(10, result.x);
        assertEquals(20, result.y);
    }

    @Test
    void writerNull() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (SerialStreamWriter writer = SerialStreamWriter.builder(baos).build()) {
            writer.writeSerialized(SerializedNull.INSTANCE);
        }
        Object result = jdkDeserialize(baos.toByteArray());
        assertNull(result);
    }

    @Test
    void writerString() throws Exception {
        Serialized graph = ctx.serialize("hello world");
        byte[] bytes = writeToBytes(graph);
        Object result = jdkDeserialize(bytes);
        assertEquals("hello world", result);
    }

    @Test
    void writerIntArray() throws Exception {
        int[] original = { 1, 2, 3, Integer.MAX_VALUE, Integer.MIN_VALUE };
        Serialized graph = ctx.serialize(original);
        byte[] bytes = writeToBytes(graph);
        int[] result = (int[]) jdkDeserialize(bytes);
        assertArrayEquals(original, result);
    }

    @Test
    void writerByteArray() throws Exception {
        byte[] original = { 0, 1, -1, Byte.MAX_VALUE, Byte.MIN_VALUE };
        Serialized graph = ctx.serialize(original);
        byte[] bytes = writeToBytes(graph);
        byte[] result = (byte[]) jdkDeserialize(bytes);
        assertArrayEquals(original, result);
    }

    @Test
    void writerBooleanArray() throws Exception {
        boolean[] original = { true, false, true, true };
        Serialized graph = ctx.serialize(original);
        byte[] bytes = writeToBytes(graph);
        boolean[] result = (boolean[]) jdkDeserialize(bytes);
        assertArrayEquals(original, result);
    }

    @Test
    void writerCharArray() throws Exception {
        char[] original = { 'a', 'Z', ' ', '￿' };
        Serialized graph = ctx.serialize(original);
        byte[] bytes = writeToBytes(graph);
        char[] result = (char[]) jdkDeserialize(bytes);
        assertArrayEquals(original, result);
    }

    @Test
    void writerShortArray() throws Exception {
        short[] original = { Short.MIN_VALUE, 0, Short.MAX_VALUE };
        Serialized graph = ctx.serialize(original);
        byte[] bytes = writeToBytes(graph);
        short[] result = (short[]) jdkDeserialize(bytes);
        assertArrayEquals(original, result);
    }

    @Test
    void writerLongArray() throws Exception {
        long[] original = { Long.MIN_VALUE, 0L, Long.MAX_VALUE };
        Serialized graph = ctx.serialize(original);
        byte[] bytes = writeToBytes(graph);
        long[] result = (long[]) jdkDeserialize(bytes);
        assertArrayEquals(original, result);
    }

    @Test
    void writerFloatArray() throws Exception {
        float[] original = { 0.0f, -0.0f, Float.NaN, Float.POSITIVE_INFINITY, 3.14f };
        Serialized graph = ctx.serialize(original);
        byte[] bytes = writeToBytes(graph);
        float[] result = (float[]) jdkDeserialize(bytes);
        assertArrayEquals(original, result);
    }

    @Test
    void writerDoubleArray() throws Exception {
        double[] original = { 0.0, -0.0, Double.NaN, Double.NEGATIVE_INFINITY, Math.PI };
        Serialized graph = ctx.serialize(original);
        byte[] bytes = writeToBytes(graph);
        double[] result = (double[]) jdkDeserialize(bytes);
        assertArrayEquals(original, result);
    }

    @Test
    void writerObjectArray() throws Exception {
        String[] original = { "one", "two", null, "four" };
        Serialized graph = ctx.serialize(original);
        byte[] bytes = writeToBytes(graph);
        String[] result = (String[]) jdkDeserialize(bytes);
        assertArrayEquals(original, result);
    }

    @Test
    void writerEmptyString() throws Exception {
        Serialized graph = ctx.serialize("");
        byte[] bytes = writeToBytes(graph);
        assertEquals("", jdkDeserialize(bytes));
    }

    // ======== Reader correctness: JDK OOS → read → inspect graph ========

    @Test
    void readerSimpleSerializable() throws Exception {
        byte[] bytes = jdkSerialize(new SimplePoint(42, -7));
        Serialized graph = readFromBytes(bytes);
        assertInstanceOf(SerializedSerializable.class, graph);
        SerializedSerializable ss = (SerializedSerializable) graph;
        assertEquals("io.smallrye.serial.stream.SerialStreamRoundTripTest$SimplePoint",
                ss.serializedClass().name());
    }

    @Test
    void readerEnum() throws Exception {
        byte[] bytes = jdkSerialize(Thread.State.BLOCKED);
        Serialized graph = readFromBytes(bytes);
        assertInstanceOf(SerializedEnum.class, graph);
        SerializedEnum se = (SerializedEnum) graph;
        assertEquals("java.lang.Thread$State", se.enumClass().name());
        assertInstanceOf(SerializedString.class, se.constantName());
        assertEquals("BLOCKED", ((SerializedString) se.constantName()).string());
    }

    @Test
    void readerExternalizable() throws Exception {
        byte[] bytes = jdkSerialize(new ExtPoint(5, 6));
        Serialized graph = readFromBytes(bytes);
        assertInstanceOf(SerializedExternalizable.class, graph);
    }

    @Test
    void readerString() throws Exception {
        byte[] bytes = jdkSerialize("test string");
        Serialized graph = readFromBytes(bytes);
        assertInstanceOf(SerializedString.class, graph);
        assertEquals("test string", ((SerializedString) graph).string());
    }

    @Test
    void readerNull() throws Exception {
        byte[] bytes = jdkSerialize(null);
        Serialized graph = readFromBytes(bytes);
        assertSame(SerializedNull.INSTANCE, graph);
    }

    @Test
    void readerIntArray() throws Exception {
        int[] original = { 10, 20, 30 };
        byte[] bytes = jdkSerialize(original);
        Serialized graph = readFromBytes(bytes);
        assertInstanceOf(SerializedIntArray.class, graph);
        assertArrayEquals(original, ((SerializedIntArray) graph).asArray());
    }

    @Test
    void readerByteArray() throws Exception {
        byte[] original = { 1, 2, 3 };
        byte[] bytes = jdkSerialize(original);
        Serialized graph = readFromBytes(bytes);
        assertInstanceOf(SerializedByteArray.class, graph);
        assertArrayEquals(original, ((SerializedByteArray) graph).asArray());
    }

    @Test
    void readerBooleanArray() throws Exception {
        boolean[] original = { true, false };
        byte[] bytes = jdkSerialize(original);
        Serialized graph = readFromBytes(bytes);
        assertInstanceOf(SerializedBooleanArray.class, graph);
        assertArrayEquals(original, ((SerializedBooleanArray) graph).asArray());
    }

    @Test
    void readerCharArray() throws Exception {
        char[] original = { 'x', 'y' };
        byte[] bytes = jdkSerialize(original);
        Serialized graph = readFromBytes(bytes);
        assertInstanceOf(SerializedCharArray.class, graph);
        assertArrayEquals(original, ((SerializedCharArray) graph).asArray());
    }

    @Test
    void readerShortArray() throws Exception {
        short[] original = { 100, 200 };
        byte[] bytes = jdkSerialize(original);
        Serialized graph = readFromBytes(bytes);
        assertInstanceOf(SerializedShortArray.class, graph);
        assertArrayEquals(original, ((SerializedShortArray) graph).asArray());
    }

    @Test
    void readerLongArray() throws Exception {
        long[] original = { 999L, -999L };
        byte[] bytes = jdkSerialize(original);
        Serialized graph = readFromBytes(bytes);
        assertInstanceOf(SerializedLongArray.class, graph);
        assertArrayEquals(original, ((SerializedLongArray) graph).asArray());
    }

    @Test
    void readerFloatArray() throws Exception {
        float[] original = { 1.5f, 2.5f };
        byte[] bytes = jdkSerialize(original);
        Serialized graph = readFromBytes(bytes);
        assertInstanceOf(SerializedFloatArray.class, graph);
        assertArrayEquals(original, ((SerializedFloatArray) graph).asArray());
    }

    @Test
    void readerDoubleArray() throws Exception {
        double[] original = { 1.5, 2.5 };
        byte[] bytes = jdkSerialize(original);
        Serialized graph = readFromBytes(bytes);
        assertInstanceOf(SerializedDoubleArray.class, graph);
        assertArrayEquals(original, ((SerializedDoubleArray) graph).asArray());
    }

    @Test
    void readerObjectArray() throws Exception {
        String[] original = { "a", "b", null };
        byte[] bytes = jdkSerialize(original);
        Serialized graph = readFromBytes(bytes);
        assertInstanceOf(SerializedObjectArray.class, graph);
        SerializedObjectArray oa = (SerializedObjectArray) graph;
        assertEquals(3, oa.length());
        assertEquals("a", ((SerializedString) oa.get(0)).string());
        assertEquals("b", ((SerializedString) oa.get(1)).string());
        assertSame(SerializedNull.INSTANCE, oa.get(2));
    }

    @Test
    void readerEmptyArray() throws Exception {
        int[] original = {};
        byte[] bytes = jdkSerialize(original);
        Serialized graph = readFromBytes(bytes);
        assertInstanceOf(SerializedIntArray.class, graph);
        assertEquals(0, ((SerializedIntArray) graph).asArray().length);
    }

    // ======== Round-trip fidelity: OOS → read → write → OIS → verify ========

    @Test
    void roundTripSimpleSerializable() throws Exception {
        SimplePoint original = new SimplePoint(100, 200);
        byte[] jdkBytes = jdkSerialize(original);
        Serialized graph = readFromBytes(jdkBytes);
        byte[] ourBytes = writeToBytes(graph);
        SimplePoint result = (SimplePoint) jdkDeserialize(ourBytes);
        assertEquals(100, result.x);
        assertEquals(200, result.y);
    }

    @Test
    void roundTripInheritance() throws Exception {
        NamedPoint original = new NamedPoint(3, 4, "point");
        byte[] jdkBytes = jdkSerialize(original);
        Serialized graph = readFromBytes(jdkBytes);
        byte[] ourBytes = writeToBytes(graph);
        NamedPoint result = (NamedPoint) jdkDeserialize(ourBytes);
        assertEquals(3, result.x);
        assertEquals(4, result.y);
        assertEquals("point", result.name);
    }

    @Test
    void roundTripCustomWriteObject() throws Exception {
        CustomWriteObject original = new CustomWriteObject(77, "world");
        byte[] jdkBytes = jdkSerialize(original);
        Serialized graph = readFromBytes(jdkBytes);
        byte[] ourBytes = writeToBytes(graph);
        CustomWriteObject result = (CustomWriteObject) jdkDeserialize(ourBytes);
        assertEquals(77, result.value);
        assertEquals("world", result.extra);
    }

    @Test
    void roundTripEnum() throws Exception {
        byte[] jdkBytes = jdkSerialize(Thread.State.WAITING);
        Serialized graph = readFromBytes(jdkBytes);
        byte[] ourBytes = writeToBytes(graph);
        assertSame(Thread.State.WAITING, jdkDeserialize(ourBytes));
    }

    @Test
    void roundTripExternalizable() throws Exception {
        ExtPoint original = new ExtPoint(7, 8);
        byte[] jdkBytes = jdkSerialize(original);
        Serialized graph = readFromBytes(jdkBytes);
        byte[] ourBytes = writeToBytes(graph);
        ExtPoint result = (ExtPoint) jdkDeserialize(ourBytes);
        assertEquals(7, result.x);
        assertEquals(8, result.y);
    }

    @Test
    void roundTripString() throws Exception {
        byte[] jdkBytes = jdkSerialize("round trip");
        Serialized graph = readFromBytes(jdkBytes);
        byte[] ourBytes = writeToBytes(graph);
        assertEquals("round trip", jdkDeserialize(ourBytes));
    }

    @Test
    void roundTripAllPrimitiveArrays() throws Exception {
        Object[] arrays = {
                new boolean[] { true, false },
                new byte[] { 1, 2, 3 },
                new char[] { 'a', 'b' },
                new short[] { 10, 20 },
                new int[] { 100, 200 },
                new long[] { 1000L, 2000L },
                new float[] { 1.5f, 2.5f },
                new double[] { 1.5, 2.5 }
        };
        for (Object original : arrays) {
            byte[] jdkBytes = jdkSerialize(original);
            Serialized graph = readFromBytes(jdkBytes);
            byte[] ourBytes = writeToBytes(graph);
            Object result = jdkDeserialize(ourBytes);
            if (original instanceof boolean[] a) {
                assertArrayEquals(a, (boolean[]) result);
            } else if (original instanceof byte[] a) {
                assertArrayEquals(a, (byte[]) result);
            } else if (original instanceof char[] a) {
                assertArrayEquals(a, (char[]) result);
            } else if (original instanceof short[] a) {
                assertArrayEquals(a, (short[]) result);
            } else if (original instanceof int[] a) {
                assertArrayEquals(a, (int[]) result);
            } else if (original instanceof long[] a) {
                assertArrayEquals(a, (long[]) result);
            } else if (original instanceof float[] a) {
                assertArrayEquals(a, (float[]) result);
            } else if (original instanceof double[] a) {
                assertArrayEquals(a, (double[]) result);
            }
        }
    }

    @Test
    void roundTripObjectArray() throws Exception {
        String[] original = { "hello", null, "world" };
        byte[] jdkBytes = jdkSerialize(original);
        Serialized graph = readFromBytes(jdkBytes);
        byte[] ourBytes = writeToBytes(graph);
        assertArrayEquals(original, (String[]) jdkDeserialize(ourBytes));
    }

    @Test
    void roundTripNull() throws Exception {
        byte[] jdkBytes = jdkSerialize(null);
        Serialized graph = readFromBytes(jdkBytes);
        byte[] ourBytes = writeToBytes(graph);
        assertNull(jdkDeserialize(ourBytes));
    }

    @Test
    void roundTripMultipleObjects() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject("first");
            oos.writeObject(42);
            oos.writeObject(Thread.State.NEW);
        }
        byte[] jdkBytes = baos.toByteArray();

        // read all three
        SerialStreamReader reader = SerialStreamReader.builder(new ByteArrayInputStream(jdkBytes)).build();
        Serialized s1 = reader.readSerialized();
        Serialized s2 = reader.readSerialized();
        Serialized s3 = reader.readSerialized();
        Serialized s4 = reader.readSerialized();
        reader.close();
        assertNotNull(s1);
        assertNotNull(s2);
        assertNotNull(s3);
        assertNull(s4);

        // write all three back
        baos = new ByteArrayOutputStream();
        try (SerialStreamWriter writer = SerialStreamWriter.builder(baos).build()) {
            writer.writeSerialized(s1);
            writer.writeSerialized(s2);
            writer.writeSerialized(s3);
        }
        byte[] ourBytes = baos.toByteArray();

        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(ourBytes))) {
            assertEquals("first", ois.readObject());
            assertEquals(42, ois.readObject());
            assertSame(Thread.State.NEW, ois.readObject());
        }
    }

    // ======== Safety tests ========

    @Test
    void readerBadMagic() {
        byte[] bad = { 0x00, 0x00, 0x00, 0x05 };
        assertThrows(StreamCorruptedException.class,
                () -> SerialStreamReader.builder(new ByteArrayInputStream(bad)).build());
    }

    @Test
    void readerBadVersion() {
        byte[] bad = { (byte) 0xAC, (byte) 0xED, 0x00, 0x06 };
        assertThrows(StreamCorruptedException.class,
                () -> SerialStreamReader.builder(new ByteArrayInputStream(bad)).build());
    }

    @Test
    void readerTruncatedStream() throws Exception {
        // valid header but no content
        byte[] truncated = { (byte) 0xAC, (byte) 0xED, 0x00, 0x05 };
        try (SerialStreamReader reader = SerialStreamReader.builder(new ByteArrayInputStream(truncated)).build()) {
            // should return null at EOF
            assertNull(reader.readSerialized());
        }
    }

    @Test
    void readerInvalidTypeCode() throws Exception {
        byte[] data = { (byte) 0xAC, (byte) 0xED, 0x00, 0x05, 0x7F };
        try (SerialStreamReader reader = SerialStreamReader.builder(new ByteArrayInputStream(data)).build()) {
            assertThrows(StreamCorruptedException.class, reader::readSerialized);
        }
    }

    // ======== Cross-validation: full pipeline ========

    @Test
    void crossValidationWritePath() throws Exception {
        // object → SerialContext.serialize() → SerialStreamWriter → bytes → OIS → verify
        SimplePoint original = new SimplePoint(11, 22);
        Serialized graph = ctx.serialize(original);
        byte[] bytes = writeToBytes(graph);
        SimplePoint result = (SimplePoint) jdkDeserialize(bytes);
        assertEquals(11, result.x);
        assertEquals(22, result.y);
    }

    @Test
    void crossValidationReadPath() throws Exception {
        // OOS → bytes → SerialStreamReader → Serialized → SerialContext.deserialize() → verify
        SimplePoint original = new SimplePoint(33, 44);
        byte[] bytes = jdkSerialize(original);
        Serialized graph = readFromBytes(bytes);
        SimplePoint result = (SimplePoint) ctx.deserialize(graph);
        assertEquals(33, result.x);
        assertEquals(44, result.y);
    }
}
