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
package io.netty.handler.codec.dns.server;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.dns.DnsDecoderException;
import io.netty.handler.codec.dns.DatagramDnsQuery;
import io.netty.handler.codec.dns.DatagramDnsResponse;
import io.netty.handler.codec.dns.DnsMessageFlags;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.util.internal.ObjectUtil;

/**
 * Handles some of the plumbing of writing a DNS server. To use, simply
 * implement DnsAnswerProvider and pass it to the constructor.
 *
 * <pre>
 *        NioEventLoopGroup group = new NioEventLoopGroup(4); // 4 threads
 *
 *        Bootstrap b = new Bootstrap();
 *        b.group(group)
 *                .channel(NioDatagramChannel.class)
 *                .handler(new DnsServerHandler(new AnswerProviderImpl()));
 *
 *        Channel channel = b.bind(5753).sync().channel();
 *        channel.closeFuture().await();
 * </pre>
 */
@ChannelHandler.Sharable
public class DnsServerHandler extends SimpleChannelInboundHandler<DatagramDnsQuery> {

    private final DnsAnswerProvider answerer;

    /**
     * Create a new DnsServerHandler which will used the passed
     * {@link DnsAnswerProvider} to answer DNS questions.
     *
     * @param answerer The thing which prevides results for queries of this DNS
     * server
     */
    public DnsServerHandler(DnsAnswerProvider answerer) {
        super(DatagramDnsQuery.class);
        this.answerer = ObjectUtil.checkNotNull(answerer, "answerer");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramDnsQuery query) throws Exception {
        try {
            answerer.respond(query, ctx, new DnsResponderImpl(ctx, this));
        } catch (Exception ex) {
            exceptionCaught(ctx, ex);
            ctx.channel().writeAndFlush(createErrorResponse(ex, query));
        }
    }

    /**
     * Convenience method to produce an error response. If the passed exception
     * is a DnsDecoderException, will use the error code from it.
     *
     * @param ex The exception
     * @param query The DNS query
     * @return A response which may be sent
     */
    public static DnsResponse createErrorResponse(Exception ex, DatagramDnsQuery query) {
        DnsResponseCode code = ex instanceof DnsDecoderException ? ((DnsDecoderException) ex).code()
                : DnsResponseCode.SERVFAIL;
        DnsResponse resp = new DatagramDnsResponse(query.sender(), query.recipient(),
                query.id(), DnsOpCode.QUERY, code, DnsMessageFlags.setOf(true));
        resp.setOpCode(query.opCode());
        resp.setZ(query.z());
        int max = query.count(DnsSection.QUESTION);
        for (int i = 0; i < max; i++) {
            DnsQuestion question = (DnsQuestion) query.recordAt(DnsSection.QUESTION, i);
            resp.addRecord(DnsSection.QUESTION, question);
        }
        return resp;
    }

    private static class DnsResponderImpl implements DnsResponder {

        final ChannelHandlerContext ctx;
        final DnsServerHandler handler;

        public DnsResponderImpl(ChannelHandlerContext ctx, DnsServerHandler handler) {
            this.ctx = ctx;
            this.handler = handler;
        }

        @Override
        public void withResponse(DnsResponse response) throws Exception {
            System.out.println("Flush response " + response);
            try {
                ctx.channel().writeAndFlush((DatagramDnsResponse) response);
            } catch (Exception e) {
                handler.exceptionCaught(ctx, e);
            }
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        answerer.exceptionCaught(ctx, cause);
    }
}
