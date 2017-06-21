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

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DatagramDnsResponse;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import static io.netty.handler.codec.dns.DnsClass.CLASS_CSNET;
import io.netty.handler.codec.dns.DnsMessage;
import io.netty.handler.codec.dns.DnsMessageFlags;
import static io.netty.handler.codec.dns.DnsMessageFlags.AUTHORITATIVE_ANSWER;
import static io.netty.handler.codec.dns.DnsOpCode.QUERY;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsRawRecord;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import static io.netty.handler.codec.dns.DnsRecordType.OPT;
import io.netty.handler.codec.dns.DnsResponseCode;
import static io.netty.handler.codec.dns.DnsResponseCode.BADALG;
import static io.netty.handler.codec.dns.DnsResponseCode.BADCOOKIE;
import static io.netty.handler.codec.dns.DnsResponseCode.BADMODE;
import static io.netty.handler.codec.dns.DnsSection.ADDITIONAL;
import io.netty.handler.codec.dns.names.NameCodec;
import io.netty.handler.codec.dns.server.DnsAnswerProvider;
import io.netty.handler.codec.dns.server.DnsResponder;
import io.netty.handler.codec.dns.server.DnsServerHandler;
import java.net.InetSocketAddress;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 * Tests encoding and decoding of extended response codes, which involves the
 * encoder adding or modifying an OPT record.
 */
public class EDNSResponseCodesTest {

    private final DnsMessageDecoder<DatagramDnsResponse> dec = DnsMessageDecoder.builder()
            .buildResponseDecoder();
    private final DnsMessageEncoder enc = DnsMessageEncoder.builder()
            .withIllegalRecordPolicy(IllegalRecordPolicy.INCLUDE)
            .withMaxPacketSize(576).build();

    public static void main(String[] args) {
        // Sets up a DNS server that always gives the same answer, for
        // some debugging purposes
        Bootstrap b = new Bootstrap();
        NioEventLoopGroup group = new NioEventLoopGroup();
        b.group(group)
                .channel(NioDatagramChannel.class)
                .handler(new DnsServerHandler(new DnsAnswerProvider() {
                    @Override
                    public void respond(DnsQuery query, ChannelHandlerContext ctx,
                            DnsResponder callback) throws Exception {
                        try {
                            DatagramDnsResponse resp = DnsMessageEncoderTest.createResponse();
                            resp.addRecord(ADDITIONAL, new DefaultDnsRawRecord(".",
                                    DnsRecordType.OPT, 0, Unpooled.EMPTY_BUFFER));
                            resp.setCode(DnsResponseCode.BADCOOKIE);
                            callback.withResponse(resp);
                        } catch (Exception e) {
                            e.printStackTrace();
                            System.exit(1);
                        }
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, DnsMessage message, Throwable cause) {
                        cause.printStackTrace();
                        System.exit(1);
                    }
                }));

        try {
            Channel channel = b.bind(5053).sync().channel();

            ChannelPipeline pipeline = channel.pipeline();
            pipeline.addFirst("decoder", DnsMessageDecoder.builder().buildUdpQueryDecoder());
            pipeline.addLast("encoder", DnsMessageEncoder.builder()
                    .withIllegalRecordPolicy(IllegalRecordPolicy.DISCARD)
                    .withMaxPacketSize(4096).buildUdpResponseEncoder());

            // XXX test code
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static final InetSocketAddress SENDER = InetSocketAddress.createUnresolved("localhost", 53);
    private static final InetSocketAddress RECIPIENT = InetSocketAddress.createUnresolved("localhost", 6353);

    @Test
    public void testInsertRecord() throws Exception {
        DatagramDnsResponse resp = DnsMessageEncoderTest.createResponse();
        resp.addRecord(ADDITIONAL, new DefaultDnsRawRecord("foo",
                DnsRecordType.OPT, 23, Unpooled.EMPTY_BUFFER));
        assertNotNull("Not added or in wrong section: " + resp, findOptRecord(resp));

        ByteBuf buf = Unpooled.buffer();
        enc.encode(resp, buf, NameCodec.compressingNameCodec(), 576);
        DatagramDnsResponse decoded = (DatagramDnsResponse) dec.decode(buf, SENDER, RECIPIENT);

        assertNotNull("Not added or in wrong section: " + decoded, findOptRecord(decoded));
    }

    @Test
    public void testEDNS() throws Exception {
        DatagramDnsResponse resp = DnsMessageEncoderTest.createResponse();
        assertNull("Original should not have an opt record", findOptRecord(resp));
        resp.setCode(DnsResponseCode.BADCOOKIE);
        assertEquals("Value not set", BADCOOKIE, resp.code());
        ByteBuf buf = Unpooled.buffer();
        enc.encode(resp, buf, NameCodec.compressingNameCodec(), 576);
        DatagramDnsResponse decoded = (DatagramDnsResponse) dec.decode(buf,
                InetSocketAddress.createUnresolved("localhost", 53),
                InetSocketAddress.createUnresolved("localhost", 5353));

        DnsRecord opt = findOptRecord(decoded);
        assertNotNull("No opt record in " + decoded, opt);
        assertEquals(576, opt.dnsClassValue());

        assertEquals("Response code encoded incorrectly: "
                + decoded.code(),
                BADCOOKIE, decoded.code());
    }

    private DnsRecord findOptRecord(DnsMessage<?> msg) {
        int max = msg.count(ADDITIONAL);
        for (int i = 0; i < max; i++) {
            DnsRecord rec = msg.recordAt(ADDITIONAL, i);
            if (DnsRecordType.OPT.equals(rec.type())) {
                return rec;
            }
        }
        return null;
    }

    @Test
    public void testEDNSResponseCodeOnExistingOptRecord() throws Exception {
        DatagramDnsResponse resp = new DatagramDnsResponse(SENDER, RECIPIENT, 5231, QUERY,
                DnsResponseCode.valueOf(0), DnsMessageFlags.setOf(false, AUTHORITATIVE_ANSWER));

        DnsRawRecord raw = new DefaultDnsRawRecord("foo.example", OPT, CLASS_CSNET, 329238101834L, EMPTY_BUFFER);
        resp.addRecord(ADDITIONAL, raw);

        resp.setCode(BADALG);

        ByteBuf buf = Unpooled.buffer();
        enc.encode(resp, buf, NameCodec.compressingNameCodec(), 1024);

        DatagramDnsResponse found = dec.decode(buf, SENDER, RECIPIENT);
        assertEquals(found.code(), BADALG);

        DnsRecord rec = found.recordAt(ADDITIONAL);
        assertNotNull(rec);
        assertEquals("Encoder writing UDP payload size should not clobber OPT record's"
                + " dns class value if it is non-zero", rec.dnsClassValue(), CLASS_CSNET);

        resp = new DatagramDnsResponse(SENDER, RECIPIENT, 5232, QUERY,
                DnsResponseCode.valueOf(0), DnsMessageFlags.setOf(false, AUTHORITATIVE_ANSWER));

        raw = new DefaultDnsRawRecord("foo.example", OPT, 0, Long.MAX_VALUE, EMPTY_BUFFER);
        resp.addRecord(ADDITIONAL, raw);

        resp.setCode(BADMODE);
        buf = Unpooled.buffer();
        enc.encode(resp, buf, NameCodec.compressingNameCodec(), 1024);

        found = dec.decode(buf, SENDER, RECIPIENT);
        assertEquals(found.code(), BADMODE);

        rec = found.recordAt(ADDITIONAL);
        assertNotNull(rec);
        assertNotEquals("Encoder writing UDP payload size should overwrite OPT record's"
                + " dns class value if it is zero", rec.dnsClassValue(), 0);
        assertEquals("Encoder writing UDP payload size should overwrite OPT record's"
                + " dns class value if it is zero", rec.dnsClassValue(), 576);
    }

}
