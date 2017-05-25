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
package io.netty.handler.codec.dns;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageEncoder;
import static io.netty.handler.codec.dns.NameCodec.Feature.COMPRESSION;
import static io.netty.handler.codec.dns.NameCodec.Feature.READ_TRAILING_DOT;
import static io.netty.handler.codec.dns.NameCodec.Feature.WRITE_TRAILING_DOT;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import java.util.List;
import io.netty.util.internal.UnstableApi;

/**
 * Encodes DNS responses.
 */
@UnstableApi
@ChannelHandler.Sharable
public class DatagramDnsResponseEncoder
        extends MessageToMessageEncoder<DatagramDnsResponse> {

    private static final int FLAGS_Z = 4;
    private final int basePacketSize;
    private final DnsRecordEncoder encoder;

    static final int DEFAULT_BASE_PACKET_SIZE = 64;
    static final int DEFAULT_MAX_PACKET_SIZE = 576;
    private final int minPacketSize;
    private final NameCodec.Factory names;

    public DatagramDnsResponseEncoder() {
        this(DnsRecordEncoder.DEFAULT);
    }

    public DatagramDnsResponseEncoder(DnsRecordEncoder encoder, NameCodec.Feature... features) {
        this(DEFAULT_BASE_PACKET_SIZE, DEFAULT_MAX_PACKET_SIZE, encoder, NameCodec.factory(features));
    }

    public DatagramDnsResponseEncoder(DnsRecordEncoder encoder) {
        this(DEFAULT_BASE_PACKET_SIZE, DEFAULT_MAX_PACKET_SIZE, encoder,
                NameCodec.factory(COMPRESSION, READ_TRAILING_DOT, WRITE_TRAILING_DOT));
    }

    public DatagramDnsResponseEncoder(int minPacketSize, int maxPacketSize,
            DnsRecordEncoder encoder, NameCodec.Factory names) {
        super(DatagramDnsResponse.class);
        this.encoder = checkNotNull(encoder, "encoder");
        this.names = checkNotNull(names, "names");
        if (minPacketSize < 48) {
            throw new IllegalArgumentException("Packet base size too small");
        }
        if (maxPacketSize < 48) {
            throw new IllegalArgumentException("Packet max size too small");
        }
        this.basePacketSize = maxPacketSize;
        this.minPacketSize = minPacketSize;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, DatagramDnsResponse msg, List<Object> out) throws Exception {
        ByteBuf buf = ctx.alloc().buffer(minPacketSize, basePacketSize);
        NameCodec nameCodec = names.getForWrite();
        try {
            encode(msg, buf, nameCodec);
        } finally {
            nameCodec.close();
        }
        DatagramPacket packet = new DatagramPacket(buf, msg.sender(), msg.recipient());
        out.add(packet);
    }

    /**
     * Encode a DnsResponse into a buffer. Public so caches can be serialized to
     * disk in wire-format.
     *
     * @param into
     * @param msg
     * @throws Exception
     */
    public void encode(DnsResponse msg, ByteBuf into, NameCodec nameCodec) throws Exception {
        into.writeShort(msg.id());
        short flags = msg.flags().value();
        flags |= msg.code().intValue();
        flags |= msg.z() << FLAGS_Z;

        into.writeShort(flags);
        into.writeShort(msg.count(DnsSection.QUESTION));
        into.writeShort(msg.count(DnsSection.ANSWER));
        into.writeShort(msg.count(DnsSection.AUTHORITY));
        into.writeShort(msg.count(DnsSection.ADDITIONAL));

        // Only use the question section if an error code is set
        DnsSection[] sections = msg.code() != DnsResponseCode.NOERROR
                ? new DnsSection[]{DnsSection.QUESTION}
                : new DnsSection[]{DnsSection.QUESTION, DnsSection.ANSWER, DnsSection.AUTHORITY,
                    DnsSection.ADDITIONAL};

        for (DnsSection sect : sections) {
            int max = msg.count(sect);
            for (int i = 0; i < max; i++) {
                DnsRecord record = msg.recordAt(sect, i);
                encoder.encodeRecord(nameCodec, record, into);
            }
        }
    }
}
