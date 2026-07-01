package io.smallrye.serial.impl;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;

/**
 * An open-addressed hash table mapping keys of type {@code K} to primitive {@code int} values,
 * avoiding the autoboxing overhead of {@code Map<K, Integer>}.
 * <p>
 * The hash and equality strategy is determined by the {@link ToIntFunction} and {@link BiPredicate}
 * supplied at construction time. Static factory methods provide the two common strategies:
 * <ul>
 * <li>{@link #identity()} — identity semantics ({@link System#identityHashCode}, {@code ==})</li>
 * <li>{@link #equality()} — value semantics ({@link Object#hashCode}, {@link Object#equals})</li>
 * </ul>
 * <p>
 * This class uses linear probing with power-of-two capacity and Fibonacci hash spreading.
 * It supports only insertion and lookup; there is no removal or iteration support.
 *
 * @param <K> the key type
 */
public final class IntMap<K> {

    // Fibonacci hash constant: golden ratio * 2^32, truncated
    private static final int PHI = 0x9E3779B9;

    private final ToIntFunction<K> hashFunction;
    private final BiPredicate<K, K> equalsFunction;
    private Object[] keys;
    private int[] values;
    private int size;
    private int threshold;
    private int shift;

    /**
     * Construct a new {@code IntMap} with the given hash and equality functions and initial capacity.
     *
     * @param hashFunction the function used to compute hash codes for keys (not {@code null})
     * @param equalsFunction the predicate used to test key equality (not {@code null})
     * @param initialCapacity the minimum initial capacity (will be rounded up to a power of two)
     */
    public IntMap(final ToIntFunction<K> hashFunction, final BiPredicate<K, K> equalsFunction, final int initialCapacity) {
        this.hashFunction = Objects.requireNonNull(hashFunction, "hashFunction");
        this.equalsFunction = Objects.requireNonNull(equalsFunction, "equalsFunction");
        int cap = Integer.highestOneBit(Math.max(initialCapacity - 1, 15)) << 1;
        this.keys = new Object[cap];
        this.values = new int[cap];
        this.shift = Integer.numberOfLeadingZeros(cap) + 1;
        this.threshold = cap - (cap >>> 2); // 75% load factor
    }

    /**
     * Construct a new {@code IntMap} with the given hash and equality functions and a default
     * initial capacity of 16.
     *
     * @param hashFunction the function used to compute hash codes for keys (not {@code null})
     * @param equalsFunction the predicate used to test key equality (not {@code null})
     */
    public IntMap(final ToIntFunction<K> hashFunction, final BiPredicate<K, K> equalsFunction) {
        this(hashFunction, equalsFunction, 16);
    }

    // ---- Factory methods ----

    /**
     * Create a new {@code IntMap} using identity semantics: {@link System#identityHashCode}
     * for hashing and reference equality ({@code ==}) for comparison.
     *
     * @param <K> the key type
     * @return a new identity-based {@code IntMap} (not {@code null})
     */
    public static <K> IntMap<K> identity() {
        return new IntMap<>(System::identityHashCode, (a, b) -> a == b);
    }

    /**
     * Create a new {@code IntMap} using identity semantics with the specified initial capacity.
     *
     * @param <K> the key type
     * @param initialCapacity the minimum initial capacity
     * @return a new identity-based {@code IntMap} (not {@code null})
     */
    public static <K> IntMap<K> identity(final int initialCapacity) {
        return new IntMap<>(System::identityHashCode, (a, b) -> a == b, initialCapacity);
    }

    /**
     * Create a new {@code IntMap} using value-equality semantics: {@link Object#hashCode}
     * for hashing and {@link Object#equals} for comparison.
     *
     * @param <K> the key type
     * @return a new equality-based {@code IntMap} (not {@code null})
     */
    public static <K> IntMap<K> equality() {
        return new IntMap<>(Object::hashCode, Object::equals);
    }

    /**
     * Create a new {@code IntMap} using value-equality semantics with the specified initial capacity.
     *
     * @param <K> the key type
     * @param initialCapacity the minimum initial capacity
     * @return a new equality-based {@code IntMap} (not {@code null})
     */
    public static <K> IntMap<K> equality(final int initialCapacity) {
        return new IntMap<>(Object::hashCode, Object::equals, initialCapacity);
    }

    // ---- Public API ----

    /**
     * Return the value associated with the given key.
     *
     * @param key the key to look up (must not be {@code null})
     * @return the value associated with the key
     * @throws NoSuchElementException if the key is not present in the map
     */
    @SuppressWarnings("unchecked")
    public int get(final K key) {
        final Object[] k = this.keys;
        final int mask = k.length - 1;
        int idx = spread(hashFunction.applyAsInt(key));
        for (;;) {
            Object existing = k[idx & mask];
            if (existing == null) {
                throw new NoSuchElementException();
            }
            if (equalsFunction.test((K) existing, key)) {
                return values[idx & mask];
            }
            idx++;
        }
    }

    /**
     * Return {@code true} if the map contains a mapping for the given key.
     *
     * @param key the key to test (must not be {@code null})
     * @return {@code true} if the key is present, {@code false} otherwise
     */
    @SuppressWarnings("unchecked")
    public boolean containsKey(final K key) {
        final Object[] k = this.keys;
        final int mask = k.length - 1;
        int idx = spread(hashFunction.applyAsInt(key));
        for (;;) {
            Object existing = k[idx & mask];
            if (existing == null) {
                return false;
            }
            if (equalsFunction.test((K) existing, key)) {
                return true;
            }
            idx++;
        }
    }

    /**
     * Associate the given key with the given value. If the key is already present, the
     * previous value is replaced and returned.
     *
     * @param key the key (must not be {@code null})
     * @param value the value to associate
     * @return the previous value associated with the key, or {@code -1} if the key was not present
     */
    @SuppressWarnings("unchecked")
    public int put(final K key, final int value) {
        Object[] k = this.keys;
        int[] v = this.values;
        int mask = k.length - 1;
        int idx = spread(hashFunction.applyAsInt(key));
        for (;;) {
            Object existing = k[idx & mask];
            if (existing == null) {
                k[idx & mask] = key;
                v[idx & mask] = value;
                if (++size >= threshold) {
                    resize();
                }
                return -1;
            }
            if (equalsFunction.test((K) existing, key)) {
                int old = v[idx & mask];
                v[idx & mask] = value;
                return old;
            }
            idx++;
        }
    }

    /**
     * {@return the number of key-value mappings in this map}
     */
    public int size() {
        return size;
    }

    // ---- Internal ----

    /**
     * Spread a raw hash code using Fibonacci hashing for a more uniform distribution
     * across power-of-two table sizes.
     *
     * @param hash the raw hash code
     * @return the spread index (pre-mask)
     */
    private int spread(final int hash) {
        return (hash * PHI) >>> shift;
    }

    /**
     * Double the table capacity and rehash all existing entries.
     */
    @SuppressWarnings("unchecked")
    private void resize() {
        final Object[] oldKeys = this.keys;
        final int[] oldValues = this.values;
        int newCap = oldKeys.length << 1;
        Object[] newKeys = new Object[newCap];
        int[] newValues = new int[newCap];
        int newMask = newCap - 1;
        this.shift = Integer.numberOfLeadingZeros(newCap) + 1;
        for (int i = 0; i < oldKeys.length; i++) {
            Object k = oldKeys[i];
            if (k != null) {
                int idx = spread(hashFunction.applyAsInt((K) k));
                while (newKeys[idx & newMask] != null) {
                    idx++;
                }
                newKeys[idx & newMask] = k;
                newValues[idx & newMask] = oldValues[i];
            }
        }
        this.keys = newKeys;
        this.values = newValues;
        this.threshold = newCap - (newCap >>> 2);
    }
}
