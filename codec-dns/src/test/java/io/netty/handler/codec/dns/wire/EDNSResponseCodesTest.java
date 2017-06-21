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
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DatagramDnsResponse;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DnsMessage;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponseCode;
import static io.netty.handler.codec.dns.DnsSection.ADDITIONAL;
import io.netty.handler.codec.dns.names.NameCodec;
import io.netty.handler.codec.dns.server.DnsAnswerProvider;
import io.netty.handler.codec.dns.server.DnsResponder;
import io.netty.handler.codec.dns.server.DnsServerHandler;
import java.net.InetSocketAddress;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 * Tests encoding and decoding of extended response codes, which involves the
 * encoder adding or modifying an OPT record.
 */
public class EDNSResponseCodesTest {

    private final DnsMessageDecoder dec = DnsMessageDecoder.builder()
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

    @Test
    public void testInsertRecord() throws Exception {
        DatagramDnsResponse resp = DnsMessageEncoderTest.createResponse();
        resp.addRecord(ADDITIONAL, new DefaultDnsRawRecord("foo",
                DnsRecordType.OPT, 23, Unpooled.EMPTY_BUFFER));
        assertNotNull("Not added or in wrong section: " + resp, findOptRecord(resp));

        ByteBuf buf = Unpooled.buffer();
        enc.encode(resp, buf, NameCodec.compressingNameCodec(), 480);
        DatagramDnsResponse decoded = (DatagramDnsResponse) dec.decode(buf,
                InetSocketAddress.createUnresolved("localhost", 53),
                InetSocketAddress.createUnresolved("localhost", 5353));

        assertNotNull("Not added or in wrong section: " + decoded, findOptRecord(decoded));
    }

    @Test
    public void testEDNS() throws Exception {
        DatagramDnsResponse resp = DnsMessageEncoderTest.createResponse();
        assertNull("Original should not have an opt record", findOptRecord(resp));
        resp.setCode(DnsResponseCode.BADCOOKIE);
        assertEquals("Value not set", DnsResponseCode.BADCOOKIE, resp.code());
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
                DnsResponseCode.BADCOOKIE, decoded.code());
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

}
