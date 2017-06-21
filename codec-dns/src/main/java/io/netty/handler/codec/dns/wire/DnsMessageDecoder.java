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
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.dns.DatagramDnsQuery;
import io.netty.handler.codec.dns.DatagramDnsResponse;
import io.netty.handler.codec.dns.DnsMessage;
import io.netty.handler.codec.dns.DnsMessageFlags;
import io.netty.handler.codec.dns.DnsOpCode;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordDecoder;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.handler.codec.dns.names.NameCodec;
import io.netty.handler.codec.dns.names.NameCodecFactory;
import io.netty.handler.codec.dns.names.NameCodecFeature;
import static io.netty.handler.codec.dns.DnsMessageFlags.IS_REPLY;
import static io.netty.handler.codec.dns.DnsRecordType.OPT;
import io.netty.handler.codec.dns.DnsResponse;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import io.netty.util.internal.UnstableApi;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * Decoder for DNS messages which is not tied to UDP or TCP, and can be used by
 * actual decoders for either.
 */
@UnstableApi
public final class DnsMessageDecoder<M extends DnsMessage> {

    private final DnsRecordDecoder recordDecoder;
    private final NameCodecFactory names;
    private final DnsMessageFactory<M> factory;
    private IllegalRecordPolicy policy;
    private final boolean mdns;

    public DnsMessageDecoder(DnsMessageFactory<M> factory) {
        this(DnsRecordDecoder.DEFAULT, NameCodec.compressingFactory(), factory,
                IllegalRecordPolicy.THROW, false);
    }

    /**
     * Creates a new decoder with the specified {@code recordDecoder}.
     */
    public DnsMessageDecoder(DnsRecordDecoder decoder, DnsMessageFactory<M> factory, IllegalRecordPolicy policy) {
        this(decoder, NameCodec.compressingFactory(), factory, policy, false);
    }

    public DnsMessageDecoder(DnsRecordDecoder decoder, NameCodecFactory names,
            DnsMessageFactory<M> factory, IllegalRecordPolicy policy, boolean mdns) {
        this.recordDecoder = checkNotNull(decoder, "decoder");
        this.names = checkNotNull(names, "names");
        this.factory = checkNotNull(factory, "factory");
        this.policy = checkNotNull(policy, "policy");
        this.mdns = mdns;
    }

    /**
     * Get a builder for a DnsMessageDecoder with specific features. By default
     * you get a DnsMessageDecoder which will decode compressed names and which
     * uses the default (limited) record decoder, with an IllegalRecordPolicy of
     * throw.
     */
    public static MessageDecoderBuilder builder() {
        return new MessageDecoderBuilder();
    }

    /**
     * Convenience builder for message encoders.
     */
    @UnstableApi
    public static final class MessageDecoderBuilder {

        private final Set<NameCodecFeature> nameFeatures = EnumSet.noneOf(NameCodecFeature.class);
        private DnsRecordDecoder encoder = DnsRecordDecoder.DEFAULT;
        private IllegalRecordPolicy policy = IllegalRecordPolicy.THROW;
        private boolean mdns;

        /**
         * Build a decoder which decodes messages to instances of DnsQuery.
         */
        public DnsMessageDecoder<DatagramDnsQuery> buildQueryDecoder() {
            return buildFor(new DnsQueryFactory());
        }

        /**
         * Build a decoder which decodes messages to instances of DnsResponse.
         */
        public DnsMessageDecoder<DatagramDnsResponse> buildResponseDecoder() {
            return buildFor(new DnsResponseFactory());
        }

        public DnsMessageDecoder<DnsMessage<?>> buildQueryAndResponseDecoder() {
            return buildFor(new EitherFactory());
        }

        public MessageToMessageDecoder<DatagramPacket> buildUdpQueryAndResponseDecoder() {
            return new DatagramDnsQueryDecoder(buildQueryAndResponseDecoder());
        }

        /**
         * Build a decoder with a custom factory for creating DnsMessage
         * objects.
         */
        public <T extends DnsMessage<?>> DnsMessageDecoder<T> buildFor(DnsMessageFactory<T> msgs) {
            checkNotNull(msgs, "msgs");
            NameCodecFactory factory;
            if (!nameFeatures.isEmpty()) {
                factory = NameCodec.factory(nameFeatures.toArray(
                        new NameCodecFeature[nameFeatures.size()]));
            } else {
                factory = NameCodec.compressingFactory();
            }
            return new DnsMessageDecoder<T>(encoder, factory, msgs, policy, mdns);
        }

        /**
         * Create an encoder which will handle mDNS messages, decoding the high
         * bit of the DnsClass as a boolean describing unicast message handling.
         * Note that calling this automatically changes the NameCodec
         * configuration to MDNS_UTF_8 / COMPRESSION / WRITE_TRAILING_DOT, which
         * are required for mDNS.
         */
        public MessageDecoderBuilder mDNS() {
            this.mdns = true;
            nameFeatures.remove(NameCodecFeature.PUNYCODE);
            nameFeatures.add(NameCodecFeature.MDNS_UTF_8);
            nameFeatures.add(NameCodecFeature.COMPRESSION);
            nameFeatures.add(NameCodecFeature.WRITE_TRAILING_DOT);
            return this;
        }

        /**
         * Build an decoder suitable for use in a DNS client, decoding incoming
         * UDP packets as DNS queries.
         */
        public MessageToMessageDecoder<DatagramPacket> buildUdpResponseDecoder() {
            return new DatagramDnsResponseDecoder(buildResponseDecoder());
        }

        /**
         * Build a decoder suitable for use in a DNS server, decoding incoming
         * UDP packets as DNS queries.
         */
        public MessageToMessageDecoder<DatagramPacket> buildUdpQueryDecoder() {
            return new DatagramDnsQueryDecoder(buildQueryDecoder());
        }

        /**
         * Specifies what to do if a record is encountered where it does not
         * belong - for example, DNS queries may not contain answer records. To
         * be liberal with what you accept, choose IGNORE (which will silently
         * discard them) or INCLUDE to include them.
         */
        public MessageDecoderBuilder withIllegalRecordPolicy(IllegalRecordPolicy policy) {
            this.policy = checkNotNull(policy, "policy");
            return this;
        }

        /**
         * Provide a decoder for records within packets.
         */
        public MessageDecoderBuilder withRecordDecoder(DnsRecordDecoder encoder) {
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
        public MessageDecoderBuilder withNameFeatures(NameCodecFeature... features) {
            nameFeatures.addAll(Arrays.asList(features));
            return this;
        }
    }

    public M decode(ByteBuf content, InetSocketAddress sender, InetSocketAddress recipient) throws Exception {
        final M message = toMessage(sender, recipient, content);
        if (message == null) {
            content.release();
            return null;
        }
        boolean isQuery = !message.flags().contains(DnsMessageFlags.IS_REPLY);
        boolean success = false;
        NameCodec nameCodec = names.getForRead();
        try {
            final int questionCount = content.readUnsignedShort();
            final int answerCount = content.readUnsignedShort();
            final int authorityRecordCount = content.readUnsignedShort();
            final int additionalRecordCount = content.readUnsignedShort();

            decodeQuestions(message, content, questionCount, nameCodec);
            decodeRecords(message, DnsSection.ANSWER, content, answerCount, nameCodec, isQuery);
            decodeRecords(message, DnsSection.AUTHORITY, content, authorityRecordCount, nameCodec, isQuery);
            // If an OPT record is present, we may have an EDNS response code -
            // merge the high 8 bits of the timeToLive value of it with the
            // original response code as the low bits to form a 12-bit response
            // code, and replace the one we read before with that
            DnsRecord optRecord = decodeRecords(message, DnsSection.ADDITIONAL,
                    content, additionalRecordCount, nameCodec, isQuery);

            if (optRecord != null && message instanceof DnsResponse<?>) {
                DnsResponse<?> response = (DnsResponse<?>) message;
                // Mask off the bottom four bits
                int responseCodeLowBits = response.code().intValue() & 0xF;
                // Get bits 24 through 32 of the time-to-live value
                int responseCodeHighBits = (int) ((optRecord.timeToLive() >> 24) & 0xFF);
                if (responseCodeHighBits != 0) {
                    // OR the high bits shifted over by 4 bits with the original
                    // response code value , to be the upper 2 bytes of our 12-bit
                    // number
                    int newResponseCode = responseCodeLowBits | (responseCodeHighBits << 4);
                    DnsResponseCode newCode = DnsResponseCode.valueOf(newResponseCode);
                    // Replace the response code in the message
                    factory.updateResponseCode(newCode, message);
                }
            }
            success = true;
        } finally {
            nameCodec.close();
            if (!success) {
                message.release();
            }
        }
        return message;
    }

    private M toMessage(InetSocketAddress sender, InetSocketAddress recipient, ByteBuf buf) {
        final int id = buf.readUnsignedShort();
        short flags = buf.readShort();
        DnsMessageFlags.FlagSet flagSet = DnsMessageFlags.forFlags(flags);
        final M query = createMessage(sender, recipient, id,
                DnsOpCode.valueOf((byte) (flags >> 11 & 0xf)),
                DnsResponseCode.valueOf((byte) (flags & 0xf)),
                flagSet);

        query.setZ(flags >> 4 & 0x7);
        return query;
    }

    protected M createMessage(InetSocketAddress sender, InetSocketAddress recipient,
            int id, DnsOpCode opCode, DnsResponseCode responseCode, DnsMessageFlags.FlagSet flags) {
        return factory.createMessage(sender, recipient, id, opCode, responseCode, flags);
    }

    /**
     * Instantiates a message object for a DnsMessageDecoder.
     */
    public interface DnsMessageFactory<M extends DnsMessage> {

        M createMessage(InetSocketAddress sender, InetSocketAddress recipient,
                int id, DnsOpCode opCode, DnsResponseCode responseCode, DnsMessageFlags.FlagSet flags);

        void updateResponseCode(DnsResponseCode ednsResponseCode, M on);
    }

    static final class DnsQueryFactory implements DnsMessageFactory<DatagramDnsQuery> {

        @Override
        public DatagramDnsQuery createMessage(InetSocketAddress sender, InetSocketAddress recipient,
                int id, DnsOpCode opCode, DnsResponseCode responseCode, DnsMessageFlags.FlagSet flags) {
            if (flags.contains(IS_REPLY)) {
                throw new CorruptedFrameException("not a question - flags " + flags + " for id " + id + " from "
                        + sender + " to " + recipient);
            }
            return new DatagramDnsQuery(sender, recipient, id, opCode, flags);
        }

        @Override
        public void updateResponseCode(DnsResponseCode ednsResponseCode, DatagramDnsQuery on) {
            throw new UnsupportedOperationException("Queries do not have response codes.");
        }
    }

    static final class DnsResponseFactory implements DnsMessageFactory<DatagramDnsResponse> {

        @Override
        public DatagramDnsResponse createMessage(InetSocketAddress sender,
                InetSocketAddress recipient, int id, DnsOpCode opCode,
                DnsResponseCode responseCode, DnsMessageFlags.FlagSet flags) {
            if (!flags.contains(DnsMessageFlags.IS_REPLY)) {
                throw new CorruptedFrameException("not a response - " + flags
                        + " for id " + id + " from " + sender + " to "
                        + recipient);
            }
            return new DatagramDnsResponse(sender, null, id, opCode,
                    responseCode, flags);
        }

        @Override
        public void updateResponseCode(DnsResponseCode ednsResponseCode, DatagramDnsResponse on) {
            on.setCode(ednsResponseCode);
        }
    }

    static final class EitherFactory implements DnsMessageFactory<DnsMessage<?>> {

        DnsResponseFactory responses = new DnsResponseFactory();
        DnsQueryFactory queries = new DnsQueryFactory();

        @Override
        public DnsMessage<?> createMessage(InetSocketAddress sender, InetSocketAddress recipient,
                int id, DnsOpCode opCode, DnsResponseCode responseCode, DnsMessageFlags.FlagSet flags) {
            if (flags.contains(IS_REPLY)) {
                return responses.createMessage(sender, recipient, id, opCode, responseCode, flags);
            } else {
                return queries.createMessage(sender, recipient, id, opCode, responseCode, flags);
            }
        }

        @Override
        public void updateResponseCode(DnsResponseCode ednsResponseCode, DnsMessage on) {
            if (on instanceof DatagramDnsResponse) {
                ((DatagramDnsResponse) on).setCode(ednsResponseCode);
            }
        }
    }

    private void decodeQuestions(DnsMessage query, ByteBuf buf, int questionCount,
            NameCodec nameCodec) throws Exception {
        for (int i = 0; i < questionCount; i++) {
            DnsQuestion question = recordDecoder.decodeQuestion(buf, nameCodec);
            query.addRecord(DnsSection.QUESTION, question);
        }
    }

    private DnsRecord decodeRecords(DnsMessage query, DnsSection section, ByteBuf buf, int count,
            NameCodec nameCodec, boolean isQuery) throws Exception {
        DnsRecord optRecord = null;
        boolean optFound = false;
        for (int i = 0; i < count; i++) {
            DnsRecord record = recordDecoder.decodeRecord(buf, nameCodec);
            if (record != null) { // for compatibility
                boolean isOpt = OPT.equals(record.type());
                if (isOpt) {
                    optRecord = record;
                }
                if (isQuery && !mdns) {
                    switch (policy) {
                        case DISCARD:
                            if (section != DnsSection.ADDITIONAL) {
                                continue;
                            }
                            break;
                        case THROW:
                            switch (section) {
                                case ANSWER:
                                case AUTHORITY:
                                    throw new CorruptedFrameException("DNS queries "
                                            + "may not contain records in the "
                                            + section + " section, but found "
                                            + record);
                                case ADDITIONAL:
                                    if (!isOpt) {
                                        throw new CorruptedFrameException("Only OPT"
                                                + " records allowed in the " + section
                                                + " section of messages with an error "
                                                + "code, or query messages");
                                    } else if (optFound) {
                                        throw new CorruptedFrameException("More "
                                                + "than one OPT record found in "
                                                + "additional section: " + optRecord
                                                + " and " + record);
                                    }
                                default:
                                    break;
                            }
                            break;
                        case INCLUDE:
                            break;
                    }
                }
                optFound = isOpt;
                query.addRecord(section, record);
            }
        }
        return optRecord;
    }
}
