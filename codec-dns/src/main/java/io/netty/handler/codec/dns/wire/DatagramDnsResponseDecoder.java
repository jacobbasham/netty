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
package io.netty.handler.codec.dns.wire;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.dns.DatagramDnsResponse;

import java.util.List;

/**
 * Decodes a {@link DatagramPacket} into a {@link DatagramDnsResponse}.
 */
@ChannelHandler.Sharable
final class DatagramDnsResponseDecoder extends MessageToMessageDecoder<DatagramPacket> {

    private final DnsMessageDecoder<DatagramDnsResponse> decoder;

    DatagramDnsResponseDecoder(DnsMessageDecoder<DatagramDnsResponse> decoder) {
        this.decoder = decoder;
    }

    @Override
    public boolean acceptInboundMessage(Object msg) throws Exception {
        return msg instanceof DatagramPacket;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, DatagramPacket packet, List<Object> out) throws Exception {
        out.add(decoder.decode(packet.content(), packet.sender(), packet.recipient()));
    }
}
