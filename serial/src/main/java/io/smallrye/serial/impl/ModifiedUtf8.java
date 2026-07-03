package io.smallrye.serial.impl;

import static io.smallrye.serial.impl.Util.BE16;

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
            len += ModifiedUtf8Codec.encodedLength(str.charAt(i));
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
            switch (ModifiedUtf8Codec.encodedLength(c)) {
                case 1 -> buf[off++] = (byte) c;
                case 2 -> {
                    BE16.set(buf, off, ModifiedUtf8Codec.encode2(c));
                    off += 2;
                }
                case 3 -> {
                    int v = ModifiedUtf8Codec.encode3(c);
                    buf[off++] = (byte) (v >>> 16);
                    BE16.set(buf, off, (short) v);
                    off += 2;
                }
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
        StringBuilder sb = new StringBuilder(len >> 1);
        int end = off + len;
        while (off < end) {
            int a = buf[off++] & 0xff;
            if (a < 0x80) {
                sb.append((char) a);
            } else if (a < 0xc0) {
                throw new UTFDataFormatException("Invalid leading byte: 0x" + Integer.toHexString(a));
            } else if (a < 0xe0) {
                if (off >= end) {
                    throw new UTFDataFormatException("Truncated two-byte sequence");
                }
                int b = buf[off++] & 0xff;
                if ((b & 0xc0) != 0x80) {
                    throw new UTFDataFormatException("Invalid continuation byte");
                }
                sb.append(ModifiedUtf8Codec.decode2((short) (a << 8 | b)));
            } else if (a < 0xf0) {
                if (off + 1 >= end) {
                    throw new UTFDataFormatException("Truncated three-byte sequence");
                }
                int b = buf[off++] & 0xff;
                int c = buf[off++] & 0xff;
                if ((b & 0xc0) != 0x80 || (c & 0xc0) != 0x80) {
                    throw new UTFDataFormatException("Invalid continuation byte");
                }
                sb.append(ModifiedUtf8Codec.decode3(a << 16 | b << 8 | c));
            } else {
                throw new UTFDataFormatException("Invalid leading byte: 0x" + Integer.toHexString(a));
            }
        }
        return sb.toString();
    }
}
