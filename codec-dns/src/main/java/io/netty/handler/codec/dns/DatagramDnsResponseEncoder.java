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
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageEncoder;
import static io.netty.handler.codec.dns.DnsRecordType.OPT;
import static io.netty.handler.codec.dns.DnsResponseCode.NOERROR;
import static io.netty.handler.codec.dns.DnsSection.ADDITIONAL;
import static io.netty.handler.codec.dns.DnsSection.ANSWER;
import static io.netty.handler.codec.dns.DnsSection.AUTHORITY;
import static io.netty.handler.codec.dns.DnsSection.QUESTION;
import static io.netty.handler.codec.dns.NameCodec.Feature.COMPRESSION;
import static io.netty.handler.codec.dns.NameCodec.Feature.READ_TRAILING_DOT;
import static io.netty.handler.codec.dns.NameCodec.Feature.WRITE_TRAILING_DOT;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import java.util.List;
import io.netty.util.internal.UnstableApi;
import java.net.InetSocketAddress;

/**
 * Encodes DNS responses.
 */
@UnstableApi
@ChannelHandler.Sharable
public class DatagramDnsResponseEncoder
        extends MessageToMessageEncoder<AddressedEnvelope<DnsResponse<?>, InetSocketAddress>> {

    private static final int FLAGS_Z = 4;
    protected final int maxPacketSize;
    private final int absoluteMaxPacketSize;
    private final DnsRecordEncoder encoder;

    static final int DEFAULT_BASE_PACKET_SIZE = 64;
    static final int DEFAULT_MAX_PACKET_SIZE = 576;
    static final int DEFAULT_ABSOLUTE_MAX_PACKET_SIZE = 4096;
    protected final int minPacketSize;
    private final NameCodec.Factory names;
    static final int ABSOLUTE_MINIMUM_DNS_PACKET_SIZE = 48;

    public DatagramDnsResponseEncoder() {
        this(DnsRecordEncoder.DEFAULT);
    }

    public DatagramDnsResponseEncoder(DnsRecordEncoder encoder, NameCodec.Feature... features) {
        this(DEFAULT_BASE_PACKET_SIZE, DEFAULT_MAX_PACKET_SIZE,
                DEFAULT_ABSOLUTE_MAX_PACKET_SIZE, encoder, NameCodec.factory(features));
    }

    public DatagramDnsResponseEncoder(DnsRecordEncoder encoder) {
        this(DEFAULT_BASE_PACKET_SIZE, DEFAULT_MAX_PACKET_SIZE,
                DEFAULT_ABSOLUTE_MAX_PACKET_SIZE, encoder,
                NameCodec.factory(COMPRESSION, READ_TRAILING_DOT, WRITE_TRAILING_DOT));
    }

    public DatagramDnsResponseEncoder(int minPacketSize, int maxPacketSize,
            int absoluteMaxPacketSize,
            DnsRecordEncoder encoder, NameCodec.Factory names) {
        this.encoder = checkNotNull(encoder, "encoder");
        this.names = checkNotNull(names, "names");
        if (minPacketSize < ABSOLUTE_MINIMUM_DNS_PACKET_SIZE) {
            throw new IllegalArgumentException("Packet minimum size too small "
                    + "- minimum " + ABSOLUTE_MINIMUM_DNS_PACKET_SIZE + " bytes");
        }
        if (maxPacketSize < ABSOLUTE_MINIMUM_DNS_PACKET_SIZE) {
            throw new IllegalArgumentException("Packet maximum size too small "
                    + "- minimum " + ABSOLUTE_MINIMUM_DNS_PACKET_SIZE + " bytes");
        }
        if (maxPacketSize < minPacketSize) {
            throw new IllegalArgumentException("Minimum packet size "
                    + minPacketSize + " is > passed maxPacketSize "
                    + maxPacketSize);
        }
        if (absoluteMaxPacketSize < maxPacketSize) {
            throw new IllegalArgumentException("Absolute max packet size "
                    + absoluteMaxPacketSize + " is less than max packet size "
                    + maxPacketSize);
        }
        this.absoluteMaxPacketSize = absoluteMaxPacketSize;
        this.maxPacketSize = maxPacketSize;
        this.minPacketSize = minPacketSize;
    }

    public static final AttributeKey<Integer> MAX_UDP_PAYLOAD_SIZE
            = AttributeKey.newInstance("maxUdpPayloadSize");

    public static final void setMaxUdpPayloadSize(ChannelHandlerContext ctx,
            int value) {
        ctx.channel().attr(MAX_UDP_PAYLOAD_SIZE).set(value);
    }

    public static final int getMaxUdpPayloadSize(ChannelHandlerContext ctx,
            int defaultValue, int maximum) {
        if (ctx == null || ctx.channel() == null) { // tests
            return maximum;
        }
        Attribute<Integer> val = ctx.channel().attr(MAX_UDP_PAYLOAD_SIZE);
        Integer result = val.get();
        if (result == null) {
            return Math.min(maximum, defaultValue);
        }
        return Math.min(maximum, result);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx,
            AddressedEnvelope<DnsResponse<?>, InetSocketAddress> env,
            List<Object> out) throws Exception {
        // If we are responding to a request, and the OPT ECS header indicated
        // a packet size, use that.
        int maxSize = getMaxUdpPayloadSize(ctx, this.maxPacketSize, absoluteMaxPacketSize);
        ByteBuf buf = ctx.alloc().buffer(minPacketSize, maxSize);
        NameCodec nameCodec = names.getForWrite();
        DnsResponse msg = env.content();
        try {
            encode(msg, buf, nameCodec, maxSize);
        } catch (IndexOutOfBoundsException ex) { // Buffer too small
            msg = winnow(msg, maxSize, false);
            if (msg != null) {
                nameCodec.close();
                nameCodec = names.getForWrite();
                buf.resetReaderIndex();
                buf.resetWriterIndex();
                try {
                    encode(msg, buf, nameCodec, maxSize);
                } catch (IndexOutOfBoundsException ex2) { // Still too small
//                    ex.addSuppressed(ex2); // JDK 8
                    msg = winnow(msg, maxSize, true);
                    if (msg != null) {
                        nameCodec.close();
                        nameCodec = names.getForWrite();
                        buf.resetReaderIndex();
                        buf.resetWriterIndex();
                        try {
                            encode(msg, buf, nameCodec, maxSize);
                        } catch (IndexOutOfBoundsException ex3) { // Give up
//                            ex.addSuppressed(ex3); // JDK 8
                            throw ex3;
                        }
                    }
                }
            }
        } finally {
            nameCodec.close();
        }
        DatagramPacket packet = new DatagramPacket(buf, env.sender(), env.recipient());
        out.add(packet);
    }

    /**
     * Reduce a response and set the truncated flag to try to get something that
     * will fit in the buffer. Return null to not try further. The default
     * implementation just returns null.
     *
     * @param resp The response that could not fit in the buffer.
     * @param more Whether to try harder (second try)
     * @param maxSize The maximum available buffer size
     * @return
     */
    protected DnsResponse<?> winnow(DnsResponse<?> resp, int maxSize, boolean more) {
        return null;
    }

    /**
     * Encode a DnsResponse into a buffer.Public so caches can be serialized to
     * disk in wire-format.
     *
     * @param into The byte buf to encode into
     * @param msg The message
     * @param nameCodec Writes names
     * @param maxSize The maximum packet size, which is either the default value
     * set in a field on this object, or the max packet size passed in an OPT
     * header if set on the channel by calling
     * <code>DatagramDnsResponseEncoder.setMaxUdpPayloadSize()</code>.
     * @throws Exception If something goes wrong
     */
    public void encode(DnsResponse msg, ByteBuf into, NameCodec nameCodec, int maxSize) throws Exception {
        into.writeShort(msg.id());
        short flags = msg.flags().value();
        flags |= msg.code().intValue();
        flags |= msg.z() << FLAGS_Z;

        into.writeShort(flags);
        into.writeShort(msg.count(QUESTION));
        into.writeShort(msg.count(ANSWER));
        into.writeShort(msg.count(AUTHORITY));
        into.writeShort(msg.count(ADDITIONAL));

        // Only use the question section if an error code is set
        DnsSection[] sections = msg.code() != NOERROR
                ? new DnsSection[]{QUESTION, ADDITIONAL}
                : new DnsSection[]{QUESTION, ANSWER, AUTHORITY, ADDITIONAL};

        for (DnsSection sect : sections) {
            int max = msg.count(sect);
            for (int i = 0; i < max; i++) {
                DnsRecord record = msg.recordAt(sect, i);
                boolean writeIt = true;
                // In case of error, still pass OPT records in the ADDITIONAL
                // section but no others from that section
                if (sect == DnsSection.ADDITIONAL && msg.code() != DnsResponseCode.NOERROR) {
                    writeIt = record.type() == OPT;
                }
                if (writeIt) {
                    encoder.encodeRecord(nameCodec, record, into, maxSize);
                }
            }
        }
    }
}
