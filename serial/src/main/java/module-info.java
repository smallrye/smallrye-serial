
module io.smallrye.serial {
    requires io.smallrye.common.constraint;
    requires io.smallrye.classfile;
    requires jdk.unsupported;

    exports io.smallrye.serial;
    exports io.smallrye.serial.spi;
    exports io.smallrye.serial.stream;

    uses io.smallrye.serial.spi.ObjectSerializer;
    uses io.smallrye.serial.spi.ObjectDeserializer;
}
