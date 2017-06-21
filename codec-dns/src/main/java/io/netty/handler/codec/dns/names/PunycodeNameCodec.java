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
import java.net.IDN;
import java.nio.charset.UnmappableCharacterException;

/**
 * NameCodec which wraps another and decodes ASCII-punycode to unicode.
 */
final class PunycodeNameCodec extends NameCodec {

    private final NameCodec delegate;

    public PunycodeNameCodec(NameCodec delegate) {
        this.delegate = delegate;
    }

    @Override
    public CharSequence readName(ByteBuf in) throws DnsDecoderException {
        CharSequence ascii = delegate.readName(in);
        String result = IDN.toUnicode(ascii.toString());
        return result;
    }

    @Override
    public void writeName(CharSequence name, ByteBuf into) throws
            UnmappableCharacterException, InvalidDomainNameException {
        if (name.toString().contains("?")) {
            throw new IllegalArgumentException("Missing non-ascii chars? : "
                    + name + " in a " + name.getClass().getName());
        }
        String ascii = IDN.toASCII(name.toString());
        delegate.writeName(ascii, into);
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public boolean supportsUnicode() {
        return true;
    }

    @Override
    public String toString() {
        return "PunycodeNameCodec{" + delegate + "}";
    }
}
