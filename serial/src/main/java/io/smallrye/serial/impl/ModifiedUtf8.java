package io.smallrye.serial.impl;

import java.io.UTFDataFormatException;

/**
 * Shared utility methods for encoding and decoding the modified UTF-8 format
 * used by Java's serialization protocol.
 * <p>
 * Modified UTF-8 differs from standard UTF-8 in two ways:
 * <ul>
 * <li>The null character ({@code U+0000}) is encoded as the two-byte sequence
 * {@code 0xC0 0x80} rather than a single zero byte.</li>
 * <li>Supplementary characters ({@code U+10000} and above) are represented as
 * surrogate pairs in CESU-8 encoding (two three-byte sequences) rather than
 * a single four-byte sequence.</li>
 * </ul>
 *
 * @see java.io.DataOutput#writeUTF(String)
 * @see java.io.DataInput#readUTF()
 */
public final class ModifiedUtf8 {

    private ModifiedUtf8() {
    }

    /**
     * Compute the number of bytes needed to encode the given string in modified UTF-8.
     * This does not include the 2-byte or 8-byte length prefix.
     *
     * @param str the string to measure (must not be {@code null})
     * @return the encoded byte length (may exceed 65535)
     */
    public static long encodedLength(final String str) {
        long len = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c > 0 && c <= 0x7f) {
                len++;
            } else if (c <= 0x7ff) {
                len += 2;
            } else {
                len += 3;
            }
        }
        return len;
    }

    /**
     * Encode the given string into modified UTF-8 bytes in the provided buffer.
     * The caller must ensure that the buffer has sufficient space starting at {@code off}
     * (at least {@link #encodedLength(String)} bytes).
     *
     * @param str the string to encode (must not be {@code null})
     * @param buf the destination byte array (must not be {@code null})
     * @param off the starting offset in the buffer
     * @return the number of bytes written
     */
    public static int encode(final String str, final byte[] buf, int off) {
        int start = off;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c > 0 && c <= 0x7f) {
                buf[off++] = (byte) c;
            } else if (c <= 0x7ff) {
                buf[off++] = (byte) (0xc0 | 0x1f & c >> 6);
                buf[off++] = (byte) (0x80 | 0x3f & c);
            } else {
                buf[off++] = (byte) (0xe0 | 0x0f & c >> 12);
                buf[off++] = (byte) (0x80 | 0x3f & c >> 6);
                buf[off++] = (byte) (0x80 | 0x3f & c);
            }
        }
        return off - start;
    }

    /**
     * Decode a modified UTF-8 byte sequence into a string.
     *
     * @param buf the source byte array (must not be {@code null})
     * @param off the starting offset in the buffer
     * @param len the number of bytes to decode
     * @return the decoded string (not {@code null})
     * @throws UTFDataFormatException if the byte sequence is malformed
     */
    public static String decode(final byte[] buf, int off, final int len) throws UTFDataFormatException {
        // range-check decoding: classify bytes by their high-bit pattern
        StringBuilder sb = new StringBuilder(len >> 1);
        int end = off + len;
        while (off < end) {
            int a = buf[off++] & 0xff;
            if (a < 0x80) {
                // one-byte character (0xxxxxxx)
                sb.append((char) a);
            } else if (a < 0xc0) {
                // bare continuation byte — invalid as a leading byte
                throw new UTFDataFormatException("Invalid leading byte: 0x" + Integer.toHexString(a));
            } else if (a < 0xe0) {
                // two-byte character (110xxxxx 10xxxxxx)
                if (off >= end) {
                    throw new UTFDataFormatException("Truncated two-byte sequence");
                }
                int b = buf[off++] & 0xff;
                if ((b & 0xc0) != 0x80) {
                    throw new UTFDataFormatException("Invalid continuation byte");
                }
                sb.append((char) ((a & 0x1f) << 6 | b & 0x3f));
            } else if (a < 0xf0) {
                // three-byte character (1110xxxx 10xxxxxx 10xxxxxx)
                if (off + 1 >= end) {
                    throw new UTFDataFormatException("Truncated three-byte sequence");
                }
                int b = buf[off++] & 0xff;
                int c = buf[off++] & 0xff;
                if ((b & 0xc0) != 0x80 || (c & 0xc0) != 0x80) {
                    throw new UTFDataFormatException("Invalid continuation byte");
                }
                sb.append((char) ((a & 0x0f) << 12 | (b & 0x3f) << 6 | c & 0x3f));
            } else {
                // four-byte or higher — not valid in modified UTF-8
                throw new UTFDataFormatException("Invalid leading byte: 0x" + Integer.toHexString(a));
            }
        }
        return sb.toString();
    }
}
