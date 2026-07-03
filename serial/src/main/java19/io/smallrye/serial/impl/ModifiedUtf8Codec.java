package io.smallrye.serial.impl;

/**
 * JDK 19+ version of modified UTF-8 encode/decode primitives using
 * {@link Integer#expand} and {@link Integer#compress} (BMI2 {@code PDEP}/{@code PEXT}).
 */
@SuppressWarnings("unused") // MR JAR layer
public final class ModifiedUtf8Codec {
    private ModifiedUtf8Codec() {
    }

    /**
     * {@return the number of bytes needed to encode the given character in modified UTF-8}
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
        return (short) (Integer.expand(c, 0x1F3F) | 0xC080);
    }

    /**
     * Encode a character as a 3-byte modified UTF-8 sequence, packed big-endian
     * into the low 24 bits of an {@code int}: {@code 1110xxxx 10xxxxxx 10xxxxxx}.
     *
     * @param c the character to encode (must be in range {@code U+0800}–{@code U+FFFF})
     * @return the packed 3-byte sequence (high byte is zero)
     */
    public static int encode3(char c) {
        return Integer.expand(c, 0x0F_3F3F) | 0xE0_8080;
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
        return (char) Integer.compress(v & 0x1F3F, 0x1F3F);
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
        return (char) Integer.compress(v & 0x0F_3F3F, 0x0F_3F3F);
    }
}
