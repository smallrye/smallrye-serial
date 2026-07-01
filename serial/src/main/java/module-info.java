
module io.smallrye.serial {
    requires io.smallrye.common.constraint;
    requires jdk.unsupported;

    exports io.smallrye.serial;
    exports io.smallrye.serial.spi;

    uses io.smallrye.serial.spi.ObjectSerializer;
    uses io.smallrye.serial.spi.ObjectDeserializer;
}
