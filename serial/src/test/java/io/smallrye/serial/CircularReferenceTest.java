package io.smallrye.serial;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.Serializable;

import org.junit.jupiter.api.Test;

/**
 * Tests for correct handling of circular references during serialization and deserialization.
 */
class CircularReferenceTest {

    private final SerialContext ctx = SerialContext.builder().addDefaultProviders().build();
    private final Serializer ser = ctx.createSerializer();
    private final Deserializer des = ctx.createDeserializer();

    /**
     * A serializable node that can reference another node, enabling circular graphs.
     */
    public static class Node implements Serializable {
        private static final long serialVersionUID = 1L;
        String label;
        Node next;

        /** No-arg constructor. */
        public Node() {
        }

        Node(String label) {
            this.label = label;
        }
    }

    @Test
    void selfReferencingObject() throws IOException, ClassNotFoundException {
        Node original = new Node("self");
        original.next = original;

        Serialized serialized = ser.serialize(original);
        Node result = (Node) des.deserialize(serialized);

        assertEquals("self", result.label);
        assertSame(result, result.next, "self-reference should preserve identity");
    }

    @Test
    void mutualReference() throws IOException, ClassNotFoundException {
        Node a = new Node("A");
        Node b = new Node("B");
        a.next = b;
        b.next = a;

        Serialized serializedA = ser.serialize(a);
        Node resultA = (Node) des.deserialize(serializedA);

        assertEquals("A", resultA.label);
        assertNotNull(resultA.next);
        assertEquals("B", resultA.next.label);
        assertSame(resultA, resultA.next.next, "mutual reference should preserve identity");
    }
}
