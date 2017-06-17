/*
 * Copyright 2016 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.socket.InternetProtocolFamily;
import static io.netty.handler.codec.dns.NameCodec.Feature.COMPRESSION;
import static io.netty.handler.codec.dns.NameCodec.Feature.READ_TRAILING_DOT;
import static io.netty.handler.codec.dns.NameCodec.Feature.WRITE_TRAILING_DOT;
import io.netty.util.AsciiString;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SocketUtils;
import io.netty.util.internal.StringUtil;
import static io.netty.util.internal.StringUtil.charSequencesEqual;
import io.netty.util.internal.ThreadLocalRandom;
import org.junit.Test;

import java.net.InetAddress;
import org.junit.Assert;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DefaultDnsRecordEncoderTest {

    @Test
    public void testEncodeName() throws Exception {
        testEncodeName(new byte[] { 5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0 }, "netty.io.");
    }

    @Test
    public void testEncodeNameWithoutTerminator() throws Exception {
        testEncodeName(new byte[] { 5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0 }, "netty.io");
    }

    @Test
    public void testEncodeNameWithExtraTerminator() throws Exception {
        testEncodeName(new byte[] { 5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0 }, "netty.io..");
    }

    // Test for https://github.com/netty/netty/issues/5014
    @Test
    public void testEncodeEmptyName() throws Exception {
        testEncodeName(new byte[] { 0 }, StringUtil.EMPTY_STRING);
    }

    @Test
    public void testEncodeRootName() throws Exception {
        testEncodeName(new byte[] { 0 }, ".");
    }

    private static void testEncodeName(byte[] expected, String name) throws Exception {
        ByteBuf out = Unpooled.buffer();
        ByteBuf expectedBuf = Unpooled.wrappedBuffer(expected);
        try {
            NameCodec.get(COMPRESSION, READ_TRAILING_DOT, WRITE_TRAILING_DOT).writeName(name, out);
            assertBuffersEqual(bufferComparison(expectedBuf, out, name), expectedBuf, out);
        } finally {
            out.release();
            expectedBuf.release();
        }
    }

    private static void assertBuffersEqual(CharSequence msg, ByteBuf expected, ByteBuf got) {
        // Avoids comparing the capacity of th ebuffers, which may differ
        byte[] expBytes = new byte[expected.readableBytes()];
        byte[] gotBytes = new byte[got.readableBytes()];
        expected.readBytes(expBytes);
        got.readBytes(gotBytes);
        Assert.assertArrayEquals(msg.toString(), expBytes, gotBytes);
    }

    private static String bufferComparison(ByteBuf a, ByteBuf b, String orig) {
        StringBuilder sb = new StringBuilder("Expected for '").append(orig)
                .append("' - '").append(new AsciiString(a.array(), 0,
                a.readableBytes(), true)).append("' got '")
                .append(new AsciiString(b.array(), 0, b.readableBytes(), true))
                .append("'\nEXPECTED:\n");
        ByteBufUtil.appendPrettyHexDump(sb, a, 0, a.readableBytes());
        sb.append("\nGOT:\n");
        ByteBufUtil.appendPrettyHexDump(sb, b, 0, b.readableBytes());
        sb.append('\n');
        return sb.toString();
    }

    @Test
    public void testOptEcsRecordIpv4() throws Exception {
        testOptEcsRecordIp(SocketUtils.addressByName("1.2.3.4"));
    }

    @Test
    public void testOptEcsRecordIpv6() throws Exception {
        testOptEcsRecordIp(SocketUtils.addressByName("::0"));
    }

    private static void testOptEcsRecordIp(InetAddress address) throws Exception {
        int addressBits = address.getAddress().length * Byte.SIZE;
        for (int i = 0; i <= addressBits; ++i) {
            testIp(address, i);
        }
    }

    static void assertCharsEqual(CharSequence expected, CharSequence got) {
        String msg = "Expected '" + expected + "' but got '" + got + "'";
        assertTrue(msg, charSequencesEqual(expected, got, false));
    }

    private static void testIp(InetAddress address, int prefix) throws Exception {
        int lowOrderBitsToPreserve = prefix % Byte.SIZE;

        ByteBuf addressPart = Unpooled.wrappedBuffer(address.getAddress(), 0,
                DefaultDnsRecordEncoder.calculateEcsAddressLength(prefix, lowOrderBitsToPreserve));

        if (lowOrderBitsToPreserve > 0) {
            // Pad the leftover of the last byte with zeros.
            int idx = addressPart.writerIndex() - 1;
            byte lastByte = addressPart.getByte(idx);
            addressPart.setByte(idx, DefaultDnsRecordEncoder.padWithZeros(lastByte, lowOrderBitsToPreserve));
        }

        int payloadSize = nextInt(Short.MAX_VALUE);
        int extendedRcode = nextInt(Byte.MAX_VALUE * 2); // Unsigned
        int version = nextInt(Byte.MAX_VALUE * 2); // Unsigned

        DefaultDnsRecordEncoder encoder = new DefaultDnsRecordEncoder();
        ByteBuf out = Unpooled.buffer();
        try {
            DnsOptEcsRecord record = new DefaultDnsOptEcsRecord(
                    payloadSize, extendedRcode, version, prefix, address.getAddress());
            encoder.encodeRecord(NameCodec.get(COMPRESSION, READ_TRAILING_DOT, WRITE_TRAILING_DOT),
                    record, out, Integer.MAX_VALUE);

            assertEquals(0, out.readByte()); // Name
            assertEquals(DnsRecordType.OPT.intValue(), out.readUnsignedShort()); // Opt
            assertEquals(payloadSize, out.readUnsignedShort()); // payload
            assertEquals(record.timeToLive(), out.getUnsignedInt(out.readerIndex()));

            // Read unpacked TTL.
            assertEquals(extendedRcode, out.readUnsignedByte());
            assertEquals(version, out.readUnsignedByte());
            assertEquals(extendedRcode, record.extendedRcode());
            assertEquals(version, record.version());
            assertEquals(0, record.flags());

            assertEquals(0, out.readShort());

            int payloadLength = out.readUnsignedShort();
            assertEquals(payloadLength, out.readableBytes());

            assertEquals(8, out.readShort()); // As defined by RFC.

            int rdataLength = out.readUnsignedShort();
            assertEquals(rdataLength, out.readableBytes());

            assertEquals((short) InternetProtocolFamily.of(address).addressNumber(), out.readShort());

            assertEquals(prefix, out.readUnsignedByte());
            assertEquals(0, out.readUnsignedByte()); // This must be 0 for requests.
            assertEquals(addressPart, out);
        } finally {
            addressPart.release();
            out.release();
        }
    }

    private static int nextInt(int max) {
        return PlatformDependent.threadLocalRandom().nextInt(max);
    }
}
