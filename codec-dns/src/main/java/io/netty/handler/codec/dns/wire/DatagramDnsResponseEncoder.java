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
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.dns.DnsResponse;
import java.util.List;
import java.net.InetSocketAddress;

/**
 * Encodes DNS responses.
 */
@ChannelHandler.Sharable
final class DatagramDnsResponseEncoder
        extends MessageToMessageEncoder<AddressedEnvelope<DnsResponse<?>, InetSocketAddress>> {

    private final DnsMessageEncoder encoder;

    DatagramDnsResponseEncoder(DnsMessageEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        boolean result = msg instanceof AddressedEnvelope<?, ?>
                && ((AddressedEnvelope<?, ?>) msg).content() instanceof DnsResponse;
        return result;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx,
            AddressedEnvelope<DnsResponse<?>, InetSocketAddress> env,
            List<Object> out) throws Exception {
        ByteBuf buf = encoder.encode(ctx, env);
        DatagramPacket packet = new DatagramPacket(buf, env.sender(), env.recipient());
        out.add(packet);
    }
}
