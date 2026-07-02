package io.smallrye.serial.stream;

/**
 * Wire-format constants for the Java serialization stream protocol.
 * Shared by {@link SerialStreamReader} and {@link SerialStreamWriter}.
 *
 * @see <a href="https://docs.oracle.com/en/java/docs/books/jls/serialization/protocol.html">
 *      Java Object Serialization Protocol</a>
 */
final class SerialProtocol {

    // ---- Stream header ----

    static final short STREAM_MAGIC = (short) 0xACED;
    static final short STREAM_VERSION = 5;
    static final int BASE_WIRE_HANDLE = 0x7E0000;

    // ---- Type codes ----

    static final byte TC_NULL = 0x70;
    static final byte TC_REFERENCE = 0x71;
    static final byte TC_CLASSDESC = 0x72;
    static final byte TC_OBJECT = 0x73;
    static final byte TC_STRING = 0x74;
    static final byte TC_ARRAY = 0x75;
    static final byte TC_CLASS = 0x76;
    static final byte TC_BLOCKDATA = 0x77;
    static final byte TC_ENDBLOCKDATA = 0x78;
    static final byte TC_RESET = 0x79;
    static final byte TC_BLOCKDATALONG = 0x7A;
    static final byte TC_EXCEPTION = 0x7B;
    static final byte TC_LONGSTRING = 0x7C;
    static final byte TC_PROXYCLASSDESC = 0x7D;
    static final byte TC_ENUM = 0x7E;

    // ---- Class descriptor flags ----

    static final byte SC_WRITE_METHOD = 0x01;
    static final byte SC_SERIALIZABLE = 0x02;
    static final byte SC_EXTERNALIZABLE = 0x04;
    static final byte SC_BLOCK_DATA = 0x08;
    static final byte SC_ENUM = 0x10;

    private SerialProtocol() {
    }
}
