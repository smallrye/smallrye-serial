package io.smallrye.serial.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link IntMap} hash table.
 */
class IntMapTest {

    // ---- Basic equality-mode operations ----

    @Test
    void emptyMapHasSizeZero() {
        IntMap<String> map = IntMap.equality();
        assertEquals(0, map.size());
    }

    @Test
    void putAndGet() {
        IntMap<String> map = IntMap.equality();
        map.put("a", 1);
        map.put("b", 2);
        assertEquals(1, map.get("a"));
        assertEquals(2, map.get("b"));
        assertEquals(2, map.size());
    }

    @Test
    void containsKeyPresent() {
        IntMap<String> map = IntMap.equality();
        map.put("x", 42);
        assertTrue(map.containsKey("x"));
    }

    @Test
    void containsKeyAbsent() {
        IntMap<String> map = IntMap.equality();
        assertFalse(map.containsKey("missing"));
    }

    @Test
    void getThrowsOnMissingKey() {
        IntMap<String> map = IntMap.equality();
        assertThrows(NoSuchElementException.class, () -> map.get("missing"));
    }

    @Test
    void putReturnsMinusOneForNewKey() {
        IntMap<String> map = IntMap.equality();
        assertEquals(-1, map.put("new", 5));
    }

    @Test
    void putReturnsPreviousValue() {
        IntMap<String> map = IntMap.equality();
        map.put("k", 10);
        assertEquals(10, map.put("k", 20));
        assertEquals(20, map.get("k"));
        assertEquals(1, map.size());
    }

    @Test
    void putReplacesWithZero() {
        IntMap<String> map = IntMap.equality();
        map.put("k", 99);
        assertEquals(99, map.put("k", 0));
        assertEquals(0, map.get("k"));
    }

    @Test
    void putReplacesWithNegative() {
        IntMap<String> map = IntMap.equality();
        map.put("k", 1);
        assertEquals(1, map.put("k", Integer.MIN_VALUE));
        assertEquals(Integer.MIN_VALUE, map.get("k"));
    }

    // ---- Identity mode ----

    @Test
    void identityDistinguishesSameValueDifferentInstances() {
        IntMap<String> map = IntMap.identity();
        String a = new String("hello");
        String b = new String("hello");
        map.put(a, 1);
        map.put(b, 2);
        assertEquals(2, map.size());
        assertEquals(1, map.get(a));
        assertEquals(2, map.get(b));
    }

    @Test
    void identitySameInstanceReturnsSameValue() {
        IntMap<String> map = IntMap.identity();
        String key = "shared";
        map.put(key, 42);
        assertTrue(map.containsKey(key));
        assertEquals(42, map.get(key));
    }

    @Test
    void identityContainsKeyFalseForEqualButDistinctInstance() {
        IntMap<String> map = IntMap.identity();
        String a = new String("x");
        String b = new String("x");
        map.put(a, 1);
        assertTrue(map.containsKey(a));
        assertFalse(map.containsKey(b));
    }

    // ---- Equality mode uses equals/hashCode ----

    @Test
    void equalityTreatsEqualStringsAsSameKey() {
        IntMap<String> map = IntMap.equality();
        String a = new String("hello");
        String b = new String("hello");
        map.put(a, 1);
        map.put(b, 2);
        assertEquals(1, map.size());
        assertEquals(2, map.get(a));
        assertEquals(2, map.get(b));
    }

    // ---- Resize / many entries ----

    @Test
    void survivesResize() {
        IntMap<Integer> map = IntMap.equality(4);
        for (int i = 0; i < 200; i++) {
            map.put(i, i * 3);
        }
        assertEquals(200, map.size());
        for (int i = 0; i < 200; i++) {
            assertTrue(map.containsKey(i), "missing key " + i);
            assertEquals(i * 3, map.get(i), "wrong value for key " + i);
        }
    }

    @Test
    void identitySurvivesResize() {
        Object[] keys = new Object[200];
        IntMap<Object> map = IntMap.identity(4);
        for (int i = 0; i < keys.length; i++) {
            keys[i] = new Object();
            map.put(keys[i], i);
        }
        assertEquals(200, map.size());
        for (int i = 0; i < keys.length; i++) {
            assertEquals(i, map.get(keys[i]));
        }
    }

    // ---- Edge cases ----

    @Test
    void zeroValueIsStoredAndRetrieved() {
        IntMap<String> map = IntMap.equality();
        map.put("zero", 0);
        assertTrue(map.containsKey("zero"));
        assertEquals(0, map.get("zero"));
    }

    @Test
    void negativeValueIsStoredAndRetrieved() {
        IntMap<String> map = IntMap.equality();
        map.put("neg", -1);
        map.put("min", Integer.MIN_VALUE);
        assertEquals(-1, map.get("neg"));
        assertEquals(Integer.MIN_VALUE, map.get("min"));
    }

    @Test
    void maxValueIsStoredAndRetrieved() {
        IntMap<String> map = IntMap.equality();
        map.put("max", Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, map.get("max"));
    }

    @Test
    void initialCapacityOfOneStillWorks() {
        IntMap<String> map = IntMap.equality(1);
        map.put("a", 1);
        map.put("b", 2);
        assertEquals(1, map.get("a"));
        assertEquals(2, map.get("b"));
    }

    // ---- Custom strategy ----

    @Test
    void customHashAndEquals() {
        // case-insensitive string map
        IntMap<String> map = new IntMap<>(
                s -> s.toLowerCase().hashCode(),
                (a, b) -> a.equalsIgnoreCase(b));
        map.put("Hello", 1);
        assertTrue(map.containsKey("hello"));
        assertTrue(map.containsKey("HELLO"));
        assertEquals(1, map.get("hElLo"));
        assertEquals(1, map.put("HELLO", 2));
        assertEquals(2, map.get("hello"));
        assertEquals(1, map.size());
    }
}
