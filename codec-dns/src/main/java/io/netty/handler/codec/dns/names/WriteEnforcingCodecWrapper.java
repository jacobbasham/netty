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
final class WriteEnforcingCodecWrapper extends NameCodec implements WrapperCodec {

    private final NameCodec delegate;
    private final boolean writesAllowed;
    private final CachingNameCodecFactory owner;
    private final Thread creationThread;

    WriteEnforcingCodecWrapper(NameCodec delegate) {
        this(delegate, false, null);
    }

    WriteEnforcingCodecWrapper(NameCodec delegate, boolean writesAllowed, CachingNameCodecFactory owner) {
        this.delegate = delegate;
        this.writesAllowed = writesAllowed;
        this.owner = owner;
        creationThread = writesAllowed ? Thread.currentThread() : null;
    }

    @Override
    public void writeName(CharSequence name, ByteBuf into)
            throws UnmappableCharacterException, InvalidDomainNameException {
        if (!writesAllowed) {
            throw new IllegalStateException("This "
                    + StringUtil.simpleClassName(NameCodec.class) + " was obtained from "
                    + "NameCodecFactory.getForRead() - cannot be used for writing.  Get one from "
                    + "the factory's getForWrite() method instead.");
        }
        delegate.writeName(name, into);
    }

    @Override
    public CharSequence readName(ByteBuf in) throws DnsDecoderException {
        if (writesAllowed) {
            throw new IllegalStateException("This " + StringUtil.simpleClassName(NameCodec.class)
                    + " was obtained from NameCodecFactory.getForRead() - cannot be used for writing. "
                    + "Get one from the factories getForWrite() method" + " instead.");
        }
        return delegate.readName(in);
    }

    @Override
    public void close() {
        delegate.close();
        // Only write instances are held in a threadlocal in CachingNameCodecFactory
        // If we are being closed on some random thread, we will wind up in the wrong
        // slot (and something bad is probably happening)
        if (owner != null && Thread.currentThread() == creationThread) {
            owner.onClose(this);
        }
    }

    @Override
    public boolean supportsUnicode() {
        return delegate.supportsUnicode();
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + "{" + delegate + ", isWriteInstance=" + writesAllowed + "}";
    }

    @Override
    public boolean allowsWhitespace() {
        return delegate.allowsWhitespace();
    }

    @Override
    public boolean convertsCase() {
        return delegate.convertsCase();
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
