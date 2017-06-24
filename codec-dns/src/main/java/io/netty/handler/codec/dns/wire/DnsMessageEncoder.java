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
import io.netty.buffer.Unpooled;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import static io.netty.handler.codec.dns.DnsClass.CLASS_IN;
import io.netty.handler.codec.dns.DnsMessage;
import static io.netty.handler.codec.dns.DnsMessageFlags.IS_REPLY;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordEncoder;
import io.netty.handler.codec.dns.DnsRecordType;
import static io.netty.handler.codec.dns.DnsRecordType.OPT;
import static io.netty.handler.codec.dns.DnsRecordType.SOA;
import static io.netty.handler.codec.dns.DnsRecordType.SRV;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import static io.netty.handler.codec.dns.DnsResponseCode.NOERROR;
import io.netty.handler.codec.dns.DnsSection;
import static io.netty.handler.codec.dns.DnsSection.*;
import io.netty.handler.codec.dns.names.NameCodec;
import io.netty.handler.codec.dns.names.NameCodecFactory;
import io.netty.handler.codec.dns.names.NameCodecFeature;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import io.netty.util.internal.UnstableApi;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * Encodes DNS messages into wire format, without caring whether they will be
 * sent via UDP or TCP.
 */
@UnstableApi
public class DnsMessageEncoder {

    private final Limits limits;
    private final NameCodecFactory names;
    private final DnsRecordEncoder encoder;
    private final DnsMessageWinnower winnower;
    private static final int FLAGS_Z = 4;
    private static final DnsSection[] ALL_SECTIONS_ORDERED
            = new DnsSection[]{QUESTION, ANSWER, AUTHORITY, ADDITIONAL};
    private final IllegalRecordPolicy policy;
    private final boolean mdns;

    /**
     * Create a new encoder using the passed parameters.
     *
     * @param limits Buffer size limits
     * @param encoder The record encoder
     * @param names Writes names (possibly compressed or converting punycode)
     * into buffers
     * @param winnower (may be null) Can reduce the size of messages that are
     * too big for a UDP packet, most likely setting the truncated flag to
     * trigger a request via TCP
     */
    public DnsMessageEncoder(Limits limits,
            DnsRecordEncoder encoder, NameCodecFactory names,
            IllegalRecordPolicy policy, DnsMessageWinnower winnower, boolean mdns) {
        this.encoder = checkNotNull(encoder, "encoder");
        this.names = checkNotNull(names, "names");
        this.limits = checkNotNull(limits, "limits");
        this.policy = checkNotNull(policy, "policy");
        this.winnower = winnower;
        this.mdns = mdns;
    }

    /**
     * Create a DNS message encoder with default values.
     */
    public DnsMessageEncoder() {
        this(Limits.DEFAULT, DnsRecordEncoder.DEFAULT,
                NameCodec.compressingFactory(), IllegalRecordPolicy.THROW,
                null, false);
    }

    public MessageToMessageEncoder<AddressedEnvelope<DnsResponse<?>, InetSocketAddress>>
            udpResponseEncoder() {
        return new DatagramDnsResponseEncoder(this);
    }

    public MessageToMessageEncoder<AddressedEnvelope<DnsQuery<?>, InetSocketAddress>>
            udpQueryEncoder() {
        return new DatagramDnsQueryEncoder(this);
    }

    /**
     * Get a builder for a DnsMessageEncoder with specific features.
     */
    public static MessageEncoderBuilder builder() {
        return new MessageEncoderBuilder();
    }

    /**
     * Convenience builder for message encoders.
     */
    @UnstableApi
    public static final class MessageEncoderBuilder {

        private final Set<NameCodecFeature> nameFeatures = EnumSet.noneOf(NameCodecFeature.class);
        private final Limits.LimitsBuilder limitsBuilder = Limits.builder();
        private DnsMessageWinnower winnower;
        private DnsRecordEncoder encoder = DnsRecordEncoder.DEFAULT;
        private IllegalRecordPolicy policy = IllegalRecordPolicy.THROW;
        private boolean mdns;

        private MessageEncoderBuilder() {
        }

        /**
         * Build an encoder suitable for encoding DNS responses into UDP packets
         * for use in a DNS server.
         */
        public MessageToMessageEncoder<AddressedEnvelope<DnsResponse<?>, InetSocketAddress>>
                buildUdpResponseEncoder() {
            return build().udpResponseEncoder();
        }

        /**
         * Build an encoder suitable for encoding DNS queries into UDP packets
         * for use in a DNS client.
         */
        public MessageToMessageEncoder<AddressedEnvelope<DnsQuery<?>, InetSocketAddress>>
                buildUdpQueryEncoder() {
            return build().udpQueryEncoder();
        }

        /**
         * Build an encoder.
         */
        public DnsMessageEncoder build() {
            NameCodecFactory factory;
            if (!nameFeatures.isEmpty()) {
                factory = NameCodec.factory(nameFeatures);
            } else {
                factory = NameCodec.compressingFactory();
            }
            Limits limits = limitsBuilder.build();
            return new DnsMessageEncoder(limits, encoder, factory, policy, winnower, mdns);
        }

        /**
         * Create an encoder which will handle mDNS messages, decoding the high
         * bit of the DnsClass as a boolean describing unicast message handling.
         * Note that calling this automatically changes the NameCodec
         * configuration to MDNS_UTF_8 / COMPRESSION / WRITE_TRAILING_DOT, which
         * are required for mDNS.
         */
        public MessageEncoderBuilder mDNS() {
            this.mdns = true;
            nameFeatures.remove(NameCodecFeature.PUNYCODE);
            nameFeatures.add(NameCodecFeature.MDNS_UTF_8);
            nameFeatures.add(NameCodecFeature.COMPRESSION);
            nameFeatures.add(NameCodecFeature.WRITE_TRAILING_DOT);
            return this;
        }

        /**
         * Set what to do when a record is encountered where it does not belong.
         * The default policy is to throw an exception, but it is also possible
         * to either encode such invalid messages as-is, or silently discard
         * anything not spec-compliant.
         */
        public MessageEncoderBuilder withIllegalRecordPolicy(IllegalRecordPolicy policy) {
            this.policy = checkNotNull(policy, "policy");
            return this;
        }

        /**
         * Set the minimum packet size. The absolute minimum for DNS packets is
         * 48 bytes. This simply determines the size of the initial buffer
         * allocated for packets being encoded.
         */
        public MessageEncoderBuilder withMinPacketSize(int size) {
            limitsBuilder.withMinPacketSize(size);
            return this;
        }

        /**
         * Set the default maximum packet size - the maximum capacity of buffers
         * allocated for encoding DNS packets. Note that the {@link Limits}
         * class includes a mechanism to advise it of a higher capacity - EDNS
         * OPT records can include a maximum UDP packet size that can traverse
         * the network. If that has been set and is higher than this value, it
         * will be used in place of this value.
         * <p>
         * Note that if this value is less than the minimum packet size set, the
         * build method will throw an exception.
         */
        public MessageEncoderBuilder withMaxPacketSize(int maxSize) {
            limitsBuilder.withMaxPacketSize(maxSize);
            return this;
        }

        /**
         * Set the <i>absolute</i> maximum buffer size - even if the
         * {@link Limits} class has been advised that a larger packet is
         * possible, it will never allocate a larger buffer than the value
         * passed here.
         * <p>
         * Note that if this value is less than the minimum or maximum packet
         * size set, the build method will throw an exception.
         */
        public MessageEncoderBuilder withAbsoluteMaxBufferSize(int absMax) {
            limitsBuilder.withAbsoluteMaxPacketSize(absMax);
            return this;
        }

        /**
         * Provide a custom encoder for DNS records.
         */
        public MessageEncoderBuilder withRecordEncoder(DnsRecordEncoder encoder) {
            this.encoder = checkNotNull(encoder, "encoder");
            return this;
        }

        /**
         * Specify the NameCodec features you want. Note that if you are sending
         * data over the wire as DNS packets, include WRITE_TRAILING_DOT or you
         * will generate invalid packets. By default you get a NameCodec that
         * supports punycode, compression, and writing but not reading the
         * trailing dot. Specifying anything here overrides that default.
         */
        public MessageEncoderBuilder withNameFeatures(NameCodecFeature... features) {
            checkNotNull(features, "features");
            for (int i = 0; i < features.length; i++) {
                if (features[i] == null) {
                    throw new NullPointerException("Null element at " + i + " in "
                        + Arrays.asList(features));
                }
            }
            nameFeatures.addAll(Arrays.asList(features));
            return this;
        }

        /**
         * Provide a service which can reduce the size of messages which are too
         * big for the available buffer size (most likely setting the truncated
         * flag on the message to tell the client that the data is incomplete).
         */
        public MessageEncoderBuilder withWinnower(DnsMessageWinnower winnower) {
            this.winnower = checkNotNull(winnower, "winnower");
            return this;
        }
    }

    /**
     * Encode a message into a buffer.
     *
     * @param ctx The channel context
     * @param env The envelope holding the message, sender and recipient
     * addresses
     * @return A buffer
     * @throws Exception If encoding fails
     */
    public ByteBuf encode(ChannelHandlerContext ctx,
            AddressedEnvelope<? extends DnsMessage<?>, InetSocketAddress> env) throws Exception {

        // Allocate a buffer sized to the limits in the constructor and
        // advised by any call to set the max size higher for this channel
        ByteBuf buf = limits.buffer(ctx);

        // Max capacity will be our size limit
        int maxSize = buf.maxCapacity();
        DnsMessage<?> msg = env.content();
        boolean success = false;
        NameCodec nameCodec = names.getForWrite();
        try {
            // First encoding pass
            encode(msg, buf, nameCodec, maxSize);
            success = true;
        } catch (IndexOutOfBoundsException ex) { // Buffer too small
            // If a winnower was set, iterate giving it chances to
            // make the message smaller and set the truncated flag.
            // Stop when the message encodes successfully, or the
            // winnower returns null
            int count = 0;
            while ((msg = winnow(msg, maxSize, count++)) != null) {
                // Reset compressed name positions table
                nameCodec.close();
                // Get a new one
                nameCodec = names.getForWrite();
                buf.resetReaderIndex();
                buf.resetWriterIndex();
                try {
                    encode(msg, buf, nameCodec, maxSize);
                    success = true;
                    break;
                } catch (IndexOutOfBoundsException ex1) {
                    // ex.addSuppressed(ex1) // PENDING jdk 8
                    ex = ex1;
                    msg = null;
                }
            }
            if (!success) {
                throw ex;
            }
        } finally {
            nameCodec.close();
            if (!success) {
                buf.release();
            }
        }
        return buf;
    }

    private void encodeEDNSResponseCodeHighBits(DnsResponse<?> msg) {
        // If the response code is > 15, then the high 12 bits of it
        // get encoded into an OPT EDNS record
        int code = msg.code().intValue();
        int highBits = (code >> 4) & 0x00FF;
        long ttl = 0;
        // 512 is the minimum all DNS servers and clients must support
        // according to spec
        int udpPayloadSize = Math.max(limits.maxPacketSize(), 512);

        DnsRecord optRecord = null;
        int addtlCount = msg.count(ADDITIONAL);
        int index;
        // Look for an existing OPT record to modify, and pick
        // up its ttl and dns class values - we will mask out any
        // existing response code bits, but not replace the udp
        // payload size if already set
        for (index = 0; index < addtlCount; index++) {
            DnsRecord rec = msg.recordAt(ADDITIONAL, index);
            if (rec.type() == DnsRecordType.OPT) {
                optRecord = rec;
                ttl = rec.timeToLive();
                // Already set, don't clobber it
                if (CLASS_IN != rec.dnsClassValue() && rec.dnsClassValue() != 0) {
                    udpPayloadSize = rec.dnsClassValue();
                }
                break;
            }
        }
        // OR in the high bits of the response code, preserving anything
        // else found if there was an existing record
        ttl = (ttl & 0x00FFFFFF) | (highBits << 24);
        if (optRecord == null) {
            // Add a new empty OPT record to hold the values
            optRecord = new DefaultDnsRawRecord(".", OPT, udpPayloadSize, ttl, Unpooled.EMPTY_BUFFER);
            msg.addRecord(ADDITIONAL, optRecord);
        } else {
            // Modify the existing OPT record, replacing it if we were
            // returned a new record instance
            DnsRecord orig = optRecord;
            optRecord = optRecord.withTimeToLiveAndDnsClass(ttl, udpPayloadSize);
            if (orig != optRecord) {
                msg.removeRecord(ADDITIONAL, index);
                msg.addRecord(ADDITIONAL, optRecord);
            }
        }
    }

    /**
     * Encode a DnsResponse into a buffer. Note that all names written within
     * this call will be part of the passed NameCodec's compression table, if
     * you pass in a compressing name codec - in that case, it should be clean
     * when you pass it in, and you should close it after this call.
     *
     * @param into The byte buf to encode into
     * @param msg The message
     * @param nameCodec Writes names
     * @param maxSize The maximum packet size, which is either the default value
     * set in a field on this object, or the max packet size passed in an OPT
     * header if set on the channel by calling
     * <code>Limits.setMaxUdpPayloadSize()</code>.
     * @throws Exception If something goes wrong
     */
    public void encode(DnsMessage<?> msg, ByteBuf into, NameCodec nameCodec, int maxSize) throws Exception {
        short flags = msg.flags().value();

        // Merge in the opcode
        flags |= (msg.opCode().byteValue() & 0xFF) << 14;

        // Look up the response code, which only exists in
        // DNS responses
        DnsResponseCode code = DnsResponseCode.NOERROR;
        if (msg instanceof DnsResponse<?>) {
            // Merge the response code into the low four bits
            // of the flags
            DnsResponse<?> resp = (DnsResponse<?>) msg;
            code = resp.code();
            flags |= code.intValue() & 0x000F;
            if (code.isEDNS()) {
                encodeEDNSResponseCodeHighBits(resp);
            }
        }
        // Merge in the Z value
        flags |= msg.z() << FLAGS_Z;

        // Special handling for questions and errors, which are
        // - not allowed to have any ANSWER or AUTHORITY records
        // - allowed to have only exactly one OPT record in ADDITIONAL
        boolean isError = !NOERROR.equals(code);
        boolean isQuery = !msg.flags().contains(IS_REPLY);

        // Count the ADDITIONAL records, which will differ based on whether
        // this is a question, no error response or an error response
        // Also throw an error here if illegal records are present and the
        // IllegalRecordPolicy is THROW.
        if ((isError || isQuery) && policy != IllegalRecordPolicy.INCLUDE) {
            // In error responses and questions, the only legal item
            // in the additional section is an OPT record
            int additionalCount = 0;
            int itemsInAdditionalSection = msg.count(ADDITIONAL);
            for (int i = 0; i < itemsInAdditionalSection; i++) {
                DnsRecord rec = msg.recordAt(ADDITIONAL, i);
                if (OPT.equals(rec.type())) {
                    additionalCount++;
                    // XXX if > 1, OPT records could be coalesced - there
                    // is a legitimate need for that if multiple decoupled
                    // things are composing a response
                    if (additionalCount > 1 && policy != IllegalRecordPolicy.INCLUDE) {
                        throw new InvalidDnsRecordException("Only one OPT record "
                                + "allowed per DNS message - see "
                                + "https://tools.ietf.org/html/rfc6891#section-6.1.1");
                    }
                } else if ((isError || isQuery) && policy == IllegalRecordPolicy.THROW) {
                    throw new InvalidDnsRecordException("In error responses and "
                            + "questions, only "
                            + "OPT records are allowed in the ADDITIONAL section, "
                            + "but found " + rec);
                }
            }
        }
        // Check the other sections and throw if required by policy
        if ((isError || isQuery) && policy == IllegalRecordPolicy.THROW) {
            if (msg.count(ANSWER) != 0) {
                throw new InvalidDnsRecordException("Found an ANSWER record in a "
                        + "message which is either a query or an error.");
            }
            if (msg.count(AUTHORITY) != 0) {
                throw new InvalidDnsRecordException("Found an AUTHORITY record in a "
                        + "message which is either a query or an error.");
            }
        }

        // Write the message header
        into.writeShort(msg.id());
        into.writeShort(flags);
        boolean omitAnswerAndAuthoritySections = (isQuery || isError) && (policy != IllegalRecordPolicy.INCLUDE);

        int countsPosition = into.writerIndex();
        into.writerIndex(into.writerIndex() + (ALL_SECTIONS_ORDERED.length * 2));

        // Store final counts into this array and rewrite
        int[] counts = new int[ALL_SECTIONS_ORDERED.length];

        // Iterate all records, check they are legal, and include, ignore or
        // throw according to policy
        for (int sectionIndex = 0; sectionIndex < ALL_SECTIONS_ORDERED.length; sectionIndex++) {
            DnsSection sect = ALL_SECTIONS_ORDERED[sectionIndex];
            int max = msg.count(sect);
            // Skip sections we've written 0 counts for above
            if (omitAnswerAndAuthoritySections) {
                switch (sect) {
                    case ANSWER:
                    case AUTHORITY:
                        continue;
                }
            }
            if (!mdns && sect == QUESTION && max > 1) {
                switch(policy) {
                    case DISCARD :
                        continue;
                    case THROW :
                        throw new InvalidDnsRecordException("mDNS allows multiple "
                                + "question section entries, but this encoder "
                                + "does not have mDNS support turned on.");
                }
            }
            int optCountForSection = 0;
            for (int recordIndex = 0; recordIndex < max; recordIndex++) {
                DnsRecord record = msg.recordAt(sect, recordIndex);
                if (isError || isQuery) {
                    switch (sect) {
                        case ANSWER:
                        case AUTHORITY:
                            if (policy == IllegalRecordPolicy.DISCARD) {
                                // Loop back and get the next record/section
                                continue;
                            } else if (policy == IllegalRecordPolicy.THROW) {
                                throw new InvalidDnsRecordException("Query and "
                                        + "error messages should not have "
                                        + "contents in the " + sect + " section"
                                        + " but was requested to encode " + record);
                            }
                            break;
                    }
                }
                // Enforce spec-required special handling for OPT records
                if (OPT.equals(record.type())) {
                    if (sect != ADDITIONAL) {
                        if (policy == IllegalRecordPolicy.DISCARD) {
                            continue;
                        } else if (policy == IllegalRecordPolicy.THROW) {
                            throw new InvalidDnsRecordException("OPT messages are "
                                    + "only permitted in the ADDITIONAL section "
                                    + "of a DNS message, but requested to encode "
                                    + record + " in the " + sect + " section. See "
                                    + " https://tools.ietf.org/html/rfc6891#section-6.1.1");
                        }
                    }
                    optCountForSection++;
                    if (optCountForSection > 1) {
                        if (policy == IllegalRecordPolicy.DISCARD) {
                            continue;
                        } else if (policy == IllegalRecordPolicy.THROW) {
                            throw new InvalidDnsRecordException("Only one OPT "
                                    + "record allowed per message "
                                    + "- https://tools.ietf.org/html/rfc6891#section-6.1.1");
                        }
                    }
                }
                if (mdns && SOA.equals(record.type())) {
                    if (policy == IllegalRecordPolicy.DISCARD) {
                        continue;
                    } else if (policy == IllegalRecordPolicy.THROW) {
                        throw new InvalidDnsRecordException("SOA records not allowed"
                                + " in mDNS, but requested to encode " + record);
                    }
                }
                boolean reallyWriteRecord = true;
                // In case of error, still pass OPT records in the ADDITIONAL
                // section but no others from that section
                if (policy != IllegalRecordPolicy.INCLUDE) {
                    // If the policy is THROW and there is something illegal
                    // here, we have already thrown it
                    if ((isError || isQuery) && sect == DnsSection.ADDITIONAL) {
                        reallyWriteRecord = record.type() == OPT;
                    }
                }
                if (reallyWriteRecord) {
                    NameCodec codecToUse = nameCodec;
                    boolean isSRV = SRV.equals(record.type());
                    if ((!mdns && isSRV) || (mdns && isSRV && record.isUnicast())) {
                        // See https://tools.ietf.org/html/rfc6762#section-18.14
                        // SRV records may not be compressed for unicast DNS
                        // but may be for multicast DNS
                        if (mdns && codecToUse.supportsUnicode()) {
                            codecToUse = NameCodec.get(NameCodecFeature.WRITE_TRAILING_DOT,
                                    NameCodecFeature.MDNS_UTF_8);
                        } else {
                            codecToUse = NameCodec.nonCompressingNameCodec();
                        }
                    }
                    encoder.encodeRecord(codecToUse, record, into, maxSize);
                    counts[sectionIndex]++;
                    if (codecToUse != nameCodec) {
                        codecToUse.close();
                    }
                }
            }
        }
        int endIndex = into.writerIndex();
        into.writerIndex(countsPosition);
        for (int i = 0; i < counts.length; i++) {
            into.writeShort(counts[i]);
        }
        into.writerIndex(endIndex);
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
    private <T extends DnsMessage<?>> T winnow(T resp, int maxSize, int count) {
        if (winnower != null) {
            return winnower.winnow(resp, maxSize, count);
        }
        return null;
    }
}
