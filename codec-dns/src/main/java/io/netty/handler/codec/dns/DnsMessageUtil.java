/*
 * Copyright 2015 The Netty Project
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
package io.netty.handler.codec.dns;

import io.netty.channel.AddressedEnvelope;
import io.netty.util.internal.StringUtil;

import java.net.SocketAddress;

/**
 * Provides some utility methods for DNS message implementations.
 */
final class DnsMessageUtil {

    static StringBuilder appendQuery(StringBuilder buf, DnsQuery query) {
        appendQueryHeader(buf, query);
        appendAllRecords(buf, query);
        return buf;
    }

    static StringBuilder appendResponse(StringBuilder buf, DnsResponse response) {
        appendResponseHeader(buf, response);
        appendAllRecords(buf, response);
        return buf;
    }

    static StringBuilder appendRecordClass(StringBuilder buf, DnsClass dnsClass) {
        buf.append(dnsClass.name());
        return buf;
    }

    private static void appendQueryHeader(StringBuilder buf, DnsQuery msg) {
        buf.append(StringUtil.simpleClassName(msg))
           .append('(');

        appendAddresses(buf, msg)
           .append(msg.id())
           .append(", ")
           .append(msg.opCode());

        if (msg.isRecursionDesired()) {
            buf.append(", RD");
        }
        if (msg.z() != 0) {
            buf.append(", Z: ")
               .append(msg.z());
        }
        buf.append(')');
    }

    private static void appendResponseHeader(StringBuilder buf, DnsResponse msg) {
        buf.append(StringUtil.simpleClassName(msg))
           .append('(');

        appendAddresses(buf, msg)
           .append(msg.id())
           .append(", ")
           .append(msg.opCode())
           .append(", ")
           .append(msg.code())
           .append(',');

        boolean hasComma = true;
        if (msg.isRecursionDesired()) {
            hasComma = false;
            buf.append(" RD");
        }
        if (msg.isAuthoritativeAnswer()) {
            hasComma = false;
            buf.append(" AA");
        }
        if (msg.isTruncated()) {
            hasComma = false;
            buf.append(" TC");
        }
        if (msg.isRecursionAvailable()) {
            hasComma = false;
            buf.append(" RA");
        }
        if (msg.z() != 0) {
            if (!hasComma) {
                buf.append(',');
            }
            buf.append(" Z: ")
               .append(msg.z());
        }

        if (hasComma) {
            buf.setCharAt(buf.length() - 1, ')');
        } else {
            buf.append(')');
        }
    }

    private static StringBuilder appendAddresses(StringBuilder buf, DnsMessage msg) {

        if (!(msg instanceof AddressedEnvelope)) {
            return buf;
        }

        @SuppressWarnings("unchecked")
        AddressedEnvelope<?, SocketAddress> envelope = (AddressedEnvelope<?, SocketAddress>) msg;

        SocketAddress addr = envelope.sender();
        if (addr != null) {
            buf.append("from: ")
               .append(addr)
               .append(", ");
        }

        addr = envelope.recipient();
        if (addr != null) {
            buf.append("to: ")
               .append(addr)
               .append(", ");
        }

        return buf;
    }

    private static void appendAllRecords(StringBuilder buf, DnsMessage msg) {
        appendRecords(buf, msg, DnsSection.QUESTION);
        appendRecords(buf, msg, DnsSection.ANSWER);
        appendRecords(buf, msg, DnsSection.AUTHORITY);
        appendRecords(buf, msg, DnsSection.ADDITIONAL);
    }

    private static void appendRecords(StringBuilder buf, DnsMessage message, DnsSection section) {
        final int count = message.count(section);
        if (count == 0) {
            return;
        }
        buf.append(StringUtil.NEWLINE).append(StringUtil.TAB).append(section);
        for (int i = 0; i < count; i ++) {
            buf.append(StringUtil.NEWLINE)
               .append(StringUtil.TAB).append(StringUtil.TAB)
               .append(message.<DnsRecord>recordAt(section, i));
        }
    }

    public static void arrayToHexWithChars(StringBuilder into, byte[] b) {
        for (int i = 0; i < b.length; i++) {
            byte currentByte = b[i];
            if (i > 0) {
                into.append(' ');
            }
            appendFixedLengthHexOrChar(currentByte, into);
        }
    }

    private static void appendFixedLengthHexOrChar(byte value, StringBuilder into) {
        if (isPrintableChar(value)) {
            appendChar(value, into);
        } else {
            appendHex(value, into);
        }
    }

    public static void appendHex(byte value, StringBuilder into) {
        String hx = Integer.toHexString(value & 0xFF);
        into.append("0x");
        if (hx.length() == 1) {
            into.append('0');
        }
        into.append(hx);
    }

    private static void appendChar(byte value, StringBuilder into) {
        into.append('\'').append((char) value & 0xFF).append('\'');
    }

    private static boolean isPrintableChar(byte value) {
        return (value >= '0' && value <= '9')
                || (value >= 'A' && value <= 'Z')
                || (value >= 'a' && value <= 'z');
    }

    /**
     * Generate a usable hash code for a DNS name, which is the same
     * with or without trailing dots.
     */
    public static int nameHashCode(CharSequence name) {
        int result = 0;
        for (int i = name.length() - 1; i >= 0; i--) {
           char c = name.charAt(i);
           if ('.' == c) {
               continue;
           }
           result = 31 * result + Character.toLowerCase(c);
        }
        return result;
    }

    /**
     * Compare two DNS names for equality, ignoring trailing dots.
     */
    public static boolean namesEqual(CharSequence a, CharSequence b) {
        int endA = a.length();
        int endB = b.length();
        while (endA > 0) {
            if (a.charAt(endA - 1) != '.') {
                break;
            }
            endA--;
        }
        while (endB > 0) {
            if (b.charAt(endB - 1) != '.') {
                break;
            }
            endB--;
        }
        if (endB != endA) {
            return false;
        }
        for (int i = 0; i < endA; i++) {
            char ca = Character.toLowerCase(a.charAt(i));
            char cb = Character.toLowerCase(a.charAt(i));
            if (ca != cb) {
                return false;
            }
        }
        return true;
    }

    private DnsMessageUtil() { }
}
