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

import io.netty.handler.codec.dns.names.NameCodec;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.CorruptedFrameException;
import static io.netty.handler.codec.dns.DnsRecordDecoder.UnderflowPolicy.THROW_ON_UNDERFLOW;

/**
 * The default {@link DnsRecordDecoder} implementation.
 *
 * @see DefaultDnsRecordEncoder
 */
public class DefaultDnsRecordDecoder implements DnsRecordDecoder {

    private final UnderflowPolicy policy;
    protected final boolean mdns;

    public DefaultDnsRecordDecoder() {
        this(THROW_ON_UNDERFLOW, false);
    }

    public DefaultDnsRecordDecoder(UnderflowPolicy policy, boolean mdns) {
        this.policy = policy;
        this.mdns = mdns;
    }

    @Override
    public final DnsQuestion decodeQuestion(ByteBuf in, NameCodec forReadingNames) throws Exception {
        CharSequence name = forReadingNames.readName(in);
        DnsRecordType type = DnsRecordType.valueOf(in.readUnsignedShort());
        int qClass = in.readUnsignedShort();
        boolean isUnicastResponsePreferred = false;
        if (mdns) {
            isUnicastResponsePreferred = (qClass & MDNS_UNICAST_RESPONSE_BIT) != 0;
            qClass &= MDNS_DNS_CLASS_MASK;
        }
        return new DefaultDnsQuestion(name, type, qClass, isUnicastResponsePreferred);
    }

    @Override
    public final DnsRecord decodeRecord(ByteBuf in, NameCodec forReadingNames) throws Exception {
        final int startOffset = in.readerIndex();
        final CharSequence name = forReadingNames.readName(in);

        final int endOffset = in.writerIndex();
        if (endOffset - startOffset < 10) {
            // Not enough data
            in.readerIndex(startOffset);
            return null;
        }

        final DnsRecordType type = DnsRecordType.valueOf(in.readUnsignedShort());
        int aClass = in.readUnsignedShort();

        final long ttl = in.readUnsignedInt();
        final int length = in.readUnsignedShort();
        final int offset = in.readerIndex();

        if (endOffset - offset < length) {
            // Not enough data
            in.readerIndex(startOffset);
            if (policy == THROW_ON_UNDERFLOW) {
                throw new CorruptedFrameException("Insufficient data "
                        + (endOffset - offset)
                    + " remaining bytes but length should be " + length + " at "
                    + startOffset);
            } else {
                return null;
            }
        }

        @SuppressWarnings("unchecked")
        DnsRecord record = decodeRecord(name, type, aClass, ttl, in, offset, length, forReadingNames);
        in.readerIndex(offset + length);
        return record;
    }

    /**
     * Decodes a record from the information decoded so far by {@link #decodeRecord(ByteBuf)}.
     *
     * @param name the domain name of the record
     * @param type the type of the record
     * @param dnsClass the class of the record
     * @param timeToLive the TTL of the record
     * @param in the {@link ByteBuf} that contains the RDATA
     * @param offset the start offset of the RDATA in {@code in}
     * @param length the length of the RDATA
     * @param forReadingNames used by some decoders to decode DNS names with pointer compression or
     * punycode
     *
     * @return a {@link DnsRawRecord}. Override this method to decode RDATA and return other record implementation.
     */
    protected DnsRecord decodeRecord(
            CharSequence name, DnsRecordType type, int dnsClass, long timeToLive,
            ByteBuf in, int offset, int length, NameCodec forReadingNames) throws Exception {
        boolean isUnicastResponse = false;
        if (mdns) {
            isUnicastResponse = (dnsClass & MDNS_UNICAST_RESPONSE_BIT) != 0;
            dnsClass = dnsClass & MDNS_DNS_CLASS_MASK;
        }

        // DNS message compression means that domain names may contain "pointers" to other positions in the packet
        // to build a full message. This means the indexes are meaningful and we need the ability to reference the
        // indexes un-obstructed, and thus we cannot use a slice here.
        // See https://www.ietf.org/rfc/rfc1035 [4.1.4. Message compression]
        if (type == DnsRecordType.PTR) { // XXX deleteme
            return new DefaultDnsPtrRecord(
                    name.toString(), DnsClass.valueOf(dnsClass), timeToLive,
                    // XXX duplicating the buffer is silly, but a bunch of tests depend on it
                    // We are passing in the buffer at the correct location already
                    forReadingNames.readName(in.duplicate().setIndex(offset, offset + length)),
                    isUnicastResponse
            );
        }
        return new DefaultDnsRawRecord(
                name, type, dnsClass, timeToLive,
                in.retainedDuplicate().setIndex(offset, offset + length).retain(),
                isUnicastResponse
        );
    }
}
