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
import io.netty.handler.codec.dns.DnsDecoderException;
import io.netty.util.internal.StringUtil;
import java.nio.charset.UnmappableCharacterException;

/**
 * Wrapper for NameCodec which enforces that any NameCodec retrieved from
 * NameCodecFactory.getForWrite() is not used to read, and vice-versa. This
 * simply makes an easy-to-have category of bug impossible.
 */
final class WriteEnforcingCodecWrapper extends NameCodec {

    private final NameCodec delegate;
    private final boolean isWrite;

    public WriteEnforcingCodecWrapper(NameCodec delegate, boolean isWrite) {
        this.delegate = delegate;
        this.isWrite = isWrite;
    }

    @Override
    public void writeName(CharSequence name, ByteBuf into)
            throws UnmappableCharacterException, InvalidDomainNameException {
        if (!isWrite) {
            throw new IllegalStateException("This "
                    + StringUtil.simpleClassName(NameCodec.class) + " was obtained from "
                    + "getForRead() - cannot be used for writing.  Get one from "
                    + "the factory's getForWrite() method instead.");
        }
        delegate.writeName(name, into);
    }

    @Override
    public CharSequence readName(ByteBuf in) throws DnsDecoderException {
        if (isWrite) {
            throw new IllegalStateException("This " + StringUtil.simpleClassName(NameCodec.class)
                    + " was obtained from getForRead() - cannot be used for writing. "
                    + "Get one from the factories getForWrite() method" + " instead.");
        }
        return delegate.readName(in);
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public boolean supportsUnicode() {
        return delegate.supportsUnicode();
    }

    @Override
    public String toString() {
        return "WriteEnforcingCodecWrapper{" + delegate
                + ", isWrite=" + isWrite + "}";
    }

}
