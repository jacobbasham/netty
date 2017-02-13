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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.util.internal.ObjectUtil;
import java.util.List;

/**
 * Encodes DNS responses.
 */
public class DatagramDnsResponseEncoder
        extends MessageToMessageEncoder<DatagramDnsResponse> {

    private static final int FLAGS_QR = 15;
    private static final int FLAGS_OPCODE = 11;
    private static final int FLAGS_AA = 10;
    private static final int FLAGS_TC = 9;
    private static final int FLAGS_RD = 8;
    private static final int FLAGS_RA = 7;
    private static final int FLAGS_Z = 4;
    /**
     * Message type is query.
     */
    public static final int TYPE_QUERY = 0;

    /**
     * Message type is response.
     */
    public static final int TYPE_RESPONSE = 1;
    private final int basePacketSize;
    private final DnsRecordEncoder encoder;

    static final int DEFAULT_BASE_PACKET_SIZE = 80;
    static final int DEFAULT_MAX_PACKET_SIZE = 576;
    private final int minPacketSize;

    public DatagramDnsResponseEncoder() {
        this(DnsRecordEncoder.DEFAULT);
    }

    public DatagramDnsResponseEncoder(DnsRecordEncoder encoder) {
        this(DEFAULT_BASE_PACKET_SIZE, DEFAULT_MAX_PACKET_SIZE, encoder);
    }

    public DatagramDnsResponseEncoder(int minPacketSize, int maxPacketSize, DnsRecordEncoder encoder) {
        super(DatagramDnsResponse.class);
        this.encoder = ObjectUtil.checkNotNull(encoder, "encoder");
        if (minPacketSize < 48) {
            throw new IllegalArgumentException("Packet base size too small");
        }
        if (maxPacketSize < 48) {
            throw new IllegalArgumentException("Packet max size too small");
        }
        this.basePacketSize = maxPacketSize;
        this.minPacketSize = minPacketSize;
    }

    /**
     * Flip the bit at position <code>index</code> on or off.
     *
     * @param value The value to change
     * @param index The bit-index
     * @param on Whether or not the bit should be a 1
     * @return The updated value
     */
    private static int flip(int value, int index, boolean on) {
        int i = 1 << index;
        if (on) {
            value |= i;
        } else {
            value &= i ^ 0xFFFF;
        }
        return value;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, DatagramDnsResponse msg, List<Object> out) throws Exception {
        ByteBuf buf = ctx.alloc().buffer(minPacketSize, basePacketSize);
        encode(msg, buf);

        DatagramPacket packet = new DatagramPacket(buf, msg.sender(), msg.recipient());
        out.add(packet);
    }

    /**
     * Encode a DnsResponse into a buffer.  Public so caches can be serialized to disk
     * in wire-format.
     *
     * @param into
     * @param msg
     * @throws Exception
     */
    public void encode(DnsResponse msg, ByteBuf into) throws Exception {
        into.writeShort(msg.id());
        short flags = msg.flags().value();
        flags |= msg.code().intValue();
        flags |= msg.opCode().byteValue() << FLAGS_OPCODE;
        flags |= msg.z() << FLAGS_Z;
//        flags |= TYPE_RESPONSE << FLAGS_QR;
//        short flags = (short) msg.code().intValue();
//        flags |= msg.opCode().byteValue() << FLAGS_OPCODE;
//        flags |= TYPE_RESPONSE << FLAGS_QR;
//        flags = DnsMessageFlags.AUTHORITATIVE_ANSWER.write(flags, msg.isAuthoritativeAnswer());
//        flags = DnsMessageFlags.TRUNCATED.write(flags, msg.isTruncated());
//        flags = DnsMessageFlags.RECURSION_AVAILABLE.write(flags, msg.isRecursionAvailable());
//        flags = DnsMessageFlags.RECURSION_DESIRED.write(flags, msg.isRecursionDesired());
//        flags |= msg.z() << FLAGS_Z;

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

        NameCodec nameCodec = NameCodec.compressingNameWriter();
        try {
            for (DnsSection sect : sections) {
                int max = msg.count(sect);
                for (int i = 0; i < max; i++) {
                    DnsRecord record = msg.recordAt(sect, i);
                    encoder.encodeRecord(nameCodec, record, into);
                }
            }
        } finally {
            nameCodec.close();
        }
    }
}
