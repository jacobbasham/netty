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

import io.netty.buffer.ByteBuf;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageEncoder;
import static io.netty.handler.codec.dns.DatagramDnsResponseEncoder.DEFAULT_BASE_PACKET_SIZE;
import static io.netty.handler.codec.dns.DatagramDnsResponseEncoder.DEFAULT_MAX_PACKET_SIZE;

import java.net.InetSocketAddress;
import java.util.List;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Encodes a {@link DatagramDnsQuery} (or an {@link AddressedEnvelope} of
 * {@link DnsQuery}} into a {@link DatagramPacket}.
 */
@ChannelHandler.Sharable
public class DatagramDnsQueryEncoder
        extends MessageToMessageEncoder<AddressedEnvelope<DnsQuery<?>, InetSocketAddress>> {

    private final DnsRecordEncoder recordEncoder;
    private final NameCodec.Factory names;
    private final int baseBufferSize;
    private final int maxBufferSize;

    /**
     * Creates a new encoder with
     * {@linkplain DnsRecordEncoder#DEFAULT the default record encoder}.
     */
    public DatagramDnsQueryEncoder() {
        this(DnsRecordEncoder.DEFAULT, NameCodec.compressingFactory(), DEFAULT_BASE_PACKET_SIZE,
                DEFAULT_MAX_PACKET_SIZE);
    }

    /**
     * Creates a new encoder with the specified {@code recordEncoder}.
     *
     * @param recordEncoder encodes DNS records into wire format
     * @param names writes names in wire format
     * @param baseBufferSize the initial buffer size to create - something
     * slightly above average DNS query on-wire byte length
     * @param maxBufferSize the maximum buffer size to allow
     */
    public DatagramDnsQueryEncoder(DnsRecordEncoder recordEncoder, NameCodec.Factory names,
            int baseBufferSize, int maxBufferSize) {
        this.recordEncoder = checkNotNull(recordEncoder, "recordEncoder");
        this.names = names;
        this.baseBufferSize = baseBufferSize;
        this.maxBufferSize = maxBufferSize;
    }

    @Override
    protected void encode(
            ChannelHandlerContext ctx,
            AddressedEnvelope<DnsQuery<?>, InetSocketAddress> in, List<Object> out) throws Exception {

        final InetSocketAddress recipient = in.recipient();
        final DnsQuery query = in.content();
        final ByteBuf buf = allocateBuffer(ctx, in);

        boolean success = false;
        NameCodec nameWriter = names.getForWrite();
        try {
            encodeHeader(query, buf);
            encodeQuestions(nameWriter, query, buf);
            encodeRecords(nameWriter, query, DnsSection.ADDITIONAL, buf);
            success = true;
        } finally {
            if (!success) {
                buf.release();
            }
            nameWriter.close();
        }

        out.add(new DatagramPacket(buf, recipient, null));
    }

    /**
     * Allocate a {@link ByteBuf} which will be used for constructing a datagram
     * packet. Sub-classes may override this method to return a {@link ByteBuf}
     * with a perfect matching initial capacity.
     */
    protected ByteBuf allocateBuffer(
            ChannelHandlerContext ctx,
            @SuppressWarnings("unused") AddressedEnvelope<DnsQuery<?>, InetSocketAddress> msg) throws Exception {
        return ctx.alloc().ioBuffer(baseBufferSize, maxBufferSize);
    }

    /**
     * Encodes the header that is always 12 bytes long.
     *
     * @param query the query header being encoded
     * @param buf the buffer the encoded data should be written to
     */
    private static void encodeHeader(DnsQuery<?> query, ByteBuf buf) {
        buf.writeShort(query.id());
        short flags = query.flags().value();
        flags |= (query.opCode().byteValue() & 0xFF) << 14;
        buf.writeShort(flags);
        buf.writeShort(query.count(DnsSection.QUESTION));
        buf.writeShort(0); // answerCount
        buf.writeShort(0); // authorityResourceCount
        buf.writeShort(query.count(DnsSection.ADDITIONAL));
    }

    private void encodeQuestions(NameCodec nameWriter, DnsQuery<?> query, ByteBuf buf) throws Exception {
        final int count = query.count(DnsSection.QUESTION);
        for (int i = 0; i < count; i++) {
            recordEncoder.encodeQuestion(nameWriter, (DnsQuestion) query.recordAt(DnsSection.QUESTION, i), buf);
        }
    }

    private void encodeRecords(NameCodec nameWriter, DnsQuery<?> query,
            DnsSection section, ByteBuf buf) throws Exception {
        final int count = query.count(section);
        for (int i = 0; i < count; i++) {
            recordEncoder.encodeRecord(nameWriter, query.recordAt(section, i), buf);
        }
    }
}
