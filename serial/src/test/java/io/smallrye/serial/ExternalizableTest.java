package io.smallrye.serial;

import static org.junit.jupiter.api.Assertions.*;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.junit.jupiter.api.Test;

/**
 * Tests for round-trip serialization and deserialization of {@link Externalizable} objects.
 */
class ExternalizableTest {

    private final SerialContext ctx = SerialContext.builder().addDefaultProviders().build();
    private final Serializer ser = ctx.createSerializer();
    private final Deserializer des = ctx.createDeserializer();

    /**
     * A simple externalizable point used as a test fixture.
     */
    public static class ExternalizablePoint implements Externalizable {
        private static final long serialVersionUID = 1L;
        int x;
        int y;

        /** Required no-arg constructor for Externalizable. */
        public ExternalizablePoint() {
        }

        ExternalizablePoint(int x, int y) {
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

    @Test
    void externalizableRoundTrip() throws IOException, ClassNotFoundException {
        ExternalizablePoint original = new ExternalizablePoint(10, 20);
        Serialized serialized = ser.serialize(original);
        assertInstanceOf(SerializedExternalizable.class, serialized);
        ExternalizablePoint result = (ExternalizablePoint) des.deserialize(serialized);
        assertEquals(original.x, result.x);
        assertEquals(original.y, result.y);
    }

    @Test
    void checkIntermediateStructure() throws IOException {
        Serialized serialized = ser.serialize(new ExternalizablePoint(3, 4));
        SerializedExternalizable se = (SerializedExternalizable) serialized;
        assertInstanceOf(SerializedExternalizableClass.class, se.serializedClass());
        assertEquals(ExternalizablePoint.class.getName(), se.serializedClass().name());
        assertFalse(se.data().isEmpty());
    }
}
