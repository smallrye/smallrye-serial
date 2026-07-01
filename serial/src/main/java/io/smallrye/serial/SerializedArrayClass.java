package io.smallrye.serial;

import java.lang.constant.ClassDesc;

import io.smallrye.common.constraint.Assert;

/**
 * The serialized representation of an array class.
 * Array class descriptors carry a reference to the component type's class descriptor
 * and a serial version UID, in addition to the class descriptor and class loader
 * inherited from {@link SerializedVersionedClass}.
 * <p>
 * Note: the Java serialization specification waives the requirement for matching
 * serial version UIDs on array classes, so the UID value is not significant for
 * compatibility purposes.
 */
public final class SerializedArrayClass extends SerializedVersionedClass {

    private final SerializedClass componentType;

    /**
     * Construct a new instance.
     *
     * @param classDesc the class descriptor for the array type (must not be {@code null})
     * @param classLoader the serialized class loader (must not be {@code null} but may be {@link SerializedNull#INSTANCE})
     * @param uid the serial version UID (not checked for arrays per the serialization specification)
     * @param componentType the class descriptor of the array's component type (must not be {@code null})
     */
    public SerializedArrayClass(final ClassDesc classDesc, final Serialized classLoader, final long uid,
            final SerializedClass componentType) {
        super(classDesc, classLoader, uid);
        this.componentType = Assert.checkNotNullParam("componentType", componentType);
    }

    /**
     * {@return the class descriptor of the array's component type (not {@code null})}
     * For multi-dimensional arrays, this is itself a {@code SerializedArrayClass}.
     */
    public SerializedClass componentType() {
        return componentType;
    }

    /**
     * {@return the number of array dimensions}
     * For example, {@code String[]} has 1 dimension, {@code int[][]} has 2 dimensions.
     */
    public int dimensions() {
        int dims = 1;
        SerializedClass ct = componentType;
        while (ct instanceof SerializedArrayClass arr) {
            dims++;
            ct = arr.componentType;
        }
        return dims;
    }

    /**
     * {@return the innermost non-array component type (not {@code null})}
     * For example, for {@code String[][]}, this returns the descriptor for {@code String}.
     */
    public SerializedClass leafComponentType() {
        SerializedClass ct = componentType;
        while (ct instanceof SerializedArrayClass arr) {
            ct = arr.componentType;
        }
        return ct;
    }
}
