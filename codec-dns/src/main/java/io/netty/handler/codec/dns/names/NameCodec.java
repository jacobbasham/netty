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
import static io.netty.handler.codec.dns.names.NameCodecFeature.CASE_CONVERSION;
import static io.netty.handler.codec.dns.names.NameCodecFeature.COMPRESSION;
import static io.netty.handler.codec.dns.names.NameCodecFeature.MDNS_UTF_8;
import static io.netty.handler.codec.dns.names.NameCodecFeature.PUNYCODE;
import static io.netty.handler.codec.dns.names.NameCodecFeature.READ_TRAILING_DOT;
import static io.netty.handler.codec.dns.names.NameCodecFeature.WRITE_TRAILING_DOT;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import static io.netty.util.CharsetUtil.UTF_8;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import io.netty.util.internal.UnstableApi;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.UnmappableCharacterException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * Reads and writes DNS names optionally using pointer compression and/or punycode.
 * <p>
 * Writers with and without pointer compression are supported; generally the compressing version should be used unless
 * you are debugging data on the wire.
 * <p>
 * The basic (no compression) NameWriter is stateless; the compressing NameWriter needs its state cleared after use;
 * NameCodec implements AutoCloseable so it can be used in a try-with-resources structure.
 * <p>
 * NameCodec reads and writes <code>CharSequence</code>. In the case of non-punycode codecs, the return values will
 * likely be <code>AsciiString</code>, using 8-bit internal data structures instead of Java's two-byte chars.
 * Punycode-supporting encoders use String.
 * <p>
 * The choice of what to use is based on your application - for example, if you are storing a lot of DNS records in
 * memory, you are better off storing them in un-decoded punycode at 8 bits per character than decoding punycode to
 * 16-bit characters (the results will look the same on the wire either way). On the other hand, if you are displaying
 * them in a UI, full unicode is likely what you want.
 * <p>
 * The {@code WRITE_TRAILING_DOT} feature is worth being careful with - it is handy to <i>read</i> names without the
 * trailing dot, but if you disable writing the trailing dot for data on the wire, you will generate invalid packets.
 * <p>
 * Note:  <i>All</i> NameCodec instances support <i>reading</i> DNS pointer compression, regardless of the
 * compression setting they use for writing.
 */
@UnstableApi
public abstract class NameCodec implements AutoCloseable {

    static final AsciiString ROOT = new AsciiString(".");
    static final NameCodec DEFAULT = new NonCompressingNameCodec(false, true);
    static final CharsetEncoder ASCII_ENCODER = CharsetUtil.US_ASCII.newEncoder();
    static NameCodecFactory DEFAULT_FACTORY;

    /**
     * Get a NameCodec that has the supplied list of features. Passing nothing gets you a
     * non-compression-supporting, ASCII-only NameCodec.
     *
     * @param features The features you need.
     * @return A codec
     */
    public static NameCodec get(NameCodecFeature... features) {
        Set<NameCodecFeature> feat = NameCodecFeature.validate(features);
        return safeGet(feat);
    }

    /**
     * Get a NameCodec that has the supplied list of features. Passing nothing gets you a
     * non-compression-supporting, ASCII-only NameCodec that <i>DOES NOT</i> write
     * trailing dots (required for valid wire packets).
     *
     * @param features The features you need.
     * @return A codec
     */
    public static NameCodec get(Set<NameCodecFeature> features) {
        NameCodecFeature.validate(features);
        return safeGet(features);
    }

    private static NameCodec safeGet(Set<NameCodecFeature> features) {
        // Assume the feature set is already validated
        NameCodec result;
        if (features.contains(MDNS_UTF_8)) {
            if (features.contains(COMPRESSION)) {
                result = new Utf8CompressingCodec(features.contains(READ_TRAILING_DOT),
                        features.contains(WRITE_TRAILING_DOT));
            } else {
                result = new Utf8NonCompressingCodec(features.contains(READ_TRAILING_DOT),
                        features.contains(WRITE_TRAILING_DOT));
            }
            if (features.contains(CASE_CONVERSION)) {
                result = result.toCaseConvertingNameCodec();
            }
        } else {
            if (features.contains(COMPRESSION)) {
                result = new CompressingNameCodec(features.contains(READ_TRAILING_DOT),
                        features.contains(WRITE_TRAILING_DOT));
            } else {
                result = new NonCompressingNameCodec(features.contains(READ_TRAILING_DOT),
                        features.contains(WRITE_TRAILING_DOT));
            }
            if (features.contains(PUNYCODE)) {
                result = result.toPunycodeNameCodec();
            }
            if (features.contains(CASE_CONVERSION)) {
                result = result.toCaseConvertingNameCodec();
            }
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
        checkNotNull(features, "features");
        Set<NameCodecFeature> featureSet = EnumSet.noneOf(NameCodecFeature.class);
        featureSet.addAll(Arrays.asList(features));
        return factory(featureSet);
    }

    /**
     * Get a factory for NameCodecs that have the feature list passed here.
     *
     * @param features The features
     * @return A factory for name codecs
     */
    public static NameCodecFactory factory(Set<NameCodecFeature> features) {
        checkNotNull(features, "features");
        if (features.size() == 2 && features.contains(WRITE_TRAILING_DOT) && features.contains(COMPRESSION)) {
            return compressingFactory();
        }
        NameCodecFeature.validate(features);
        return new CachingNameCodecFactory(new DefaultNameCodecFactory(features));
    }

    private static final class DefaultNameCodecFactory implements NameCodecFactory {
        private final NameCodecFeature[] features;
        private final NameCodec readInstance;
        private final NameCodec writeInstance;
        public DefaultNameCodecFactory(Set<NameCodecFeature> features) {
            readInstance = NameCodec.get(features);
            boolean isStatefulCodec = readInstance.writesWithPointerCompression();
            if (!isStatefulCodec) {
                writeInstance = readInstance;
                this.features = null;
            } else {
                writeInstance = null;
                this.features = features.toArray(new NameCodecFeature[features.size()]);
            }
        }

        @Override
        public NameCodec getForRead() {
            return readInstance;
        }

        @Override
        public NameCodec getForWrite() {
            return writeInstance == null ? NameCodec.get(features) : writeInstance;
        }
    }

    public boolean supportsUnicode() {
        return false;
    }

    /**
     * Return a wrapper for this codec which supports punycode encoding of unicode characters (non-ascii).
     *
     * @return A codec that converts/unconverts to/from punycode then calls this one.
     */
    public final NameCodec toPunycodeNameCodec() {
        Set<NameCodecFeature> features = NameCodecFeature.featuresOf(this);
        if (features.contains(PUNYCODE)) {
            return this;
        }
        if (supportsUnicode() || features.contains(MDNS_UTF_8)) {
            throw new UnsupportedOperationException("This codec is an MDNS_UTF_8 codec. "
                    + "Cannot support that and PUNYCODE at the same time - they are both "
                    + "incompatible ways of encoding non-ascii characters.");
        }
        return new PunycodeNameCodec(this);
    }

    /**
     * Write a name into the passed buffer.
     *
     * @param name The name
     * @param into The buffer
     * @throws UnmappableCharacterException If a character is invalid and this codec does not perform conversion
     * @throws io.netty.handler.codec.dns.InvalidDomainNameException If the result of encoding violates the rules of
     * domain name encoding according to the features of this codec.
     */
    public abstract void writeName(CharSequence name, ByteBuf into) throws UnmappableCharacterException,
            InvalidDomainNameException;

    /**
     * Get a NameCodec that does not use DNS name compression.
     *
     * @return A non-compressing NameCodec
     */
    public static NameCodec nonCompressingNameCodec() {
        return DEFAULT;
    }

    /**
     * Get a NameCodec that reads and writes mDNS's UTF8 encoding for names (not compatible
     * with traditional unicast DNS servers or clients, but used by Avahi, Rondezvous and similar).
     *
     * @return A codec
     */
    public static NameCodec mdnsNameCodec() {
        return new Utf8CompressingCodec(false, true).toCaseConvertingNameCodec();
    }

    /**
     * Get a NameCodec which will compress names according to the RFC. Note that the returned NameCodec is stateful;
     * its <code>close()</code> method should be called if it is to be reused against a different buffer.
     *
     * @return A compressing NameCodec
     */
    public static NameCodec compressingNameCodec() {
        return new CompressingNameCodec(false, true);
    }

    /**
     * Clear any state associated with this NameCodec, if it is to be reused.  It is important to
     * call this on instances used for writing, otherwise subsequent writes into new buffers may contain
     * pointers to data that is not present.
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
        return DEFAULT_FACTORY == null
                ? DEFAULT_FACTORY = new CachingNameCodecFactory(
                        new DefaultNameCodecFactory(EnumSet.of(COMPRESSION, WRITE_TRAILING_DOT)))
                : DEFAULT_FACTORY;
    }

    /**
     * Get a factory for name codecs which do not use name pointer compression (useful for
     * debugging, but results in bigger packets).
     *
     * @return A factory
     */
    public static NameCodecFactory nonCompressingFactory() {
        return new CachingNameCodecFactory(new DefaultNameCodecFactory(EnumSet.of(WRITE_TRAILING_DOT)));
    }

    /**
     * Read a name from a buffer. Assumes the buffer is positioned at the initial byte of a name (which will be a label
     * length). Retrieves a domain name given a buffer containing a DNS packet. If the name contains a pointer, the
     * position of the buffer will be set to directly after the pointer's index after the name has been read.
     * <p>
     * The default implementation can decode DNS pointers, but does not interpret punycode encoding of non-ascii
     * characters.
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
     * Convenience method to read a name from a buffer. Assumes the buffer is positioned at the initial byte of a name
     * (which will be a label length). Retrieves a domain name given a buffer containing a DNS packet. If the name
     * contains a pointer, the position of the buffer will be set to directly after the pointer's index after the name
     * has been read.
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
            return new String(nameAsBytes, start, length, UTF_8);
        } else {
            return new AsciiString(nameAsBytes, start, length, false);
        }
    }

    public NameCodec toCaseConvertingNameCodec() {
        return this.convertsCase() ? this : new CaseConvertingNameCodecWrapper(this);
    }

    public boolean allowsWhitespace() {
        return false;
    }

    public boolean convertsCase() {
        return false;
    }

    public abstract boolean readsTrailingDot();

    public abstract boolean writesTrailingDot();

    public abstract boolean writesWithPointerCompression();

    /**
     * Check that the passed name can actually be encoded into ASCII, is legal length for a DNS name, etc.
     *
     * @param name The name
     * @throws UnencodableCharactersException If the name contains invalid characters
     * @throws InvalidDomainNameException If the name structurally cannot be a spec-compliant domain name
     */
    protected void checkName(CharSequence name) throws UnmappableCharacterException,
            InvalidDomainNameException {
        validateName(name, supportsUnicode(), allowsWhitespace());
    }

    /**
     * Check that the passed name can actually be encoded into ASCII, is legal length for a DNS name, etc. Note that
     * this does not test for IDN illegal characters (you might be using mDNS UTF-8 which has fewer restrictions), but
     * attempting to encode illegal characters with punycode will fail when you call writeName().
     *
     * @param name The name
     * @param unicodeOk If true, don't fail on non-ascii characters
     * @throws UnencodableCharactersException If the name contains invalid characters
     * @throws InvalidDomainNameException If the name structurally cannot be a spec-compliant domain name
     */
    public static void validateName(CharSequence name, boolean unicodeOk,
            boolean whitspaceOk) throws UnmappableCharacterException,
            InvalidDomainNameException {

        int length = name.length();
        if (length > 253) {
            throw new InvalidDomainNameException(name, "Name length must be <= 253");
        }
        if (!unicodeOk && !ASCII_ENCODER.canEncode(name)) {
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
            if (!legalCharacter(c, i - lastLabelStart, length, unicodeOk)) {
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
