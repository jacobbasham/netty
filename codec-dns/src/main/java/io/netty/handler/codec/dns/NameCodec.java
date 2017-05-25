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
package io.netty.handler.codec.dns;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import static io.netty.handler.codec.dns.NameCodec.Feature.COMPRESSION;
import static io.netty.handler.codec.dns.NameCodec.Feature.PUNYCODE;
import static io.netty.handler.codec.dns.NameCodec.Feature.READ_TRAILING_DOT;
import static io.netty.handler.codec.dns.NameCodec.Feature.WRITE_TRAILING_DOT;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.UnstableApi;
import java.net.IDN;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.UnmappableCharacterException;

/**
 * Reads and writes DNS names optionally using pointer compression. Note that
 * the name codecs defined in this package do <i>not</i> support non-ascii
 * characters - it is suggested to write a NameCodec which supports punycode and
 * wraps one of these if you want that.
 * <p>
 * Writers with and without pointer compression are supported; generally the
 * compressing version should be used unless you are debugging data on the wire
 * and want to make it more human-readable.
 * </p><p>
 * The standard (no compression) NameWriter is stateless; the compressing
 * NameWriter needs its state cleared after use, and implements AutoCloseable so
 * it can be used in a try-with-resources structure.
 * </p>
 */
@UnstableApi
public abstract class NameCodec implements AutoCloseable {

    static final AsciiString ROOT = new AsciiString(".");
    private static NameCodec DEFAULT = new DefaultNameCodec(false, true);
    private static NameCodec DEFAULT_TRAILING_DOT = new DefaultNameCodec(true, true);

    public static enum Feature {
        /**
         * Use DNS name pointer compression. Unless you are debugging the wire
         * format, you almost always want this as it reduces packet size.
         */
        COMPRESSION,
        /**
         * Translate punycode into unicode. This forces the use of
         * {@link java.lang.String} instead of
         * {@link io.netty.util.AsciiString}, doubling the memory footprint of
         * names. If you are writing something like a caching server, where you
         * are not displaying the domain names directly, just forwarding them,
         * leave this unset and - clients will do the encoding and decoding if
         * needed.
         */
        PUNYCODE,
        /**
         * Write the trailing 0 on names in DNS packets. This is almost always
         * wanted, as according to the spec, this makes the final label (such as
         * "com") a child of the
         * <i>root domain</i> of the internet, named "".
         */
        WRITE_TRAILING_DOT,
        /**
         * Read and append the trailing dot when reading names. This is also
         * part of the DNS spec (assuming the incoming name is indeed fully
         * qualified), but if you are displaying names to a user unfamiliar with
         * the spec, may look like a bug.
         */
        READ_TRAILING_DOT
    }

    public static NameCodec get(Feature... features) {
        boolean compression = false;
        boolean punycode = false;
        boolean readTrailingDot = false;
        boolean writeTrailingDot = false;
        for (Feature f : features) {
            if (f == PUNYCODE) {
                punycode = true;
            }
            if (f == COMPRESSION) {
                compression = true;
            }
            if (f == READ_TRAILING_DOT) {
                readTrailingDot = true;
            }
            if (f == WRITE_TRAILING_DOT) {
                writeTrailingDot = true;
            }
        }
        NameCodec result = compression ? new CompressingNameCodec(readTrailingDot,
                writeTrailingDot) : readTrailingDot ? DEFAULT_TRAILING_DOT : DEFAULT;
        if (punycode) {
            result = new IdnNameCodec(result);
        }
        return result;
    }

    public static Factory factory(Feature... features) {
        boolean compression = false;
        boolean punycode = false;
        for (Feature f : features) {
            if (f == PUNYCODE) {
                punycode = true;
            }
            if (f == COMPRESSION) {
                compression = true;
            }
        }
        Factory result = compression ? compressingFactory() : standardFactory();
        if (punycode) {
            result = new IdnFactory(result);
        }
        return result;
    }

    private static class IdnFactory implements Factory {

        private final Factory delegate;
        private final NameCodec forReads;

        public IdnFactory(Factory delegate) {
            this.delegate = delegate;
            forReads = new IdnNameCodec(delegate.getForRead());
        }

        @Override
        public NameCodec getForRead() {
            return forReads;
        }

        @Override
        public NameCodec getForWrite() {
            return new IdnNameCodec(delegate.getForWrite());
        }
    }

    public NameCodec toPunycodeNameCodec() {
        return this instanceof IdnNameCodec ? this : new IdnNameCodec(this);
    }

    /**
     * Write a name into the passed buffer.
     *
     * @param name The name
     * @param into The buffer
     * @throws UnmappableCharacterException, InvalidDomainNameException
     */
    public abstract void writeName(CharSequence name, ByteBuf into) throws UnmappableCharacterException,
            InvalidDomainNameException;

    /**
     * Get a NameCodec that does not use DNS name compression.
     *
     * @return A non-compressing NameCodec
     */
    public static NameCodec basicNameCodec() {
        return DEFAULT;
    }

    /**
     * Get a NameCodec which will compress names according to the RFC. Note that
     * the returned NameCodec is stateful; its <code>close()</code> method
     * should be called if it is to be reused against a different buffer.
     *
     * @return A compressing NameCodec
     */
    public static NameCodec compressingNameCodec() {
        return new CompressingNameCodec(false, true);
    }

    /**
     * Clear any state associated with this NameCodec, if it is to be reused.
     */
    @Override
    public void close() {
        //do nothing
    }

    /**
     * Factory for NameCodecs that uses ThreadLocals to ensure that if the
     * result is stateful, then it is not shared across threads.
     */
    public interface Factory {

        /**
         * Get a NameCodec for reading. The result of this call may not be used
         * for writing, and may throw an exception to prevent that.
         *
         * @return A namewriter
         */
        NameCodec getForRead();

        /**
         * Get a NameCodec for writing. The result of this call may not be used
         * for reading, and may throw an exception to prevent that.
         *
         * @return A namewriter
         */
        NameCodec getForWrite();
    }

    /**
     * Get a factory for name codec with compression.
     *
     * @return A factory
     */
    public static Factory compressingFactory() {
        return new CompressingFactory(false, true);
    }

    /**
     * Get a factory for name codecs with compression.
     *
     * @return A factory
     */
    public static Factory standardFactory() {
        return new StandardFactory();
    }

    private static final class ReadOrWriteWrapper extends NameCodec {

        private final NameCodec delegate;
        private final boolean isWrite;

        public ReadOrWriteWrapper(NameCodec delegate, boolean isWrite) {
            this.delegate = delegate;
            this.isWrite = isWrite;
        }

        @Override
        public void writeName(CharSequence name, ByteBuf into) throws UnmappableCharacterException,
                InvalidDomainNameException {
            if (!isWrite) {
                throw new IllegalStateException("This " + NameCodec.class.getSimpleName() + " was obtained from "
                        + "getForRead() - cannot be used for writing.  Get one from the factories getForWrite() method"
                        + " instead.");
            }
            delegate.writeName(name, into);
        }

        @Override
        public CharSequence readName(ByteBuf in) throws DnsDecoderException {
            if (isWrite) {
                throw new IllegalStateException("This " + NameCodec.class.getSimpleName() + " was obtained from "
                        + "getForRead() - cannot be used for writing.  Get one from the factories getForWrite() method"
                        + " instead.");
            }
            return delegate.readName(in);
        }
    }

    private static final class StandardFactory implements Factory {

        private final NameCodec readInstance = new ReadOrWriteWrapper(DEFAULT, false);
        private final NameCodec writeInstance = new ReadOrWriteWrapper(DEFAULT, true);

        @Override
        public NameCodec getForRead() {
            return readInstance;
        }

        @Override
        public NameCodec getForWrite() {
            return writeInstance;
        }
    }

    private static final class CompressingFactory implements Factory {

        private final FastThreadLocal<NameCodec> writer = new FastThreadLocal<NameCodec>();
        // StandardNameWriter and CompressingNameCodec delegate to the same defaultRead()
        // method and have no state when reading
        private final NameCodec readInstance;
        private final boolean writeTrailingDot;

        CompressingFactory(boolean readTrailingDot, boolean writeTrailingDot) {
            readInstance = new ReadOrWriteWrapper(readTrailingDot ? DEFAULT_TRAILING_DOT
                    : DEFAULT, false);
            this.writeTrailingDot = writeTrailingDot;
        }

        public NameCodec getForRead() {
            return readInstance;
        }

        @Override
        public NameCodec getForWrite() {
            NameCodec result;
            if (!writer.isSet()) {
                result = new ReadOrWriteWrapper(new CompressingNameCodec(
                        readInstance == DEFAULT_TRAILING_DOT, writeTrailingDot),
                        true);
                writer.set(result);
            } else {
                result = writer.get();
            }
            return result;
        }
    }

    /**
     * Read a name from a buffer. Assumes the buffer is positioned at the
     * initial byte of a name (which will be a label length). Retrieves a domain
     * name given a buffer containing a DNS packet. If the name contains a
     * pointer, the position of the buffer will be set to directly after the
     * pointer's index after the name has been read.
     * <p>
     * The default implementation can decode DNS pointers, but does not
     * interpret punycode encoding of non-ascii characters.
     *
     * @param in the byte buffer containing the DNS packet
     * @return the domain name for an entry
     *
     * @throws DnsDecoderException if the data is invalid
     */
    public CharSequence readName(ByteBuf in) throws DnsDecoderException {
        return defaultReadName(in, false);
    }

    /**
     * Convenience method to read a name from a buffer. Assumes the buffer is
     * positioned at the initial byte of a name (which will be a label length).
     * Retrieves a domain name given a buffer containing a DNS packet. If the
     * name contains a pointer, the position of the buffer will be set to
     * directly after the pointer's index after the name has been read.
     *
     * @param in the byte buffer containing the DNS packet
     * @return the domain name for an entry
     *
     * @param buf The buffer
     * @return A name
     * @throws DnsDecoderException
     */
    protected static CharSequence defaultReadName(ByteBuf buf, boolean trailingDot) throws DnsDecoderException {
        if (buf.readableBytes() == 0) {
            return ROOT;
        }
        if (buf.readableBytes() == 1) {
            byte r = buf.readByte();
            if (r == 0) {
                return ROOT;
            }
            throw new DnsDecoderException(DnsResponseCode.FORMERR, "The only valid value of a 1-byte name "
                    + "buffer is 0 but found " + r + " (" + (char) r + ")");
        }
        int position = -1;
        int checked = 0;
        int length = buf.writerIndex();
        boolean first = true;
        // See http://blogs.msdn.com/b/oldnewthing/archive/2012/04/12/10292868.aspx for why 253
        ByteBuf name = Unpooled.buffer(64, 253);
        try {
            for (int len = buf.readUnsignedByte(); buf.isReadable() && len != 0; len = buf.readUnsignedByte()) {
                if (len == 0 && name.readableBytes() == 0) {
                    return ROOT;
                }
                boolean pointer = (len & 0xc0) == 0xc0;
                if (pointer) {
                    if (position == -1) {
                        position = buf.readerIndex() + 1;
                    }
                    if (!buf.isReadable()) {
                        throw new DnsDecoderException(DnsResponseCode.FORMERR, "truncated pointer in a name at "
                                + buf.readerIndex() + " after '" + new AsciiString(name.array(), 0,
                                name.readableBytes(), false) + "'");
                    }
                    final int next = (len & 0x3f) << 8 | buf.readUnsignedByte();
                    if (next >= length) {
                        throw new DnsDecoderException(DnsResponseCode.FORMERR, "name has an "
                                + "out-of-range pointer at "
                                + buf.readerIndex() + " after '" + new AsciiString(name.array(), 0,
                                name.readableBytes(), false) + "'");
                    }
                    buf.readerIndex(next);
                    // check for loops
                    checked += 2;
                    if (checked >= length) {
                        throw new DnsDecoderException(DnsResponseCode.FORMERR, "Name contains a loop at "
                                + buf.readerIndex() + " after '" + new AsciiString(name.array(), 0,
                                name.readableBytes(), false) + "'");
                    }
                } else if (len != 0) {
                    if (!buf.isReadable(len)) {
                        throw new DnsDecoderException(DnsResponseCode.FORMERR, "truncated label in a name after "
                                + new AsciiString(name.array()));
                    }
                    if (len > 63) {
                        throw new DnsDecoderException(DnsResponseCode.BADNAME, "Label length " + len
                                + " but max DNS label length is 63 bytes at " + buf.readerIndex());
                    }
                    if (!first) {
                        name.writeByte((byte) '.');
                    }
                    name.writeBytes(buf, len);
                    first = false;
                }
                if (buf.readableBytes() == 0) {
                    break;
                }
            }
        } catch (IndexOutOfBoundsException ex) {
            throw new DnsDecoderException(DnsResponseCode.BADNAME, "Name is longer than 253 characters at "
                    + buf.readerIndex() + " decoded: "
                    + new AsciiString(name.array(), 0, name.readableBytes(), false), ex);
        }
        if (position != -1) {
            buf.readerIndex(position);
        }
        if (name.writerIndex() == 0) {
            return ROOT;
        }
        if (trailingDot) {
            name.readerIndex(name.writerIndex() - 1);
            if (name.readByte() != '.') {
                name.writeByte('.');
            }
            name.readerIndex(0);
        }
        return new AsciiString(name.array(), 0, name.readableBytes(), false);
    }

    static final CharsetEncoder enc = CharsetUtil.US_ASCII.newEncoder();

    protected void checkName(CharSequence name) throws UnencodableCharactersException,
            InvalidDomainNameException {
        int length = name.length();
        if (length > 253) {
            throw new InvalidDomainNameException(name, "Name length must be <= 253");
        }
        if (!enc.canEncode(name)) {
            throw new UnencodableCharactersException(name);
        }
        int lastLabelStart = 0;
        for (int i = 0; i < length; i++) {
            char c = name.charAt(i);
            if (!legalCharacter(c, i - lastLabelStart)) {
                throw new InvalidDomainNameException(name, "Name contains illegal character '" + c
                        + "' at " + i + ": '" + name + "'");
            }
            if (c == '.' || c == '@') {
                lastLabelStart = i + 1;
            }
        }
    }

    private boolean legalCharacter(char c, int index) {
        boolean result = (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '-'
                || c == '.';
        if (result && index == 0 && c == '-') { // Hyphen is legal, but not as leading char
            result = false;
        }
        return result;
    }

    static final class DefaultNameCodec extends NameCodec { //package private for tests

        private final boolean readTrailingDot;
        private final boolean writeTrailingDot;

        public DefaultNameCodec(boolean readTrailingDot, boolean writeTrailingDot) {
            this.readTrailingDot = readTrailingDot;
            this.writeTrailingDot = writeTrailingDot;
        }

        @Override
        public CharSequence readName(ByteBuf in) throws DnsDecoderException {
            return defaultReadName(in, readTrailingDot);
        }

        @Override
        public void writeName(CharSequence name, ByteBuf buf) throws UnmappableCharacterException,
                InvalidDomainNameException {
            checkName(name);
            writeName0(name, buf);
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
                        throw new InvalidDomainNameException(name, "Dns cannot encode label longer than 63 chars. '"
                                + label + "' has " + label.length());
                    }
                    lastStart = i + 1;
                    if (length == 0) {
                        continue;
                    }
                    if (length != 0) {
                        buf.writeByte(length);
                        ByteBufUtil.writeAscii(buf, label);
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

    static class UnencodableCharactersException extends UnmappableCharacterException {

        private final CharSequence name;

        public UnencodableCharactersException(CharSequence name) {
            super(name.length());
            this.name = name;
        }

        @Override
        public String getMessage() {
            return "Name contains non-ascii character - convert it to punycode first: '" + name + "'";
        }
    }

    private static final class IdnNameCodec extends NameCodec {

        private final NameCodec delegate;

        public IdnNameCodec(NameCodec delegate) {
            this.delegate = delegate;
        }

        @Override
        public CharSequence readName(ByteBuf in) throws DnsDecoderException {
            return IDN.toUnicode(delegate.readName(in).toString());
        }

        @Override
        public void writeName(CharSequence name, ByteBuf into)
                throws UnmappableCharacterException, InvalidDomainNameException {
            delegate.writeName(IDN.toASCII(name.toString()), into);
        }
    }
}
