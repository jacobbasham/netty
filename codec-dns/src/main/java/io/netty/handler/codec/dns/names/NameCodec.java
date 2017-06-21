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
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.dns.DnsDecoderException;
import io.netty.handler.codec.dns.DnsResponseCode;
import static io.netty.handler.codec.dns.names.NameCodecFeature.COMPRESSION;
import static io.netty.handler.codec.dns.names.NameCodecFeature.MDNS_UTF_8;
import static io.netty.handler.codec.dns.names.NameCodecFeature.PUNYCODE;
import static io.netty.handler.codec.dns.names.NameCodecFeature.READ_TRAILING_DOT;
import static io.netty.handler.codec.dns.names.NameCodecFeature.WRITE_TRAILING_DOT;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.UnstableApi;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.UnmappableCharacterException;
import java.util.Set;

/**
 * Reads and writes DNS names optionally using pointer compression and/or
 * punycode.
 * <p>
 * Writers with and without pointer compression are supported; generally the
 * compressing version should be used unless you are debugging data on the wire.
 * <p>
 * The basic (no compression) NameWriter is stateless; the compressing
 * NameWriter needs its state cleared after use; NameCodec implements
 * AutoCloseable so it can be used in a try-with-resources structure.
 * <p>
 * NameCodec reads and writes <code>CharSequence</code>. In the case of
 * non-punycode codecs, the return values will likely be
 * <code>AsciiString</code>, using 8-bit internal data structures instead of
 * Java's two-byte chars. Punycode-supporting encoders use String.
 * <p>
 * The choice of what to use is based on your application - for example, if you
 * are storing a lot of DNS records in memory, you are better off storing them
 * in un-decoded punycode at 8 bits per character than decoding punycode to
 * 16-bit characters (the results will look the same on the wire either way). On
 * the other hand, if you are displaying them in a UI, full unicode is likely
 * what you want.
 * <p>
 * The {@code WRITE_TRAILING_DOT} feature is worth being careful with - it is
 * handy to <i>read</i> names without the trailing dot, but if you disable
 * writing the trailing dot for data on the wire, you will generate invalid
 * packets.
 */
@UnstableApi
public abstract class NameCodec implements AutoCloseable {

    static final AsciiString ROOT = new AsciiString(".");
    static final NameCodec DEFAULT = new NonCompressingNameCodec(false, true);
    static final NameCodec DEFAULT_TRAILING_DOT = new NonCompressingNameCodec(true, true);
    static final CharsetEncoder ASCII_ENCODER = CharsetUtil.US_ASCII.newEncoder();
//    static final Utf8NonCompressingCodec MDNS_CODEC = new Utf8NonCompressingCodec();

    /**
     * Get a one-off NameCodec that has the supplied list of features. Passing
     * nothing gets you a non-compression-supporting, ASCII-only NameCodec.
     *
     * @param features The features you need.
     * @return A codec
     */
    public static NameCodec get(NameCodecFeature... features) {
        boolean compression = false;
        boolean punycode = false;
        boolean readTrailingDot = false;
        boolean writeTrailingDot = false;
        boolean utf8 = false;
        NameCodecFeature.validate(features);
        for (NameCodecFeature f : features) {
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
            if (f == MDNS_UTF_8) {
                utf8 = true;
            }
        }
        if (utf8) {
            return compression ? new Utf8CompressingCodec(readTrailingDot, writeTrailingDot)
                    : new Utf8NonCompressingCodec(readTrailingDot, writeTrailingDot);
        }
        NameCodec result = compression ? new CompressingNameCodec(readTrailingDot,
                writeTrailingDot) : readTrailingDot ? DEFAULT_TRAILING_DOT : DEFAULT;
        if (punycode) {
            result = new PunycodeNameCodec(result);
        }
        return result;
    }

    /**
     * Get a factory for NameCodecs that have the feature list passed here.
     *
     * @param features The features
     * @return A factory for name codecs
     */
    public static NameCodecFactory factory(NameCodecFeature... features) {
        Set<NameCodecFeature> all = NameCodecFeature.validate(features);
        NameCodecFactory result = all.contains(MDNS_UTF_8)
                ? new CachingNameCodecFactory(new TrivialUtf8NameCodecFactory(
                        all.contains(COMPRESSION), all.contains(READ_TRAILING_DOT),
                        all.contains(WRITE_TRAILING_DOT)))
                : all.contains(COMPRESSION)
                ? new CompressingNameCodecFactory(all.contains(READ_TRAILING_DOT),
                        all.contains(WRITE_TRAILING_DOT))
                : new UncompressedNameCodecFactory(all.contains(READ_TRAILING_DOT),
                        all.contains(WRITE_TRAILING_DOT));
        if (all.contains(PUNYCODE)) { // if UTF-8 we will already have thrown in validate()
            result = new PunycodeNameCodecFactory(result);
        }
        return result;
    }

    static final class TrivialUtf8NameCodecFactory implements NameCodecFactory {

        private final boolean compression;
        private final boolean readTrailingDot;
        private final boolean writeTrailingDot;

        public TrivialUtf8NameCodecFactory(boolean compression, boolean readTrailingDot, boolean writeTrailingDot) {
            this.compression = compression;
            this.readTrailingDot = readTrailingDot;
            this.writeTrailingDot = writeTrailingDot;
        }

        @Override
        public NameCodec getForRead() {
            return compression ? new Utf8CompressingCodec(readTrailingDot, writeTrailingDot)
                    : new Utf8NonCompressingCodec(readTrailingDot, writeTrailingDot);
        }

        @Override
        public NameCodec getForWrite() {
            return getForRead();
        }
    }

    public boolean supportsUnicode() {
        return false;
    }

    /**
     * Return a wrapper for this codec which supports punycode encoding of
     * unicode characters (non-ascii).
     *
     * @return A codec that converts/unconverts to/from punycode then calls this
     * one.
     */
    public NameCodec toPunycodeNameCodec() {
        return this instanceof PunycodeNameCodec ? this : new PunycodeNameCodec(this);
    }

    /**
     * Write a name into the passed buffer.
     *
     * @param name The name
     * @param into The buffer
     * @throws UnmappableCharacterException If a character is invalid and this
     * codec does not perform conversion
     * @throws io.netty.handler.codec.dns.InvalidDomainNameException If the
     * result of encoding violates the rules of domain name encoding.
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

    public static NameCodecFactory mdnsNameCodecFactory() {
        return new CachingNameCodecFactory(new TrivialUtf8NameCodecFactory(true, false, true));
    }

    public static NameCodec mdnsNameCodec() {
        return new Utf8CompressingCodec(false, true);
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
     * Get a factory for name codec with compression.
     *
     * @return A factory
     */
    public static NameCodecFactory compressingFactory() {
        return new CompressingNameCodecFactory(false, true);
    }

    /**
     * Get a factory for name codecs with compression.
     *
     * @return A factory
     */
    public static NameCodecFactory standardFactory() {
        return new UncompressedNameCodecFactory(false, true);
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
    protected CharSequence defaultReadName(ByteBuf buf, boolean trailingDot) throws DnsDecoderException {
        if (buf.readableBytes() == 0) {
            return ROOT;
        }
        if (buf.readableBytes() == 1) {
            byte r = buf.readByte();
            if (r == 0) {
                return ROOT;
            }
            throw new DnsDecoderException(DnsResponseCode.FORMERR, "The only valid value of a 1-byte name "
                    + "buffer is 0 but found " + r + " (" + (char) r + ") in "
                    + ByteBufUtil.hexDump(buf));
        }
        int position = -1;
        int checked = 0;
        int start = buf.readerIndex();
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
                        throw new DnsDecoderException(DnsResponseCode.FORMERR, "Truncated pointer in a name at "
                                + buf.readerIndex() + " after '" + new String(name.array(), 0,
                                name.readableBytes()) + "'");
                    }
                    final int next = (len & 0x3f) << 8 | buf.readUnsignedByte();
                    if (next >= length) {
                        throw new DnsDecoderException(DnsResponseCode.FORMERR, "Name has an "
                                + "out-of-range pointer at "
                                + buf.readerIndex() + " after '" + new String(name.array(), 0,
                                name.readableBytes(), CharsetUtil.UTF_8) + "' "
                                + ByteBufUtil.hexDump(buf.slice(start, buf.readerIndex())));
                    }
                    buf.readerIndex(next);
                    // check for loops
                    checked += 2;
                    if (checked >= length) {
                        throw new DnsDecoderException(DnsResponseCode.FORMERR, "Name contains a loop at "
                                + buf.readerIndex() + " after '" + new String(name.array(), 0,
                                name.readableBytes(), CharsetUtil.UTF_8)
                                + "' Buffer: " + ByteBufUtil.hexDump(buf.slice(start, buf.readerIndex()))
                                + " checked " + checked + " length " + length
                        );
                    }
                } else if (len != 0) {
                    if (!buf.isReadable(len)) {
                        throw new DnsDecoderException(DnsResponseCode.FORMERR, "Truncated label in a name after "
                                + new String(name.array(), 0, name.writerIndex(), CharsetUtil.UTF_8) + " in "
                                + ByteBufUtil.hexDump(buf.slice(start, buf.readerIndex()))
                                + " Requested " + len + " more bytes, but " + buf.readableBytes()
                                + " bytes are available - at index " + buf.readerIndex() + "."
                        );
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
                    + new String(name.array(), 0, name.readableBytes(), CharsetUtil.UTF_8), ex);
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
        return toDomainName(name.array(), 0, name.readableBytes());
    }

    protected CharSequence toDomainName(byte[] nameAsBytes, int start, int length) {
        if (supportsUnicode()) {
            return new String(nameAsBytes, start, length);
        } else {
            return new AsciiString(nameAsBytes, start, length, false);
        }
    }

    public boolean allowsWhitespace() {
        return false;
    }

    /**
     * Check that the passed name can actually be encoded into ASCII, is legal
     * length for a DNS name, etc.
     *
     * @param name The name
     * @throws UnencodableCharactersException If the name contains invalid
     * characters
     * @throws InvalidDomainNameException If the name structurally cannot be a
     * spec-compliant domain name
     */
    protected void checkName(CharSequence name) throws UnmappableCharacterException,
            InvalidDomainNameException {
        validateName(name, supportsUnicode(), allowsWhitespace());
    }

    /**
     * Check that the passed name can actually be encoded into ASCII, is legal
     * length for a DNS name, etc. Note that this does not test for IDN illegal
     * characters (you might be using mDNS UTF-8 which has fewer restrictions),
     * but attempting to encode illegal characters with punycode will fail when
     * you call writeName().
     *
     * @param name The name
     * @param unicode If true, don't fail on non-ascii characters
     * @throws UnencodableCharactersException If the name contains invalid
     * characters
     * @throws InvalidDomainNameException If the name structurally cannot be a
     * spec-compliant domain name
     */
    public static void validateName(CharSequence name, boolean unicode,
            boolean whitspaceOk) throws UnmappableCharacterException,
            InvalidDomainNameException {

        int length = name.length();
        if (length > 253) {
            throw new InvalidDomainNameException(name, "Name length must be <= 253");
        }
        if (!unicode && !ASCII_ENCODER.canEncode(name)) {
            throw new UnencodableCharactersException(name);
        }
        int lastLabelStart = 0;
        for (int i = 0; i < length; i++) {
            char c = name.charAt(i);
            if (!whitspaceOk && Character.isWhitespace(c)) {
                throw new InvalidDomainNameException(name, "Name contains "
                        + "illegal whitespace '" + (int) c
                        + "' at " + i + ": '" + name + "'");
            }
            if (!legalCharacter(c, i - lastLabelStart, length, unicode)) {
                throw new InvalidDomainNameException(name, "Name contains illegal character '" + c
                        + "' at " + i + ": '" + name + "'");
            }
            if (c == '.' || c == '@') {
                lastLabelStart = i + 1;
            }
            if (i - lastLabelStart > 63) {
                throw new InvalidDomainNameException(name, "Label too long (> 63 chars)");
            }
        }
    }

    private static boolean legalCharacter(char c, int index, int length, boolean unicode) {
        boolean result = unicode || ((c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '*'
                || c == '-'
                || c == '.');
        // Hyphen is legal, but not as leading or trailing char
        if (result && c == '-' && (index == 0 || index == length - 1)) {
            result = false;
        }
        return result;
    }
}
