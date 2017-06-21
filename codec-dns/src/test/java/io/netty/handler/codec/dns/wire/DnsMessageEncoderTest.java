/*
 * Copyright 2017 The Netty Project
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
package io.netty.handler.codec.dns.wire;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.handler.codec.dns.DatagramDnsResponse;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DefaultDnsRecordDecoder;
import io.netty.handler.codec.dns.DefaultDnsRecordEncoder;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsClass;
import io.netty.handler.codec.dns.DnsMessageFlags;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordDecoder.UnderflowPolicy;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.handler.codec.dns.names.NameCodec;
import io.netty.handler.codec.dns.names.NameCodecFeature;
import io.netty.util.internal.StringUtil;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import org.junit.Test;
import static org.junit.Assert.*;

public class DnsMessageEncoderTest {

    private static final byte[] PACKET = new byte[]{
        -105, 19, -127, 0, 0, 1, 0, 0, 0, 13, 0, 0, 2, 104, 112, 11, 116, 105, 109, 98, 111, 117, 100, 114,
        101, 97, 117, 3, 111, 114, 103, 0, 0, 1, 0, 1, 0, 0, 2, 0, 1, 0, 7, -23, 0, 0, 20, 1, 68, 12, 82,
        79, 79, 84, 45, 83, 69, 82, 86, 69, 82, 83, 3, 78, 69, 84, 0, 0, 0, 2, 0, 1, 0, 7, -23, 0, 0, 4, 1,
        70, -64, 49, 0, 0, 2, 0, 1, 0, 7, -23, 0, 0, 4, 1, 69, -64, 49, 0, 0, 2, 0, 1, 0, 7, -23, 0, 0, 4,
        1, 75, -64, 49, 0, 0, 2, 0, 1, 0, 7, -23, 0, 0, 4, 1, 67, -64, 49, 0, 0, 2, 0, 1, 0, 7, -23, 0, 0,
        4, 1, 76, -64, 49, 0, 0, 2, 0, 1, 0, 7, -23, 0, 0, 4, 1, 71, -64, 49, 0, 0, 2, 0, 1, 0, 7, -23, 0,
        0, 4, 1, 73, -64, 49, 0, 0, 2, 0, 1, 0, 7, -23, 0, 0, 4, 1, 66, -64, 49, 0, 0, 2, 0, 1, 0, 7, -23,
        0, 0, 4, 1, 77, -64, 49, 0, 0, 2, 0, 1, 0, 7, -23, 0, 0, 4, 1, 65, -64, 49, 0, 0, 2, 0, 1, 0, 7,
        -23, 0, 0, 4, 1, 72, -64, 49, 0, 0, 2, 0, 1, 0, 7, -23, 0, 0, 4, 1, 74, -64, 49
    };

    private static final InetSocketAddress SENDER = InetSocketAddress.createUnresolved("localhost", 9053);
    private static final InetSocketAddress RECIPIENT = InetSocketAddress.createUnresolved("localhost", 53);

    static DatagramDnsResponse createResponse() throws Exception {
        DnsMessageDecoder<DatagramDnsResponse> dec = DnsMessageDecoder.builder()
                .withNameFeatures(NameCodecFeature.WRITE_TRAILING_DOT).buildResponseDecoder();
        DatagramDnsResponse resp = dec.decode(Unpooled.wrappedBuffer(PACKET), SENDER, RECIPIENT);
        return resp;
    }

    @Test
    public void testEncodeDecode() throws Exception {
        DnsMessageEncoder enc = DnsMessageEncoder.builder().build();
        DnsMessageDecoder<DatagramDnsResponse> dec = DnsMessageDecoder.builder()
                .withNameFeatures(NameCodecFeature.WRITE_TRAILING_DOT).buildResponseDecoder();

        DefaultDnsResponse expect = createResponse();

        AddressedEnvelope<DefaultDnsResponse, InetSocketAddress> env
                = new DefaultAddressedEnvelope<DefaultDnsResponse, InetSocketAddress>(expect, SENDER, RECIPIENT);

        ByteBuf buf = Unpooled.buffer();
        enc.encode(expect, buf, NameCodec.basicNameCodec(), 4096);

        DnsResponse got = dec.decode(buf, SENDER, RECIPIENT);
        for (DnsSection sect : DnsSection.values()) {
            int expectedCount = expect.count(sect);
            int gotCount = got.count(sect);
            for (int i = 0; i < Math.min(expectedCount, gotCount); i++) {
                DnsRecord e = expect.recordAt(sect, i);
                DnsRecord g = got.recordAt(sect, i);
                assertEquals(e.dnsClassValue(), g.dnsClassValue());
                assertTrue(StringUtil.charSequencesEqual(e.name(), g.name(), false));
                assertEquals(e.type(), g.type());
                assertEquals(e.timeToLive(), g.timeToLive());
            }
            assertEquals("Counts differ in section " + sect, expectedCount, gotCount);
        }
        assertEquals(expect.id(), got.id());
        assertEquals(expect.flags(), got.flags());
        assertEquals(expect.opCode(), got.opCode());
        assertEquals(expect.code(), got.code());
        assertEquals(expect, got);
    }

    @Test
    public void testMdnsFieldsEncodedAndDecoded() throws Exception {
        DnsMessageEncoder enc = DnsMessageEncoder.builder()
                .mDNS()
                .withNameFeatures(NameCodecFeature.MDNS_UTF_8,
                        NameCodecFeature.WRITE_TRAILING_DOT)
                .withRecordEncoder(new DefaultDnsRecordEncoder(true))
                .build();

        DnsMessageDecoder<DatagramDnsResponse> dec = DnsMessageDecoder.builder()
                .withRecordDecoder(new DefaultDnsRecordDecoder(UnderflowPolicy.THROW_ON_UNDERFLOW, true))
                .withNameFeatures(NameCodecFeature.WRITE_TRAILING_DOT,
                        NameCodecFeature.MDNS_UTF_8)
                .buildResponseDecoder();

        DatagramDnsResponse resp = new DatagramDnsResponse(SENDER, RECIPIENT, 2023, DnsOpCode.QUERY,
                DnsResponseCode.NOERROR, DnsMessageFlags.setOf(false,
                        DnsMessageFlags.AUTHORITATIVE_ANSWER,
                        DnsMessageFlags.IS_REPLY,
                        DnsMessageFlags.RECURSION_AVAILABLE,
                        DnsMessageFlags.RECURSION_DESIRED));

        DefaultDnsQuestion unicastQuestion = new DefaultDnsQuestion("foo.com",
                DnsRecordType.A, DnsClass.IN, true);

        // Put a proper IP address in it
        ByteBuf buf = Unpooled.buffer()
                .writeBytes(Inet4Address.getByName("127.0.0.3").getAddress());

        DefaultDnsRawRecord unicastAnswer = new DefaultDnsRawRecord("foo.com",
                DnsRecordType.A, DnsClass.IN.intValue(), 82000, buf, true);
        resp.addRecord(DnsSection.QUESTION, unicastQuestion);
        resp.addRecord(DnsSection.ANSWER, unicastAnswer);

        ByteBuf encoded = Unpooled.buffer();
        enc.encode(resp, encoded, NameCodec.mdnsNameCodec(), 1024);
        System.out.println("ENCODED TO " + encoded.readableBytes() + " bytes "
                + ByteBufUtil.hexDump(encoded, 0, encoded.readableBytes()));

        DnsResponse decoded = dec.decode(encoded, SENDER, RECIPIENT);
        assertEquals(1, decoded.count(DnsSection.QUESTION));
        assertEquals(1, decoded.count(DnsSection.ANSWER));
        assertEquals(2023, decoded.id());
        assertTrue(decoded.flags().contains(DnsMessageFlags.AUTHORITATIVE_ANSWER));
        assertTrue(decoded.flags().contains(DnsMessageFlags.RECURSION_AVAILABLE));
        assertTrue(decoded.flags().contains(DnsMessageFlags.RECURSION_DESIRED));
        assertTrue(decoded.flags().contains(DnsMessageFlags.IS_REPLY));

        DnsQuestion question = (DnsQuestion) decoded.recordAt(DnsSection.QUESTION);
        DnsRecord record = decoded.recordAt(DnsSection.ANSWER);

        assertEquals("foo.com", question.name().toString());

        assertTrue(question.isUnicast());
        assertTrue(record.isUnicast());

        assertEquals(DnsClass.IN, question.dnsClass());
        assertEquals(DnsRecordType.A, question.type());
        assertEquals(DnsRecordType.A, record.type());
        assertEquals(82000, record.timeToLive());

        // Now make sure we don't read or write these if we are not using
        // mDNS enabled encoders or decoders
        DnsMessageEncoder nonMdnsEnc = DnsMessageEncoder.builder()
                .withRecordEncoder(new DefaultDnsRecordEncoder(false)).build();
        DnsMessageDecoder<DatagramDnsResponse> nonMdnsDec = DnsMessageDecoder.builder()
                .withRecordDecoder(new DefaultDnsRecordDecoder(UnderflowPolicy.THROW_ON_UNDERFLOW, false))
                .withNameFeatures(NameCodecFeature.WRITE_TRAILING_DOT).buildResponseDecoder();

        buf.resetReaderIndex();
        encoded = Unpooled.buffer();
        nonMdnsEnc.encode(resp, encoded, NameCodec.basicNameCodec(), 1024);
        decoded = nonMdnsDec.decode(encoded, SENDER, RECIPIENT);
        question = (DnsQuestion) decoded.recordAt(DnsSection.QUESTION);
        record = decoded.recordAt(DnsSection.ANSWER);
        assertFalse(question.isUnicast());
        assertFalse(record.isUnicast());

        // And make sure these aren't set if they aren't actually present
        // when we ARE using MDNS-enabled encoders and decoders
        resp = new DatagramDnsResponse(SENDER, RECIPIENT, 7124, DnsOpCode.QUERY,
                DnsResponseCode.NOERROR, DnsMessageFlags.setOf(false,
                        DnsMessageFlags.AUTHORITATIVE_ANSWER,
                        DnsMessageFlags.IS_REPLY,
                        DnsMessageFlags.RECURSION_AVAILABLE,
                        DnsMessageFlags.RECURSION_DESIRED));

        DefaultDnsQuestion nonMdnsQuestion = new DefaultDnsQuestion("foo.com",
                DnsRecordType.A, DnsClass.IN);

        buf = Unpooled.buffer().writeBytes(Inet4Address.getByName("127.0.0.4").getAddress());

        DefaultDnsRawRecord nonMdnsAnswer = new DefaultDnsRawRecord("foo.com", DnsRecordType.A,
                DnsClass.IN.intValue(), 82000, buf);
        resp.addRecord(DnsSection.QUESTION, nonMdnsQuestion);
        resp.addRecord(DnsSection.ANSWER, nonMdnsAnswer);

        encoded = Unpooled.buffer();
        enc.encode(resp, encoded, NameCodec.mdnsNameCodec(), 1024);

        decoded = dec.decode(encoded, SENDER, RECIPIENT);
        question = (DnsQuestion) decoded.recordAt(DnsSection.QUESTION);
        record = decoded.recordAt(DnsSection.ANSWER);

        assertEquals("foo.com", question.name().toString());
        assertEquals(7124, decoded.id());
        assertEquals(DnsClass.IN, question.dnsClass());
        assertEquals(DnsRecordType.A, question.type());
        assertEquals(DnsRecordType.A, record.type());
        assertEquals(82000, record.timeToLive());
        assertTrue(decoded.flags().contains(DnsMessageFlags.AUTHORITATIVE_ANSWER));
        assertTrue(decoded.flags().contains(DnsMessageFlags.IS_REPLY));
        assertTrue(decoded.flags().contains(DnsMessageFlags.RECURSION_AVAILABLE));
        assertTrue(decoded.flags().contains(DnsMessageFlags.RECURSION_DESIRED));

        assertFalse(question.isUnicast());
        assertFalse(record.isUnicast());

        // And make sure we can encode unicode if we built an encoder with
        // mDNS on
        String name = "přehřátých.cz";
        buf.resetReaderIndex();
        unicastQuestion = new DefaultDnsQuestion(name,
                DnsRecordType.A, DnsClass.HESIOD, true);

        buf = Unpooled.buffer()
                .writeBytes(Inet4Address.getByName("127.0.0.6").getAddress());

        unicastAnswer = new DefaultDnsRawRecord(name,
                DnsRecordType.A, DnsClass.HESIOD.intValue(), 82000, buf, true);

        resp = new DatagramDnsResponse(SENDER, RECIPIENT, 3905, DnsOpCode.QUERY,
                DnsResponseCode.NOERROR, DnsMessageFlags.setOf(false,
                        DnsMessageFlags.RECURSION_DESIRED));

        resp.addRecord(DnsSection.QUESTION, unicastQuestion);
        resp.addRecord(DnsSection.ANSWER, unicastAnswer);

        encoded = Unpooled.buffer();
        enc.encode(resp, encoded, NameCodec.mdnsNameCodec(), 1024);

        decoded = dec.decode(encoded, SENDER, RECIPIENT);
        question = (DnsQuestion) decoded.recordAt(DnsSection.QUESTION);
        record = decoded.recordAt(DnsSection.ANSWER);

        assertEquals(name, question.name());
        assertEquals(name, record.name());
        assertTrue(question.isUnicast());
        assertTrue(record.isUnicast());
        assertFalse(decoded.flags().contains(DnsMessageFlags.AUTHORITATIVE_ANSWER));
        assertTrue("Response decoder should automatically set IS_REPLY on responses",
                decoded.flags().contains(DnsMessageFlags.IS_REPLY));
        assertFalse(decoded.flags().contains(DnsMessageFlags.RECURSION_AVAILABLE));
        assertTrue(decoded.flags().contains(DnsMessageFlags.RECURSION_DESIRED));
    }
}
