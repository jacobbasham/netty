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

import io.netty.handler.codec.dns.names.NameCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import static io.netty.handler.codec.dns.DefaultDnsRecordEncoderTest.assertCharsEqual;
import static io.netty.handler.codec.dns.names.NameCodecFeature.COMPRESSION;
import static io.netty.handler.codec.dns.names.NameCodecFeature.MDNS_UTF_8;
import static io.netty.handler.codec.dns.names.NameCodecFeature.PUNYCODE;
import static io.netty.handler.codec.dns.names.NameCodecFeature.READ_TRAILING_DOT;
import static io.netty.handler.codec.dns.names.NameCodecFeature.WRITE_TRAILING_DOT;
import io.netty.util.internal.StringUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DefaultDnsRecordDecoderTest {

    @Test
    public void testDecodeName() throws Exception {
        testDecodeName("netty.io.", Unpooled.wrappedBuffer(new byte[]{
            5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0
        }));
    }

    @Test
    public void testDecodeNameWithoutTerminator() throws Exception {
        testDecodeName("netty.io.", Unpooled.wrappedBuffer(new byte[]{
            5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o'
        }));
    }

    @Test
    public void testDecodeNameWithExtraTerminator() throws Exception {
        // Should not be decoded as 'netty.io..'
        testDecodeName("netty.io.", Unpooled.wrappedBuffer(new byte[]{
            5, 'n', 'e', 't', 't', 'y', 2, 'i', 'o', 0, 0
        }));
    }

    @Test
    public void testDecodeEmptyName() throws Exception {
        testDecodeName(".", Unpooled.buffer().writeByte(0));
    }

    @Test
    public void testDecodeEmptyNameFromEmptyBuffer() throws Exception {
        testDecodeName(".", Unpooled.EMPTY_BUFFER);
    }

    @Test
    public void testDecodeEmptyNameFromExtraZeroes() throws Exception {
        testDecodeName(".", Unpooled.wrappedBuffer(new byte[]{0, 0}));
    }

    private static void testDecodeName(String expected, ByteBuf buffer) throws DnsDecoderException {
        try {
            int ix = buffer.readerIndex();
            NameCodec codec = NameCodec.get(COMPRESSION, READ_TRAILING_DOT, WRITE_TRAILING_DOT);
            CharSequence decoded = codec.readName(buffer);
            assertTrue(decoded + " expected " + expected, StringUtil.charSequencesEqual(expected, decoded, false));

            buffer.readerIndex(ix);
            codec = NameCodec.get(READ_TRAILING_DOT, WRITE_TRAILING_DOT);
            decoded = codec.readName(buffer);
            assertTrue(decoded + " expected " + expected, StringUtil.charSequencesEqual(expected, decoded, false));

            buffer.readerIndex(ix);
            codec = NameCodec.get(PUNYCODE, READ_TRAILING_DOT, WRITE_TRAILING_DOT);
            decoded = codec.readName(buffer);
            assertTrue(decoded + " expected " + expected, StringUtil.charSequencesEqual(expected, decoded, false));

            buffer.readerIndex(ix);
            codec = NameCodec.get(MDNS_UTF_8, READ_TRAILING_DOT, WRITE_TRAILING_DOT);
            decoded = codec.readName(buffer);
            assertTrue(decoded + " expected " + expected, StringUtil.charSequencesEqual(expected, decoded, false));
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testDecodePtrRecord() throws Exception {
        DefaultDnsRecordDecoder decoder = new DefaultDnsRecordDecoder();
        ByteBuf buffer = Unpooled.buffer().writeByte(0);
        try {
            DnsPtrRecord record = (DnsPtrRecord) decoder.decodeRecord("netty.io.",
                    DnsRecordType.PTR, DnsClass.IN.intValue(), 60, buffer, 1,
                    NameCodec.get(COMPRESSION, READ_TRAILING_DOT, WRITE_TRAILING_DOT));
            assertCharsEqual("netty.io.", record.name());
            assertEquals(DnsClass.IN, record.dnsClass());
            assertEquals(60, record.timeToLive());
            assertEquals(DnsRecordType.PTR, record.type());
            assertEquals(1, buffer.readerIndex());
            assertEquals(1, buffer.writerIndex());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void testDecodeMessageCompression() throws Exception {
        // See https://www.ietf.org/rfc/rfc1035 [4.1.4. Message compression]
        DefaultDnsRecordDecoder decoder = new DefaultDnsRecordDecoder();
        byte[] rfcExample = new byte[]{1, 'F', 3, 'I', 'S', 'I', 4, 'A', 'R', 'P', 'A',
            0, 3, 'F', 'O', 'O',
            (byte) 0xC0, 0, // this is 20 in the example
            (byte) 0xC0, 6, // this is 26 in the example
    };
        DefaultDnsRawRecord rawPlainRecord = null;
        DefaultDnsRawRecord rawUncompressedRecord = null;
        DefaultDnsRawRecord rawUncompressedIndexedRecord = null;
        ByteBuf buffer = Unpooled.wrappedBuffer(rfcExample);
        try {
            // First lets test that our utility funciton can correctly handle index references and decompression.
            CharSequence plainName = NameCodec.get(COMPRESSION, READ_TRAILING_DOT, WRITE_TRAILING_DOT)
                    .readName(buffer.duplicate());
            assertCharsEqual("F.ISI.ARPA.", plainName);
            CharSequence uncompressedPlainName = NameCodec.get(COMPRESSION, READ_TRAILING_DOT, WRITE_TRAILING_DOT)
                    .readName(buffer.duplicate().setIndex(16, 20));
            assertCharsEqual(plainName, uncompressedPlainName);
            CharSequence uncompressedIndexedName = NameCodec.get(COMPRESSION, READ_TRAILING_DOT, WRITE_TRAILING_DOT)
                    .readName(buffer.duplicate().setIndex(12, 20));
            assertCharsEqual("FOO." + plainName, uncompressedIndexedName);

            // Now lets make sure out object parsing produces the same results for non PTR type (just use CNAME).
            rawPlainRecord = (DefaultDnsRawRecord) decoder.decodeRecord(
                    plainName, DnsRecordType.CNAME, DnsClass.IN.intValue(), 60, buffer.duplicate(), 11,
                    NameCodec.get(COMPRESSION, READ_TRAILING_DOT, WRITE_TRAILING_DOT));
            assertCharsEqual(plainName, rawPlainRecord.name());
            assertCharsEqual(plainName, NameCodec.get(COMPRESSION, READ_TRAILING_DOT, WRITE_TRAILING_DOT)
                    .readName(rawPlainRecord.content()));

            rawUncompressedRecord = (DefaultDnsRawRecord) decoder.decodeRecord(
                    uncompressedPlainName, DnsRecordType.CNAME, DnsClass.CLASS_IN, 60,
                    buffer.duplicate(), 4,
                    NameCodec.get(COMPRESSION, READ_TRAILING_DOT, WRITE_TRAILING_DOT));
            assertCharsEqual(uncompressedPlainName, rawUncompressedRecord.name());
            assertCharsEqual(uncompressedPlainName, NameCodec.get(COMPRESSION, READ_TRAILING_DOT, WRITE_TRAILING_DOT)
                    .readName(rawUncompressedRecord.content()));

            NameCodec nc = NameCodec.get(READ_TRAILING_DOT, WRITE_TRAILING_DOT);
            rawUncompressedIndexedRecord = (DefaultDnsRawRecord) decoder.decodeRecord(
                    uncompressedIndexedName, DnsRecordType.CNAME, DnsClass.CLASS_IN, 60,
                    buffer.duplicate().slice(12, 8), 8, nc);
            assertCharsEqual(uncompressedIndexedName, rawUncompressedIndexedRecord.name());

            // Now lets make sure out object parsing produces the same results for PTR type.
            DnsPtrRecord ptrRecord = (DnsPtrRecord) decoder.decodeRecord(
                    plainName, DnsRecordType.PTR, DnsClass.CLASS_IN, 60, buffer.duplicate(), 11,
                    NameCodec.get(COMPRESSION, READ_TRAILING_DOT, WRITE_TRAILING_DOT));
            assertCharsEqual(plainName, ptrRecord.name());
            assertCharsEqual(plainName, ptrRecord.hostname());

            ptrRecord = (DnsPtrRecord) decoder.decodeRecord(
                    uncompressedPlainName, DnsRecordType.PTR, DnsClass.CLASS_IN, 60, buffer.duplicate(), 4,
                    NameCodec.get(COMPRESSION, READ_TRAILING_DOT, WRITE_TRAILING_DOT));

            assertCharsEqual(uncompressedPlainName, ptrRecord.name());
            assertCharsEqual(uncompressedPlainName, ptrRecord.hostname());

            ptrRecord = (DnsPtrRecord) decoder.decodeRecord(
                    uncompressedIndexedName, DnsRecordType.PTR, DnsClass.CLASS_IN, 60,
                    buffer.duplicate().setIndex(12, 20), 8,
                    NameCodec.get(COMPRESSION, READ_TRAILING_DOT, WRITE_TRAILING_DOT));
            assertCharsEqual(uncompressedIndexedName, ptrRecord.name());
            assertCharsEqual(uncompressedIndexedName, ptrRecord.hostname());
        } finally {
            if (rawPlainRecord != null) {
                rawPlainRecord.release();
            }
            if (rawUncompressedRecord != null) {
                rawUncompressedRecord.release();
            }
            if (rawUncompressedIndexedRecord != null) {
                rawUncompressedIndexedRecord.release();
            }
            buffer.release();
        }
    }
}
