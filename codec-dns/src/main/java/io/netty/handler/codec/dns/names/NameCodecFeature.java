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

import static io.netty.util.internal.ObjectUtil.checkNotNull;
import io.netty.util.internal.UnstableApi;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * Features available on instances of NameCodec.
 */
@UnstableApi
public enum NameCodecFeature {
    /**
     * Use DNS name pointer compression. Unless you are debugging the wire format, you almost always want this as it
     * reduces packet size.  The COMPRESSION feature is specific to writes - all NameCodec instances are able to
     * <i>read</i> pointer compression.
     */
    COMPRESSION,
    /**
     * Translate punycode into unicode. This forces the use of {@link java.lang.String} instead of
     * {@link io.netty.util.AsciiString}, doubling the memory footprint of names. If you are writing something like a
     * caching server, where you are not displaying the domain names directly, just forwarding them, leave this unset
     * and - clients will do the encoding and decoding if needed.
     */
    PUNYCODE,
    /**
     * Write the trailing 0 on names in DNS packets. This is almost always wanted, as according to the spec, this makes
     * the final label (such as "com") a child of the <i>root domain</i> of the internet, named "".
     * <i>Always</i> use this feature if you are sending data over the wire using DNS protocol, or you will generate
     * invalid packets.
     */
    WRITE_TRAILING_DOT,
    /**
     * Read and append the trailing dot when reading names. Part of the DNS spec (assuming the incoming name is indeed
     * fully qualified), but if you are displaying names in a user interface, you may want to discard trailing dots on
     * names.
     * <p>
     * Note that codecs with this feature will synthesize a trailing dot for names read which did not have one -
     * all calls to readName() result in a character sequence ending in a period.
     */
    READ_TRAILING_DOT,
    /**
     * Reads and writes names in <a href="https://tools.ietf.org/html/rfc6762#section-16">mDNS's UTF-8 format</a>
     * - which encodes labels in UTF-8 instead of Ascii or punycode.
     */
    MDNS_UTF_8,
    /**
     * Converts incoming and outgoing names to lower case on encode/decode - useful in the case that you are matching
     * against names, and want to centralize the code that manages case rather than ensure it is correct everywhere you
     * are comparing names.  This simply guarantees that all names going over the wire, inbound or outbound,
     * are lower-case - which does not affect equality according to the spec, as far as name servers
     * are concerned, but can create easy-to-have bugs if you are using Java string comparison.
     */
    CASE_CONVERSION;

    public static Set<NameCodecFeature> validate(NameCodecFeature... features) {
        checkNotNull(features, "features");
        EnumSet<NameCodecFeature> all = EnumSet.noneOf(NameCodecFeature.class);
        if (features.length > 0) {
            all.addAll(Arrays.asList(features));
            validate(all);
        }
        return all;
    }

    public boolean canCoexistWith(NameCodecFeature feature) {
        return !((feature == PUNYCODE && this == MDNS_UTF_8) || (feature == MDNS_UTF_8 && this == PUNYCODE));
    }

    public boolean isImplementedBy(NameCodec codec) {
        switch(this) {
            case CASE_CONVERSION :
                return codec.convertsCase();
            case COMPRESSION :
                return codec.writesWithPointerCompression();
            case MDNS_UTF_8 :
                return codec.supportsUnicode() &&
                        codec instanceof Utf8CompressingCodec ||
                        codec instanceof Utf8NonCompressingCodec ||
                        (codec instanceof WrapperCodec &&
                            ((isImplementedBy(((WrapperCodec) codec).delegate()))));
            case PUNYCODE :
                return codec.supportsUnicode() &&
                        codec instanceof PunycodeNameCodec ||
                        (codec instanceof WrapperCodec &&
                            ((isImplementedBy(((WrapperCodec) codec).delegate()))));
            case READ_TRAILING_DOT :
                return codec.readsTrailingDot();
            case WRITE_TRAILING_DOT :
                return codec.writesTrailingDot();
            default :
                throw new AssertionError(this);
        }
    }

    public static Set<NameCodecFeature> featuresOf(NameCodec codec) {
        Set<NameCodecFeature> result = EnumSet.noneOf(NameCodecFeature.class);
        for (NameCodecFeature f : values()) {
            if (f.isImplementedBy(codec)) {
                result.add(f);
            }
        }
        return result;
    }

    public static void validate(Set<NameCodecFeature> all) {
        // Only a consideration for MDNS which doesn't support
        // anything but plain UTF-8
        if (!all.contains(MDNS_UTF_8)) {
            return;
        }
        for (NameCodecFeature f : all) {
            for (NameCodecFeature f1 : all) {
                if (!f.canCoexistWith(f1)) {
                    throw new IllegalArgumentException("Feature " + f
                            + " cannot coexist with " + f1);
                }
            }
        }
    }
}
