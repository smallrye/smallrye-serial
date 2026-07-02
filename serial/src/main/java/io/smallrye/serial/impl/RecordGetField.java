package io.smallrye.serial.impl;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;

import io.smallrye.serial.Deserializer;
import io.smallrye.serial.SerialData;
import io.smallrye.serial.SerialField;
import io.smallrye.serial.SerializedFieldedClass;
import io.smallrye.serial.StreamData;

/**
 * A {@link ObjectInputStream.GetField} implementation that reads field values from
 * a {@link SerialData} instance, bridging the serialization library's internal representation
 * to the JDK's {@code GetField} API.
 * <p>
 * This class serves as the sole bridge between generated record factory hidden classes and
 * the serialization library. Because {@code GetField} is in {@code java.base}, the generated
 * code avoids any class loader linking dependency on library types.
 */
public final class RecordGetField extends ObjectInputStream.GetField {
    private final SerializedFieldedClass fieldedClass;
    private final StreamData.OfBytes primData;
    private final StreamData.OfObjects objectData;
    private final Deserializer deserializer;

    /**
     * Construct a new instance.
     *
     * @param data the serial data containing field values (must not be {@code null})
     * @param deserializer the deserializer for object-typed fields (must not be {@code null})
     */
    public RecordGetField(final SerialData data, final Deserializer deserializer) {
        this.fieldedClass = data.serializedClass();
        this.primData = data.primitiveFieldData();
        this.objectData = data.objectFieldData();
        this.deserializer = deserializer;
    }

    /**
     * {@inheritDoc}
     */
    public ObjectStreamClass getObjectStreamClass() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public boolean defaulted(final String name) {
        return fieldedClass.streamField(name) == null;
    }

    /**
     * {@inheritDoc}
     */
    public boolean get(final String name, final boolean val) {
        SerialField field = fieldedClass.streamField(name);
        return field != null ? primData.getBoolean(primOffset(name, field)) : val;
    }

    /**
     * {@inheritDoc}
     */
    public byte get(final String name, final byte val) {
        SerialField field = fieldedClass.streamField(name);
        return field != null ? primData.getByte(primOffset(name, field)) : val;
    }

    /**
     * {@inheritDoc}
     */
    public char get(final String name, final char val) {
        SerialField field = fieldedClass.streamField(name);
        return field != null ? primData.getChar(primOffset(name, field)) : val;
    }

    /**
     * {@inheritDoc}
     */
    public short get(final String name, final short val) {
        SerialField field = fieldedClass.streamField(name);
        return field != null ? primData.getShort(primOffset(name, field)) : val;
    }

    /**
     * {@inheritDoc}
     */
    public int get(final String name, final int val) {
        SerialField field = fieldedClass.streamField(name);
        return field != null ? primData.getInt(primOffset(name, field)) : val;
    }

    /**
     * {@inheritDoc}
     */
    public long get(final String name, final long val) {
        SerialField field = fieldedClass.streamField(name);
        return field != null ? primData.getLong(primOffset(name, field)) : val;
    }

    /**
     * {@inheritDoc}
     */
    public float get(final String name, final float val) {
        SerialField field = fieldedClass.streamField(name);
        return field != null ? primData.getFloat(primOffset(name, field)) : val;
    }

    /**
     * {@inheritDoc}
     */
    public double get(final String name, final double val) {
        SerialField field = fieldedClass.streamField(name);
        return field != null ? primData.getDouble(primOffset(name, field)) : val;
    }

    /**
     * {@inheritDoc}
     */
    public Object get(final String name, final Object val) throws IOException {
        SerialField field = fieldedClass.streamField(name);
        try {
            return field != null ? deserializer.deserialize(objectData.getObject(objOffset(name, field))) : val;
        } catch (ClassNotFoundException e) {
            throw Util.sneak(e);
        }
    }

    private static int primOffset(String name, SerialField field) {
        if (field.isPrimitive()) {
            return field.offset();
        }
        throw new IllegalArgumentException("Field " + name + " has an unexpected kind");
    }

    private static int objOffset(String name, SerialField field) {
        if (!field.isPrimitive()) {
            return field.offset();
        }
        throw new IllegalArgumentException("Field " + name + " has an unexpected kind");
    }
}
