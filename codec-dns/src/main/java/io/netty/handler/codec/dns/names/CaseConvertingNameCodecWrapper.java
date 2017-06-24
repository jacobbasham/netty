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
import io.netty.util.AsciiString;
import io.netty.util.internal.StringUtil;
import java.nio.charset.UnmappableCharacterException;

final class CaseConvertingNameCodecWrapper extends NameCodec implements WrapperCodec {

    private final NameCodec delegate;

    CaseConvertingNameCodecWrapper(NameCodec delegate) {
        this.delegate = delegate;
    }

    private static CharSequence toLowerCase(CharSequence seq) {
        if (seq instanceof String) {
            return ((String) seq).toLowerCase();
        } else if (seq instanceof AsciiString) {
            return ((AsciiString) seq).toLowerCase();
        } else {
            return seq.toString().toLowerCase();
        }
    }

    @Override
    public void writeName(CharSequence name, ByteBuf into) throws UnmappableCharacterException,
            InvalidDomainNameException {
        delegate.writeName(toLowerCase(name), into);
    }

    @Override
    public boolean allowsWhitespace() {
        return delegate.allowsWhitespace();
    }

    @Override
    public CharSequence readName(ByteBuf in) throws DnsDecoderException {
        return toLowerCase(delegate.readName(in));
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
        return StringUtil.simpleClassName(this) + "{" + delegate + "}";
    }

    @Override
    public boolean convertsCase() {
        return true;
    }

    @Override
    public boolean readsTrailingDot() {
        return delegate.readsTrailingDot();
    }

    @Override
    public boolean writesTrailingDot() {
        return delegate.writesTrailingDot();
    }

    @Override
    public boolean writesWithPointerCompression() {
        return delegate.writesWithPointerCompression();
    }

    @Override
    public NameCodec delegate() {
        return delegate;
    }
}
