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
import io.netty.handler.codec.dns.DnsDecoderException;
import java.nio.charset.UnmappableCharacterException;

/**
 * NameCodec which does not write names using pointer compression - useful for
 * debugging packet content.
 */
class NonCompressingNameCodec extends NameCodec {

    //package private for tests
    private final boolean readTrailingDot;
    private final boolean writeTrailingDot;

    public NonCompressingNameCodec(boolean readTrailingDot, boolean writeTrailingDot) {
        this.readTrailingDot = readTrailingDot;
        this.writeTrailingDot = writeTrailingDot;
    }

    @Override
    public CharSequence readName(ByteBuf in) throws DnsDecoderException {
        return defaultReadName(in, readTrailingDot);
    }

    @Override
    public void writeName(CharSequence name, ByteBuf buf)
            throws UnmappableCharacterException, InvalidDomainNameException {
        checkName(name);
        writeName0(name, buf);
    }

    protected void write(ByteBuf buf, CharSequence label, int length) {
        buf.writeByte(length);
        ByteBufUtil.writeAscii(buf, label);
    }

    // Package private for test
    void writeName0(CharSequence name, ByteBuf buf) throws UnmappableCharacterException,
            InvalidDomainNameException {
        int max = name.length();
        if (name.length() == 0 || (name.length() == 1 && name.charAt(0) == '.')) {
            buf.writeByte(0);
            return;
        }
        int lastStart = 0;
        char c = 0;
        int length;
        for (int i = 0; i < max; i++) {
            if (i == max - 1 || (c = name.charAt(i)) == '.' || c == '@') {
                CharSequence label = name.subSequence(lastStart, i == max - 1 ? max : i);
                length = label.length();
                if (length == 0) {
                    continue;
                } else if (length > 63) {
                    throw new InvalidDomainNameException(name, "Dns cannot"
                            + "encode label longer than 63 chars. '"
                            + label + "' has " + label.length());
                }
                lastStart = i + 1;
                if (length == 0) {
                    continue;
                }
                if (length != 0) {
                    write(buf, label, length);
                }
                if (i == max - 1) {
                    // ----
                    // I am not sure this is spec-compliance so much as emulating the
                    // behavior of String.split(), but it makes the tests pass
                    int old = buf.readerIndex();
                    buf.readerIndex(buf.writerIndex() - 1);
                    if (buf.readByte() == '.') {
                        buf.readerIndex(old);
                        buf.writerIndex(buf.writerIndex() - 1);
                    } else {
                        buf.readerIndex(old);
                    }
                    // ----
                    if (writeTrailingDot) {
                        buf.writeByte(0);
                    }
                }
            }
        }
    }
}
