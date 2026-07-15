package io.smallrye.serial.impl;

/**
 * Low-level encode/decode primitives for modified UTF-8 multi-byte sequences.
 * <p>
 * These methods pack and unpack the data bits of 2-byte and 3-byte modified UTF-8
 * sequences as big-endian {@code short} and {@code int} values, suitable for
 * direct VarHandle writes to byte arrays or incremental byte-at-a-time output.
 * <p>
 * The base (Java 17) version uses shift-and-mask arithmetic. The JDK 19+ MR-JAR
 * overlay replaces the encode/decode methods with
 * {@link Integer#expand}/{@link Integer#compress}, which map to single-cycle BMI2
 * {@code PDEP}/{@code PEXT} instructions on modern x86 processors.
 */
public final class ModifiedUtf8Codec {
    static {
        if (Runtime.version().feature() >= 19) {
            throw new InternalError("Expected JDK 19+ Multi-Release JAR class to be loaded");
        }
    }

    private ModifiedUtf8Codec() {
    }

    /**
     * {@return the number of bytes needed to encode the given character in modified UTF-8}
     * Returns 1 for ASCII (except {@code U+0000}), 2 for {@code U+0000}–{@code U+07FF},
     * and 3 for {@code U+0800}–{@code U+FFFF}.
     *
     * @param c the character
     */
    public static int encodedLength(char c) {
        if (c > 0 && c <= 0x7f) {
            return 1;
        } else if (c <= 0x7ff) {
            return 2;
        } else {
            return 3;
        }
    }

    /**
     * Encode a character as a 2-byte modified UTF-8 sequence, packed big-endian
     * into a {@code short}: {@code 110xxxxx 10xxxxxx}.
     *
     * @param c the character to encode (must be in range {@code U+0000}–{@code U+07FF})
     * @return the packed 2-byte sequence
     */
    public static short encode2(char c) {
        return (short) ((0xc0 | c >> 6) << 8 | (0x80 | c & 0x3f));
    }

    /**
     * Encode a character as a 3-byte modified UTF-8 sequence, packed big-endian
     * into the low 24 bits of an {@code int}: {@code 1110xxxx 10xxxxxx 10xxxxxx}.
     *
     * @param c the character to encode (must be in range {@code U+0800}–{@code U+FFFF})
     * @return the packed 3-byte sequence (high byte is zero)
     */
    public static int encode3(char c) {
        return (0xe0 | c >> 12) << 16 | (0x80 | (c >> 6) & 0x3f) << 8 | (0x80 | c & 0x3f);
    }

    /**
     * Decode a 2-byte modified UTF-8 sequence from a big-endian {@code short}.
     * The caller must have already validated that the bytes have the correct
     * prefix pattern ({@code 110xxxxx 10xxxxxx}).
     *
     * @param v the packed 2-byte sequence
     * @return the decoded character
     */
    public static char decode2(short v) {
        return (char) ((v >> 8 & 0x1f) << 6 | v & 0x3f);
    }

    /**
     * Decode a 3-byte modified UTF-8 sequence from the low 24 bits of an {@code int}.
     * The caller must have already validated that the bytes have the correct
     * prefix pattern ({@code 1110xxxx 10xxxxxx 10xxxxxx}).
     *
     * @param v the packed 3-byte sequence (high byte ignored)
     * @return the decoded character
     */
    public static char decode3(int v) {
        return (char) ((v >> 16 & 0x0f) << 12 | (v >> 8 & 0x3f) << 6 | v & 0x3f);
    }
}
