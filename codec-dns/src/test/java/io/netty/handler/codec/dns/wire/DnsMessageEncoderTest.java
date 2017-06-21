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
import io.netty.buffer.Unpooled;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.DefaultAddressedEnvelope;
import io.netty.handler.codec.dns.DatagramDnsResponse;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.handler.codec.dns.names.NameCodec;
import io.netty.handler.codec.dns.names.NameCodecFeature;
import io.netty.util.internal.StringUtil;
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

    static DatagramDnsResponse createResponse() throws Exception {
        DnsMessageDecoder<DatagramDnsResponse> dec = DnsMessageDecoder.builder()
                .withNameFeatures(NameCodecFeature.WRITE_TRAILING_DOT).buildResponseDecoder();
        InetSocketAddress sender = InetSocketAddress.createUnresolved("localhost", 9053);
        InetSocketAddress recipient = InetSocketAddress.createUnresolved("localhost", 53);
        DatagramDnsResponse resp = dec.decode(Unpooled.wrappedBuffer(PACKET), sender, recipient);
        return resp;
    }

    @Test
    public void testEncodeDecode() throws Exception {
        DnsMessageEncoder enc = DnsMessageEncoder.builder().build();
        DnsMessageDecoder<DatagramDnsResponse> dec = DnsMessageDecoder.builder()
                .withNameFeatures(NameCodecFeature.WRITE_TRAILING_DOT).buildResponseDecoder();

        DefaultDnsResponse expect = createResponse();
        InetSocketAddress sender = InetSocketAddress.createUnresolved("localhost", 9053);
        InetSocketAddress recipient = InetSocketAddress.createUnresolved("localhost", 53);

        AddressedEnvelope<DefaultDnsResponse, InetSocketAddress> env
                = new DefaultAddressedEnvelope<DefaultDnsResponse, InetSocketAddress>(expect, sender, recipient);

        ByteBuf buf = Unpooled.buffer();
        enc.encode(expect, buf, NameCodec.basicNameCodec(), 4096);

        DnsResponse got = dec.decode(buf, sender, recipient);
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
}
