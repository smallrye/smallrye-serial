package io.smallrye.serial.impl;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;

import io.smallrye.serial.SerializedPrimitiveClass;

/**
 * The primitive types of the Java language, with associated metadata
 * for serialization operations.
 */
public enum Primitive {
    /** The {@code boolean} type. */
    BOOLEAN('Z', "boolean", 1, 1, ConstantDescs.CD_boolean, SerializedPrimitiveClass.BOOLEAN),
    /** The {@code byte} type. */
    BYTE('B', "byte", 1, 1, ConstantDescs.CD_byte, SerializedPrimitiveClass.BYTE),
    /** The {@code char} type. */
    CHAR('C', "char", 2, 1, ConstantDescs.CD_char, SerializedPrimitiveClass.CHAR),
    /** The {@code short} type. */
    SHORT('S', "short", 2, 1, ConstantDescs.CD_short, SerializedPrimitiveClass.SHORT),
    /** The {@code int} type. */
    INT('I', "int", 4, 1, ConstantDescs.CD_int, SerializedPrimitiveClass.INT),
    /** The {@code long} type. */
    LONG('J', "long", 8, 2, ConstantDescs.CD_long, SerializedPrimitiveClass.LONG),
    /** The {@code float} type. */
    FLOAT('F', "float", 4, 1, ConstantDescs.CD_float, SerializedPrimitiveClass.FLOAT),
    /** The {@code double} type. */
    DOUBLE('D', "double", 8, 2, ConstantDescs.CD_double, SerializedPrimitiveClass.DOUBLE),
    /** The {@code void} type. */
    VOID('V', "void", 0, 0, ConstantDescs.CD_void, SerializedPrimitiveClass.VOID),
    ;

    private final char typeCode;
    private final String typeName;
    private final int byteSize;
    private final int slotSize;
    private final ClassDesc classDesc;
    private final SerializedPrimitiveClass serializedClass;

    Primitive(char typeCode, String typeName, int byteSize, int slotSize, ClassDesc classDesc,
            final SerializedPrimitiveClass serializedClass) {
        this.typeCode = typeCode;
        this.typeName = typeName;
        this.byteSize = byteSize;
        this.slotSize = slotSize;
        this.classDesc = classDesc;
        this.serializedClass = serializedClass;
    }

    /**
     * {@return the single-character JVM type code for this primitive type}
     */
    public char typeCode() {
        return typeCode;
    }

    /**
     * {@return the human-readable Java name of this primitive type}
     */
    public String typeName() {
        return typeName;
    }

    /**
     * {@return the number of bytes this primitive type occupies in a serialization buffer}
     */
    public int byteSize() {
        return byteSize;
    }

    /**
     * {@return the number of JVM local variable slots this primitive type occupies}
     */
    public int slotSize() {
        return slotSize;
    }

    /**
     * {@return the class descriptor for this primitive type}
     */
    public ClassDesc classDesc() {
        return classDesc;
    }

    /**
     * Return the {@code Primitive} for the given type code, or {@code null} if
     * the type code does not represent a primitive type.
     *
     * @param typeCode the single-character JVM type code
     * @return the matching primitive, or {@code null}
     */
    public static Primitive forTypeCode(char typeCode) {
        return switch (typeCode) {
            case 'Z' -> BOOLEAN;
            case 'B' -> BYTE;
            case 'C' -> CHAR;
            case 'S' -> SHORT;
            case 'I' -> INT;
            case 'J' -> LONG;
            case 'F' -> FLOAT;
            case 'D' -> DOUBLE;
            case 'V' -> VOID;
            default -> null;
        };
    }

    /**
     * Return the {@code Primitive} for the given {@link ClassDesc}, or {@code null} if
     * the class descriptor does not represent a primitive type.
     *
     * @param classDesc the class descriptor to look up
     * @return the matching primitive, or {@code null}
     */
    public static Primitive forClassDesc(ClassDesc classDesc) {
        return forTypeCode(classDesc.descriptorString().charAt(0));
    }

    /**
     * Return the {@code Primitive} for the given {@link Class}, or {@code null} if
     * the class is not a primitive type.
     *
     * @param type the class to look up
     * @return the matching primitive, or {@code null}
     */
    public static Primitive forClass(Class<?> type) {
        return forClassDesc(Util.classDesc(type));
    }

    /**
     * {@return the serialized primitive class for this primitive type}
     */
    public SerializedPrimitiveClass serializedClass() {
        return serializedClass;
    }
}
