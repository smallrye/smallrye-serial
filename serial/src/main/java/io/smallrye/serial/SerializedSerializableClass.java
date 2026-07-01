package io.smallrye.serial;

import java.lang.constant.ClassDesc;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.smallrye.common.constraint.Assert;
import io.smallrye.serial.impl.Util;

/**
 * The serialized representation of a class that implements {@link java.io.Serializable}
 * (but not {@link java.io.Externalizable} and not a {@code record}).
 * <p>
 * In addition to the stream field layout inherited from {@link SerializedFieldedClass},
 * serializable class descriptors carry a reference to the nearest serializable superclass
 * in the serialization hierarchy.
 */
public final class SerializedSerializableClass extends SerializedFieldedClass implements HasSuperClass {

    private final SerializedSerializableClass superClass;
    private final boolean hasWriteMethod;

    /**
     * Construct a new instance.
     *
     * @param classDesc the class descriptor (must not be {@code null})
     * @param classLoader the serialized class loader (must not be {@code null} but may be {@link SerializedNull#INSTANCE})
     * @param superClass the nearest serializable superclass descriptor, or {@code null} if none
     * @param fields the stream fields, sorted by {@linkplain SerialField#name() name} (must not be {@code null})
     * @param primitiveBufferSize the size of the primitive buffer for this class
     * @param objectBufferSize the size of the object buffer for this class
     * @param uid the serial version UID
     * @param hasWriteMethod {@code true} if the class has a custom {@code writeObject} method
     */
    // called via MethodHandle from ClassSerializer
    SerializedSerializableClass(final ClassDesc classDesc, final Serialized classLoader,
            final SerializedSerializableClass superClass,
            final List<SerialField> fields, final int primitiveBufferSize, final int objectBufferSize,
            final long uid, final boolean hasWriteMethod) {
        super(classDesc, classLoader, uid, fields, primitiveBufferSize, objectBufferSize);
        this.superClass = superClass;
        this.hasWriteMethod = hasWriteMethod;
    }

    /**
     * {@return the nearest serializable superclass descriptor in the serialization hierarchy,
     * or {@code null} if there is none}
     */
    @Override
    public SerializedSerializableClass superClass() {
        return superClass;
    }

    /**
     * {@return {@code true} if this class has a custom {@code writeObject} method,
     * or equivalently if the stream class descriptor had the {@code SC_WRITE_METHOD} flag set}
     */
    public boolean hasWriteMethod() {
        return hasWriteMethod;
    }

    /**
     * {@return a new builder for constructing {@link SerializedSerializableClass} instances}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A builder for constructing {@link SerializedSerializableClass} instances from wire protocol data.
     */
    public static final class Builder {
        private Builder() {
        }

        private ClassDesc classDesc;
        private Serialized classLoader;
        private SerializedSerializableClass superClass;
        private Map<String, ClassDesc> fields = Map.of();
        private long uid;
        private boolean hasWriteMethod;

        /**
         * Set the class descriptor.
         *
         * @param classDesc the class descriptor (must not be {@code null})
         * @return this builder
         */
        public Builder classDesc(final ClassDesc classDesc) {
            this.classDesc = classDesc;
            return this;
        }

        /**
         * Set the class name, converting it to a {@link ClassDesc} internally.
         * This is a convenience method that accepts a {@link Class#getName()}-format string.
         *
         * @param name the class name (must not be {@code null})
         * @return this builder
         */
        public Builder name(final String name) {
            return classDesc(Util.classDescOfName(name));
        }

        /**
         * Set the class loader.
         *
         * @param classLoader the serialized class loader (must not be {@code null})
         * @return this builder
         */
        public Builder classLoader(final Serialized classLoader) {
            this.classLoader = classLoader;
            return this;
        }

        /**
         * Set the superclass in the serialization hierarchy.
         *
         * @param superClass the serialized superclass, or {@code null} if none
         * @return this builder
         */
        public Builder superClass(final SerializedSerializableClass superClass) {
            this.superClass = superClass;
            return this;
        }

        /**
         * Set the serial version UID.
         *
         * @param uid the UID value
         * @return this builder
         */
        public Builder uid(final long uid) {
            this.uid = uid;
            return this;
        }

        /**
         * Set whether this class has a custom {@code writeObject} method.
         *
         * @param hasWriteMethod {@code true} if the class has a custom {@code writeObject} method
         * @return this builder
         */
        public Builder hasWriteMethod(final boolean hasWriteMethod) {
            this.hasWriteMethod = hasWriteMethod;
            return this;
        }

        /**
         * Add a serializable stream field.
         *
         * @param name the field name (must not be {@code null})
         * @param type the field type descriptor (must not be {@code null})
         * @return this builder
         * @throws IllegalArgumentException if a field with the same name has already been added
         */
        public Builder addField(final String name, final ClassDesc type) {
            Assert.checkNotNullParam("name", name);
            Assert.checkNotNullParam("type", type);
            switch (fields.size()) {
                case 0 -> fields = Map.of(name, type);
                case 1 -> {
                    if (fields.containsKey(name)) {
                        throw duplicateField(name);
                    }
                    Map.Entry<String, ClassDesc> e = fields.entrySet().iterator().next();
                    fields = Map.of(e.getKey(), e.getValue(), name, type);
                }
                case 2 -> {
                    if (fields.containsKey(name)) {
                        throw duplicateField(name);
                    }
                    HashMap<String, ClassDesc> hm = new HashMap<>(8);
                    hm.putAll(fields);
                    hm.put(name, type);
                    fields = hm;
                }
                default -> {
                    if (fields.putIfAbsent(name, type) != null) {
                        throw duplicateField(name);
                    }
                }
            }
            return this;
        }

        /**
         * Add a serializable stream field using a live {@link Class} reference.
         * This is a convenience overload that converts the class to a {@link ClassDesc} internally.
         *
         * @param name the field name (must not be {@code null})
         * @param type the field type (must not be {@code null})
         * @return this builder
         * @throws IllegalArgumentException if a field with the same name has already been added
         */
        public Builder addField(final String name, final Class<?> type) {
            return addField(name, type.describeConstable().orElseThrow());
        }

        /**
         * Build and return the configured {@link SerializedSerializableClass}.
         *
         * @return the new instance (not {@code null})
         */
        public SerializedSerializableClass build() {
            ClassDesc classDesc = Assert.checkNotNullParam("classDesc", this.classDesc);
            Serialized classLoader = Assert.checkNotNullParam("classLoader", this.classLoader);
            String[] names = fields.keySet().toArray(String[]::new);
            Arrays.sort(names);
            // assign offsets: primitives by byte size, objects by slot index
            int po = 0, oo = 0;
            SerialField[] result = new SerialField[names.length];
            for (int i = 0; i < names.length; i++) {
                ClassDesc type = fields.get(names[i]);
                char tc = type.descriptorString().charAt(0);
                int offset;
                if (type.isPrimitive()) {
                    offset = po;
                    po += switch (tc) {
                        case 'B', 'Z' -> 1;
                        case 'C', 'S' -> 2;
                        case 'I', 'F' -> 4;
                        case 'J', 'D' -> 8;
                        default -> throw Assert.impossibleSwitchCase(tc);
                    };
                } else {
                    offset = oo++;
                }
                result[i] = new SerialField(names[i], type, offset);
            }

            return new SerializedSerializableClass(classDesc, classLoader, superClass, List.of(result), po, oo, uid,
                    hasWriteMethod);
        }

        private static IllegalArgumentException duplicateField(String name) {
            return new IllegalArgumentException("Duplicate field name: " + name);
        }
    }
}
