package io.smallrye.serial.impl.providers;

import java.io.Externalizable;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.Serializable;
import java.util.ListIterator;

import io.smallrye.serial.SerialData;
import io.smallrye.serial.Serialized;
import io.smallrye.serial.SerializedSerializable;
import io.smallrye.serial.SerializedSerializableClass;
import io.smallrye.serial.impl.CapturedObjectInputStream;
import io.smallrye.serial.impl.ReadUtil;
import io.smallrye.serial.spi.ObjectDeserializer;

/**
 * Deserializer that handles {@link SerializedSerializable} instances.
 * <p>
 * The deserializer walks the local class hierarchy and the remote class descriptor
 * chain in parallel, consuming data entries from the data list via a {@link ListIterator}.
 * Data entries are consumed in root-to-leaf order, matching the list's natural ordering.
 * Gaps (hierarchy levels with no corresponding data) are handled with defaults or
 * {@code readObjectNoData}. Out-of-order or extra data entries are rejected.
 */
public final class SerializableDeserializer implements ObjectDeserializer {
    /**
     * Construct a new instance.
     */
    public SerializableDeserializer() {
    }

    /**
     * {@inheritDoc}
     */
    public Object deserialize(final Context ctxt, final Serialized serialized) throws IOException, ClassNotFoundException {
        if (serialized instanceof SerializedSerializable ser) {
            SerializedSerializableClass type = ser.serializedClass();
            Class<?> clazz = ctxt.deserializeClass(type);
            if (clazz.isInterface() || clazz.isPrimitive() || clazz.isEnum() || clazz.isArray()) {
                throw new InvalidObjectException("Serialized " + clazz + " must be a regular class");
            }
            if (Serializable.class.isAssignableFrom(clazz)) {
                if (Externalizable.class.isAssignableFrom(clazz)) {
                    throw new InvalidObjectException("Serialized " + clazz + " must not implement Externalizable");
                }
                Object object = ReadUtil.newSerializableInstance(clazz);
                ctxt.preSetObject(object);
                ListIterator<SerialData> dataIter = ser.data().listIterator();
                deserialize(ctxt, dataIter, object, clazz, type, clazz);
                if (dataIter.hasNext()) {
                    SerialData extra = dataIter.next();
                    throw new InvalidObjectException(
                            "Unconsumed class data for " + extra.serializedClass().name());
                }
                return object;
            } else {
                return ctxt.next();
            }
        } else {
            return ctxt.next();
        }
    }

    /**
     * Recursively deserialize the class hierarchy, walking from leaf to root on descent
     * and processing each level from root to leaf on unwind. Data entries are consumed
     * from the iterator in root-to-leaf order as the recursion unwinds.
     *
     * @param ctxt the deserialization context
     * @param dataIter iterator over the data list, consumed root-to-leaf
     * @param object the object being deserialized
     * @param local the current local class in the hierarchy
     * @param remoteSer the current remote class descriptor
     * @param remote the resolved local class for {@code remoteSer}
     */
    private void deserialize(final Context ctxt, final ListIterator<SerialData> dataIter, final Object object,
            final Class<?> local, final SerializedSerializableClass remoteSer, final Class<?> remote)
            throws IOException, ClassNotFoundException {
        if (remote == local) {
            // first do parent, then this level
            if (remoteSer.superClass() != null) {
                if (Serializable.class.isAssignableFrom(local.getSuperclass())) {
                    deserialize(ctxt, dataIter, object, local.getSuperclass(), remoteSer.superClass(),
                            ctxt.deserializeClass(remoteSer.superClass()));
                }
                // else: remote has a super but local's super is not serializable — skip remote's super data
                // (will be handled as gap/extra by the iterator)
            } else if (Serializable.class.isAssignableFrom(local.getSuperclass())) {
                // local has more serializable levels than remote — readObjectNoData route
                deserialize(ctxt, dataIter, object, local.getSuperclass(), remoteSer, remote);
            }
            // consume data for this level
            SerialData item = consumeIfMatches(dataIter, remoteSer);
            if (item != null) {
                CapturedObjectInputStream ois = new CapturedObjectInputStream(ctxt, local, object, remoteSer,
                        item.primitiveFieldData(), item.objectFieldData(), item.streamData());
                if (ReadUtil.hasReadObject(local)) {
                    ReadUtil.readObject(local, object, ois);
                } else {
                    ReadUtil.defaultReadObject(local, object, ois);
                }
            }
            // else: gap — no data for this level, fields keep their default values
        } else if (remote.isAssignableFrom(local)) {
            // remote is a supertype of local; local has an extra level with no remote data
            deserialize(ctxt, dataIter, object, local.getSuperclass(), remoteSer, remote);
            if (ReadUtil.hasReadObjectNoData(local)) {
                ReadUtil.readObjectNoData(local, object);
            }
        } else if (local.isAssignableFrom(remote)) {
            // local is a supertype of remote; remote has an extra level not present locally
            if (remoteSer.superClass() != null) {
                deserialize(ctxt, dataIter, object, local, remoteSer.superClass(),
                        ctxt.deserializeClass(remoteSer.superClass()));
            }
            // consume and discard the data for the skipped remote level
            consumeIfMatches(dataIter, remoteSer);
        } else {
            throw new InvalidObjectException("No relationship between local " + local + " and remote " + remote);
        }
    }

    /**
     * Consume the next data entry from the iterator if it matches the expected class descriptor.
     * If the next entry does not match (or the iterator is exhausted), returns {@code null}
     * without advancing the iterator.
     *
     * @param iter the data list iterator
     * @param expected the expected class descriptor for this level
     * @return the matching data entry, or {@code null} if no match (gap)
     */
    private static SerialData consumeIfMatches(final ListIterator<SerialData> iter,
            final SerializedSerializableClass expected) {
        if (iter.hasNext()) {
            SerialData entry = iter.next();
            if (entry.serializedClass() == expected) {
                return entry;
            }
            iter.previous();
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public int priority() {
        return PRIORITY_SERIALIZABLE;
    }
}
