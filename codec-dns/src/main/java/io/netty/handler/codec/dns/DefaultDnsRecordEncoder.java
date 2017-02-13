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
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.util.internal.StringUtil;

/**
 * The default {@link DnsRecordEncoder} implementation.
 *
 * @see DefaultDnsRecordDecoder
 */
public class DefaultDnsRecordEncoder implements DnsRecordEncoder {

    /**
     * Creates a new instance.
     */
    protected DefaultDnsRecordEncoder() {
    }

    @Override
    public final void encodeQuestion(NameCodec nameWriter, DnsQuestion question, ByteBuf out) throws Exception {
        nameWriter.writeName(question.name(), out);
        out.writeShort(question.type().intValue());
        out.writeShort(question.dnsClass().intValue());
    }

    @Override
    public void encodeRecord(NameCodec nameWriter, DnsRecord record, ByteBuf out) throws Exception {
        if (record instanceof DnsQuestion) {
            encodeQuestion(nameWriter, (DnsQuestion) record, out);
        } else if (record instanceof DnsRawRecord) {
            encodeRawRecord(nameWriter, (DnsRawRecord) record, out);
        } else {
            throw new UnsupportedMessageTypeException(StringUtil.simpleClassName(record));
        }
    }

    private void encodeRawRecord(NameCodec nameWriter, DnsRawRecord record, ByteBuf out) throws Exception {
        nameWriter.writeName(record.name(), out);

        out.writeShort(record.type().intValue());
        out.writeShort(record.dnsClass().intValue());
        out.writeInt((int) record.timeToLive());

        ByteBuf content = record.content();
        int contentLen = content.readableBytes();

        out.writeShort(contentLen);
        out.writeBytes(content, content.readerIndex(), contentLen);
    }
}
