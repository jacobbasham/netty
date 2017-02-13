/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util.internal;


import io.netty.util.AsciiString;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * String utility class.
 */
public final class StringUtil {

    public static final String EMPTY_STRING = "";
    public static final String NEWLINE;

    public static final char DOUBLE_QUOTE = '\"';
    public static final char COMMA = ',';
    public static final char LINE_FEED = '\n';
    public static final char CARRIAGE_RETURN = '\r';
    public static final char TAB = '\t';

    private static final String[] BYTE2HEX_PAD = new String[256];
    private static final String[] BYTE2HEX_NOPAD = new String[256];

    /**
     * 2 - Quote character at beginning and end.
     * 5 - Extra allowance for anticipated escape characters that may be added.
     */
    private static final int CSV_NUMBER_ESCAPE_CHARACTERS = 2 + 5;
    private static final char PACKAGE_SEPARATOR_CHAR = '.';

    static {
        // Determine the newline character of the current platform.
        String newLine;

        Formatter formatter = new Formatter();
        try {
            newLine = formatter.format("%n").toString();
        } catch (Exception e) {
            // Should not reach here, but just in case.
            newLine = "\n";
        } finally {
            formatter.close();
        }

        NEWLINE = newLine;

        // Generate the lookup table that converts a byte into a 2-digit hexadecimal integer.
        int i;
        for (i = 0; i < 10; i ++) {
            StringBuilder buf = new StringBuilder(2);
            buf.append('0');
            buf.append(i);
            BYTE2HEX_PAD[i] = buf.toString();
            BYTE2HEX_NOPAD[i] = String.valueOf(i);
        }
        for (; i < 16; i ++) {
            StringBuilder buf = new StringBuilder(2);
            char c = (char) ('a' + i - 10);
            buf.append('0');
            buf.append(c);
            BYTE2HEX_PAD[i] = buf.toString();
            BYTE2HEX_NOPAD[i] = String.valueOf(c);
        }
        for (; i < BYTE2HEX_PAD.length; i ++) {
            StringBuilder buf = new StringBuilder(2);
            buf.append(Integer.toHexString(i));
            String str = buf.toString();
            BYTE2HEX_PAD[i] = str;
            BYTE2HEX_NOPAD[i] = str;
        }
    }

    /**
     * Splits the specified {@link String} with the specified delimiter.  This operation is a simplified and optimized
     * version of {@link String#split(String)}.
     */
    public static String[] split(String value, char delim) {
        final int end = value.length();
        final List<String> res = new ArrayList<String>();

        int start = 0;
        for (int i = 0; i < end; i ++) {
            if (value.charAt(i) == delim) {
                if (start == i) {
                    res.add(EMPTY_STRING);
                } else {
                    res.add(value.substring(start, i));
                }
                start = i + 1;
            }
        }

        if (start == 0) { // If no delimiter was found in the value
            res.add(value);
        } else {
            if (start != end) {
                // Add the last element if it's not empty.
                res.add(value.substring(start, end));
            } else {
                // Truncate trailing empty elements.
                for (int i = res.size() - 1; i >= 0; i --) {
                    if (res.get(i).isEmpty()) {
                        res.remove(i);
                    } else {
                        break;
                    }
                }
            }
        }

        return res.toArray(new String[res.size()]);
    }

    /**
     * Splits the specified {@link String} with the specified delimiter in maxParts maximum parts.
     * This operation is a simplified and optimized
     * version of {@link String#split(String, int)}.
     */
    public static String[] split(String value, char delim, int maxParts) {
        final int end = value.length();
        final List<String> res = new ArrayList<String>();

        int start = 0;
        int cpt = 1;
        for (int i = 0; i < end && cpt < maxParts; i ++) {
            if (value.charAt(i) == delim) {
                if (start == i) {
                    res.add(EMPTY_STRING);
                } else {
                    res.add(value.substring(start, i));
                }
                start = i + 1;
                cpt++;
            }
        }

        if (start == 0) { // If no delimiter was found in the value
            res.add(value);
        } else {
            if (start != end) {
                // Add the last element if it's not empty.
                res.add(value.substring(start, end));
            } else {
                // Truncate trailing empty elements.
                for (int i = res.size() - 1; i >= 0; i --) {
                    if (res.get(i).isEmpty()) {
                        res.remove(i);
                    } else {
                        break;
                    }
                }
            }
        }

        return res.toArray(new String[res.size()]);
    }

    /**
     * Get the item after one char delim if the delim is found (else null).
     * This operation is a simplified and optimized
     * version of {@link String#split(String, int)}.
     */
    public static String substringAfter(String value, char delim) {
        int pos = value.indexOf(delim);
        if (pos >= 0) {
            return value.substring(pos + 1);
        }
        return null;
    }

    /**
     * Converts the specified byte value into a 2-digit hexadecimal integer.
     */
    public static String byteToHexStringPadded(int value) {
        return BYTE2HEX_PAD[value & 0xff];
    }

    /**
     * Converts the specified byte value into a 2-digit hexadecimal integer and appends it to the specified buffer.
     */
    public static <T extends Appendable> T byteToHexStringPadded(T buf, int value) {
        try {
            buf.append(byteToHexStringPadded(value));
        } catch (IOException e) {
            PlatformDependent.throwException(e);
        }
        return buf;
    }

    /**
     * Converts the specified byte array into a hexadecimal value.
     */
    public static String toHexStringPadded(byte[] src) {
        return toHexStringPadded(src, 0, src.length);
    }

    /**
     * Converts the specified byte array into a hexadecimal value.
     */
    public static String toHexStringPadded(byte[] src, int offset, int length) {
        return toHexStringPadded(new StringBuilder(length << 1), src, offset, length).toString();
    }

    /**
     * Converts the specified byte array into a hexadecimal value and appends it to the specified buffer.
     */
    public static <T extends Appendable> T toHexStringPadded(T dst, byte[] src) {
        return toHexStringPadded(dst, src, 0, src.length);
    }

    /**
     * Converts the specified byte array into a hexadecimal value and appends it to the specified buffer.
     */
    public static <T extends Appendable> T toHexStringPadded(T dst, byte[] src, int offset, int length) {
        final int end = offset + length;
        for (int i = offset; i < end; i ++) {
            byteToHexStringPadded(dst, src[i]);
        }
        return dst;
    }

    /**
     * Converts the specified byte value into a hexadecimal integer.
     */
    public static String byteToHexString(int value) {
        return BYTE2HEX_NOPAD[value & 0xff];
    }

    /**
     * Converts the specified byte value into a hexadecimal integer and appends it to the specified buffer.
     */
    public static <T extends Appendable> T byteToHexString(T buf, int value) {
        try {
            buf.append(byteToHexString(value));
        } catch (IOException e) {
            PlatformDependent.throwException(e);
        }
        return buf;
    }

    /**
     * Converts the specified byte array into a hexadecimal value.
     */
    public static String toHexString(byte[] src) {
        return toHexString(src, 0, src.length);
    }

    /**
     * Converts the specified byte array into a hexadecimal value.
     */
    public static String toHexString(byte[] src, int offset, int length) {
        return toHexString(new StringBuilder(length << 1), src, offset, length).toString();
    }

    /**
     * Converts the specified byte array into a hexadecimal value and appends it to the specified buffer.
     */
    public static <T extends Appendable> T toHexString(T dst, byte[] src) {
        return toHexString(dst, src, 0, src.length);
    }

    /**
     * Converts the specified byte array into a hexadecimal value and appends it to the specified buffer.
     */
    public static <T extends Appendable> T toHexString(T dst, byte[] src, int offset, int length) {
        assert length >= 0;
        if (length == 0) {
            return dst;
        }

        final int end = offset + length;
        final int endMinusOne = end - 1;
        int i;

        // Skip preceding zeroes.
        for (i = offset; i < endMinusOne; i ++) {
            if (src[i] != 0) {
                break;
            }
        }

        byteToHexString(dst, src[i ++]);
        int remaining = end - i;
        toHexStringPadded(dst, src, i, remaining);

        return dst;
    }

    /**
     * The shortcut to {@link #simpleClassName(Class) simpleClassName(o.getClass())}.
     */
    public static String simpleClassName(Object o) {
        if (o == null) {
            return "null_object";
        } else {
            return simpleClassName(o.getClass());
        }
    }

    /**
     * Generates a simplified name from a {@link Class}.  Similar to {@link Class#getSimpleName()}, but it works fine
     * with anonymous classes.
     */
    public static String simpleClassName(Class<?> clazz) {
        String className = ObjectUtil.checkNotNull(clazz, "clazz").getName();
        final int lastDotIdx = className.lastIndexOf(PACKAGE_SEPARATOR_CHAR);
        if (lastDotIdx > -1) {
            return className.substring(lastDotIdx + 1);
        }
        return className;
    }

    /**
     * Escapes the specified value, if necessary according to
     * <a href="https://tools.ietf.org/html/rfc4180#section-2">RFC-4180</a>.
     *
     * @param value The value which will be escaped according to
     *              <a href="https://tools.ietf.org/html/rfc4180#section-2">RFC-4180</a>
     * @return {@link CharSequence} the escaped value if necessary, or the value unchanged
     */
    public static CharSequence escapeCsv(CharSequence value) {
        int length = checkNotNull(value, "value").length();
        if (length == 0) {
            return value;
        }
        int last = length - 1;
        boolean quoted = isDoubleQuote(value.charAt(0)) && isDoubleQuote(value.charAt(last)) && length != 1;
        boolean foundSpecialCharacter = false;
        boolean escapedDoubleQuote = false;
        StringBuilder escaped = new StringBuilder(length + CSV_NUMBER_ESCAPE_CHARACTERS).append(DOUBLE_QUOTE);
        for (int i = 0; i < length; i++) {
            char current = value.charAt(i);
            switch (current) {
                case DOUBLE_QUOTE:
                    if (i == 0 || i == last) {
                        if (!quoted) {
                            escaped.append(DOUBLE_QUOTE);
                        } else {
                            continue;
                        }
                    } else {
                        boolean isNextCharDoubleQuote = isDoubleQuote(value.charAt(i + 1));
                        if (!isDoubleQuote(value.charAt(i - 1)) &&
                                (!isNextCharDoubleQuote || i + 1 == last)) {
                            escaped.append(DOUBLE_QUOTE);
                            escapedDoubleQuote = true;
                        }
                        break;
                    }
                case LINE_FEED:
                case CARRIAGE_RETURN:
                case COMMA:
                    foundSpecialCharacter = true;
            }
            escaped.append(current);
        }
        return escapedDoubleQuote || foundSpecialCharacter && !quoted ?
                escaped.append(DOUBLE_QUOTE) : value;
    }

    /**
     * Get the length of a string, {@code null} input is considered {@code 0} length.
     */
    public static int length(String s) {
        return s == null ? 0 : s.length();
    }

    /**
     * Determine if a string is {@code null} or {@link String#isEmpty()} returns {@code true}.
     */
    public static boolean isNullOrEmpty(String s) {
        return s == null || s.isEmpty();
    }

    private static boolean isDoubleQuote(char c) {
        return c == DOUBLE_QUOTE;
    }

    /**
     * Computes a hash code for a CharSequence.  If ignoreCase is false, the result
     * will be the same as that of java.lang.String.
     * @param seq A char sequence
     * @param ignoreCase If true, generate a hash code for the lower-case version of the
     * passed CharSequence
     * @return A hash code
     * @since 5.0.0.Alpha3
     */
    public static int charSequenceHashCode(CharSequence seq, boolean ignoreCase) {
        // Same computation as java.lang.String for case sensitive
        int length = seq.length();
        if (length == 0) {
            return 0;
        }
        int result = 0;
        for (int i = 0; i < length; i++) {
            if (ignoreCase) {
                result = 31 * result + Character.toLowerCase(seq.charAt(i));
            } else {
                result = 31 * result + seq.charAt(i);
            }
        }
        return result;
    }

    /**
     * Compare the contents of two CharSequences which may be of different types
     * for equality.
     * @param a One character sequence
     * @param b Another character sequence
     * @param ignoreCase If true, do a case-insensitive comparison
     * @return true if they match
     * @since 5.0.0.Alpha3
     */
    public static boolean charSequencesEqual(CharSequence a, CharSequence b, boolean ignoreCase) {
        if ((a == null) != (b == null)) {
            return false;
        } else if (a == b) {
            return true;
        }
        int length = a.length();
        if (length != b.length()) {
            return false;
        }
        if (ignoreCase && a.getClass() == b.getClass()) {
            return a.equals(b);
        }
        if (!ignoreCase && a instanceof String) {
            return  ((String) a).contentEquals(b);
        } else if (!ignoreCase && b instanceof String) {
            return ((String) b).contentEquals(a);
        } else if (a instanceof AsciiString) {
            return ignoreCase ? ((AsciiString) a).contentEqualsIgnoreCase(b) : ((AsciiString) a).contentEquals(b);
        } else if (b instanceof AsciiString) {
            return ignoreCase ? ((AsciiString) b).contentEqualsIgnoreCase(a) : ((AsciiString) b).contentEquals(a);
        } else {
            for (int i = 0; i < length; i++) {
                char ca = ignoreCase ? Character.toLowerCase(a.charAt(i)) : a.charAt(i);
                char cb = ignoreCase ? Character.toLowerCase(b.charAt(i)) : b.charAt(i);
                if (cb != ca) {
                    return false;
                }
            }
        }
        return true;
    }

    private StringUtil() {
        // Unused.
    }
}
