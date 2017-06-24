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
package io.netty.handler.codec.dns.names;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

/**
 * UTF-8 name codec for mDNS, which uses plain UTF-8 for names with no
 * compression or special sauce.
 */
final class Utf8NonCompressingCodec extends NonCompressingNameCodec implements NameCodecFactory {

    Utf8NonCompressingCodec(boolean readTrailingDot, boolean writeTrailingDot) {
        super(readTrailingDot, writeTrailingDot);
    }

    @Override
    protected void checkName(CharSequence name) throws UnencodableCharactersException, InvalidDomainNameException {
        // do nothing
    }

    static void writeUtf8(ByteBuf buf, CharSequence label) {
        int lengthPosition = buf.writerIndex();
        buf.writeByte(0);
        int bytesWritten = ByteBufUtil.writeUtf8(buf, label);
        if (bytesWritten > 63) {
            buf.writerIndex(lengthPosition);
            throw new InvalidDomainNameException(label,
                    "Unicode label encodes to > 63 bytes: '" + label + "'");
        }
        int end = buf.writerIndex();
        buf.writerIndex(lengthPosition);
        buf.writeByte(bytesWritten);
        buf.writerIndex(end);
    }

    @Override
    protected void write(ByteBuf buf, CharSequence label, int length) {
        writeUtf8(buf, label);
    }

    @Override
    public boolean supportsUnicode() {
        return true;
    }

    @Override
    public NameCodec getForRead() {
        return this;
    }

    @Override
    public NameCodec getForWrite() {
        return this;
    }

    @Override
    public boolean allowsWhitespace() {
        return true;
    }
}
