package io.smallrye.serial.impl;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamException;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;

public final class ReadUtil {
    // Constructor.newInstance() must be used here rather than unreflecting to a MethodHandle, because the
    // Constructor returned by ReflectionFactory has a declaring class of the non-serializable superclass
    // (not the target class), so unreflectConstructor would create instances of the wrong class.
    static final ClassLocal<Constructor<?>> serNewInstances = new ClassLocal<>(Util.RF::newConstructorForSerialization);
    static final ClassLocal<Constructor<?>> extNewInstances = new ClassLocal<>(Util.RF::newConstructorForExternalization);
    static final ClassLocal<MethodHandle> readObjects = new ClassLocal<>(Util.RF::readObjectForSerialization);
    static final ClassLocal<MethodHandle> readObjectNoDatas = new ClassLocal<>(Util.RF::readObjectNoDataForSerialization);
    static final ClassLocal<MethodHandle> defaultReadObjects = new ClassLocal<>(
            DefaultSerialization::defaultReadObjectForSerialization);
    static final ClassLocal<MethodHandle> readResolves = new ClassLocal<>(Util.RF::readResolveForSerialization);

    private ReadUtil() {
    }

    public static boolean hasReadObject(DeserializerContextImpl ctxt, Class<?> type) {
        return ctxt.classLocal(readObjects, type) != null;
    }

    public static void readObject(DeserializerContextImpl ctxt, Class<?> type, Object serializable, ObjectInputStream ois)
            throws IOException, ClassNotFoundException {
        MethodHandle mh = ctxt.classLocal(readObjects, type);
        if (mh == null) {
            throw new IllegalArgumentException("No readObject method found on " + type);
        }
        try {
            mh.invoke(serializable, ois);
        } catch (IOException | ClassNotFoundException | RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw Util.sneak(e);
        }
    }

    public static boolean hasReadObjectNoData(DeserializerContextImpl ctxt, Class<?> type) {
        return ctxt.classLocal(readObjectNoDatas, type) != null;
    }

    public static void readObjectNoData(DeserializerContextImpl ctxt, Class<?> type, Object serializable)
            throws ObjectStreamException {
        MethodHandle mh = ctxt.classLocal(readObjectNoDatas, type);
        if (mh == null) {
            throw new IllegalArgumentException("No readObject method found on " + type);
        }
        try {
            mh.invoke(serializable);
        } catch (ObjectStreamException | RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw Util.sneak(e);
        }
    }

    public static void defaultReadObject(DeserializerContextImpl ctxt, Class<?> type, Object serializable,
            ObjectInputStream ois)
            throws IOException, ClassNotFoundException {
        MethodHandle mh = ctxt.classLocal(defaultReadObjects, type);
        if (mh == null) {
            throw new IllegalArgumentException("No defaultReadObject method available for " + type);
        }
        try {
            mh.invoke(serializable, ois);
        } catch (IOException | ClassNotFoundException | RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw Util.sneak(e);
        }
    }

    public static <T> T newSerializableInstance(DeserializerContextImpl ctxt, final Class<T> clazz) {
        Constructor<?> ctor = ctxt.classLocal(serNewInstances, clazz);
        if (ctor == null) {
            throw new IllegalArgumentException("No valid constructor found on serializable " + clazz);
        }
        try {
            return clazz.cast(ctor.newInstance());
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw Util.sneak(e);
        }
    }

    public static <T extends Externalizable> T newExternalizableInstance(DeserializerContextImpl ctxt, Class<T> clazz) {
        Constructor<?> ctor = ctxt.classLocal(extNewInstances, clazz);
        if (ctor == null) {
            throw new IllegalArgumentException("No valid constructor found on Externalizable " + clazz);
        }
        try {
            return clazz.cast(ctor.newInstance());
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw Util.sneak(e);
        }
    }

    public static boolean hasReadResolve(DeserializerContextImpl ctxt, Class<?> clazz) {
        return ctxt.classLocal(readResolves, clazz) != null;
    }

    public static Object readResolve(DeserializerContextImpl ctxt, Object object) throws ObjectStreamException {
        if (object == null) {
            return null;
        }
        MethodHandle rr = ctxt.classLocal(readResolves, object.getClass());
        if (rr == null) {
            throw new IllegalArgumentException("No readResolve method found on " + object.getClass());
        }
        try {
            return rr.invoke(object);
        } catch (RuntimeException | Error | ObjectStreamException e) {
            throw e;
        } catch (Throwable e) {
            throw Util.sneak(e);
        }
    }
}
