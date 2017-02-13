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

/**
 * The default {@link DnsRecordDecoder} implementation.
 *
 * @see DefaultDnsRecordEncoder
 */
public class DefaultDnsRecordDecoder implements DnsRecordDecoder {

    @Override
    public final DnsQuestion decodeQuestion(ByteBuf in, NameCodec forReadingNames) throws Exception {
        CharSequence name = forReadingNames.readName(in);
        DnsRecordType type = DnsRecordType.valueOf(in.readUnsignedShort());
        int qClass = in.readUnsignedShort();
        return new DefaultDnsQuestion(name, type, DnsClass.valueOf(qClass));
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
        final int aClass = in.readUnsignedShort();
        final long ttl = in.readUnsignedInt();
        final int length = in.readUnsignedShort();
        final int offset = in.readerIndex();

        if (endOffset - offset < length) {
            // Not enough data
            in.readerIndex(startOffset);
            return null;
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

        return new DefaultDnsRawRecord(
                name, type, DnsClass.valueOf(dnsClass), timeToLive,
                in.duplicate().setIndex(offset, offset + length).retain());
    }
}
