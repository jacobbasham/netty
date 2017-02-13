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
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.dns.DnsMessageFlags.FlagSet;
import static io.netty.handler.codec.dns.DnsMessageFlags.IS_REPLY;

import java.net.InetSocketAddress;
import java.util.List;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Decodes a {@link DatagramPacket} into a {@link DatagramDnsResponse}.
 */
@ChannelHandler.Sharable
public class DatagramDnsResponseDecoder extends MessageToMessageDecoder<DatagramPacket> {

    private final DnsRecordDecoder recordDecoder;
    private final NameCodec.Factory names;

    /**
     * Creates a new decoder with
     * {@linkplain DnsRecordDecoder#DEFAULT the default record decoder}.
     */
    public DatagramDnsResponseDecoder() {
        this(DnsRecordDecoder.DEFAULT, NameCodec.compressingFactory());
    }

    /**
     * Creates a new decoder with the specified {@code recordDecoder}.
     *
     * @param recordDecoder Decodes records
     * @param names Factory for thing that decodes names
     */
    public DatagramDnsResponseDecoder(DnsRecordDecoder recordDecoder, NameCodec.Factory names) {
        this.recordDecoder = checkNotNull(recordDecoder, "recordDecoder");
        this.names = names;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, DatagramPacket packet, List<Object> out) throws Exception {
        final InetSocketAddress sender = packet.sender();
        final ByteBuf buf = packet.content();

        DnsResponse<?> response = decode(sender, buf);
        out.add(response);
    }

    /**
     * Decode one DatagramDnsMessage from a ByteBuf.
     *
     * @param sender The sender
     * @param buf The buffer
     * @return A response
     * @throws Exception if something goes wrong
     */
    public DnsResponse<?> decode(InetSocketAddress sender, ByteBuf buf) throws Exception {

        boolean success = false;
        NameCodec nameReader = names.getForRead();
        DnsResponse response = null;
        try {
            response = readResponseHeader(sender, buf);
            final int questionCount = buf.readUnsignedShort();
            final int answerCount = buf.readUnsignedShort();
            final int authorityRecordCount = buf.readUnsignedShort();
            final int additionalRecordCount = buf.readUnsignedShort();

            decodeQuestions(response, buf, questionCount, nameReader);
            decodeRecords(response, DnsSection.ANSWER, buf, answerCount, nameReader);
            decodeRecords(response, DnsSection.AUTHORITY, buf, authorityRecordCount, nameReader);
            decodeRecords(response, DnsSection.ADDITIONAL, buf, additionalRecordCount, nameReader);

            success = true;
            return response;
        } finally {
            nameReader.close();
            if (!success && response != null) {
                response.release();
            }
        }
    }

    private DnsResponse<?> readResponseHeader(InetSocketAddress sender, ByteBuf buf) {
        final int id = buf.readUnsignedShort();
        final short flags = buf.readShort();

        FlagSet flagSet = DnsMessageFlags.forFlags(flags);
        if (!flagSet.contains(IS_REPLY)) {
            throw new CorruptedFrameException("not a response");
        }

        final DnsResponse response = new DatagramDnsResponse(
                sender, null,
                id, DnsOpCode.valueOf((byte) (flags >> 11 & 0xf)),
                DnsResponseCode.valueOf((byte) (flags & 0xf)), flagSet);

        response.setZ(flags >> 4 & 0x7);
        return response;
    }

    private void decodeQuestions(DnsResponse<?> response, ByteBuf buf, int questionCount,
            NameCodec names) throws Exception {
        for (int i = questionCount; i > 0; i--) {
            DnsRecord question = recordDecoder.decodeQuestion(buf, names);
            response.addRecord(DnsSection.QUESTION, question);
        }
    }

    private void decodeRecords(
            DnsResponse<?> response, DnsSection section, ByteBuf buf, int count, NameCodec names) throws Exception {
        for (int i = count; i > 0; i--) {
            final DnsRecord r = recordDecoder.decodeRecord(buf, names);
            if (r == null) {
                // Truncated response
                break;
            }

            response.addRecord(section, r);
        }
    }
}
