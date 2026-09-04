package io.smallrye.serial.impl;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.lang.invoke.MethodHandle;

public final class WriteUtil {

    static final ClassLocal<MethodHandle> writeReplaces = new ClassLocal<>(Util.RF::writeReplaceForSerialization);
    static final ClassLocal<MethodHandle> writeObjects = new ClassLocal<>(Util.RF::writeObjectForSerialization);
    static final ClassLocal<MethodHandle> defaultWriteObjects = new ClassLocal<>(
            DefaultSerialization::defaultWriteObjectForSerialization);

    private WriteUtil() {
    }

    public static boolean hasWriteObject(SerializerContextImpl ctxt, Class<?> type) {
        return ctxt.classLocal(writeObjects, type) != null;
    }

    public static void writeObject(SerializerContextImpl ctxt, Class<?> type, Object serializable, ObjectOutputStream oos)
            throws IOException {
        MethodHandle mh = ctxt.classLocal(writeObjects, type);
        if (mh == null) {
            throw new IllegalArgumentException("No writeObject method found on " + type);
        }
        try {
            mh.invoke(serializable, oos);
        } catch (IOException | RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw Util.sneak(e);
        }
    }

    public static void defaultWriteObject(SerializerContextImpl ctxt, Class<?> type, Object serializable,
            ObjectOutputStream oos) throws IOException {
        MethodHandle mh = ctxt.classLocal(defaultWriteObjects, type);
        if (mh == null) {
            throw new IllegalArgumentException("No defaultWriteObject method available for " + type);
        }
        try {
            mh.invoke(serializable, oos);
        } catch (IOException | RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw Util.sneak(e);
        }
    }

    public static boolean hasWriteReplace(SerializerContextImpl ctxt, Class<?> type) {
        return ctxt.classLocal(writeReplaces, type) != null;
    }

    public static Object writeReplace(SerializerContextImpl ctxt, Object object) throws ObjectStreamException {
        if (object == null) {
            return null;
        }
        MethodHandle wr = ctxt.classLocal(writeReplaces, object.getClass());
        if (wr == null) {
            throw new IllegalArgumentException("No writeReplace method found on " + object.getClass());
        }
        try {
            return wr.invoke(object);
        } catch (RuntimeException | Error | ObjectStreamException e) {
            throw e;
        } catch (Throwable e) {
            throw Util.sneak(e);
        }
    }
}
