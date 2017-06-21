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
     * Use DNS name pointer compression. Unless you are debugging the wire
     * format, you almost always want this as it reduces packet size.
     */
    COMPRESSION,
    /**
     * Translate punycode into unicode. This forces the use of
     * {@link java.lang.String} instead of {@link io.netty.util.AsciiString},
     * doubling the memory footprint of names. If you are writing something like
     * a caching server, where you are not displaying the domain names directly,
     * just forwarding them, leave this unset and - clients will do the encoding
     * and decoding if needed.
     */
    PUNYCODE,
    /**
     * Write the trailing 0 on names in DNS packets. This is almost always
     * wanted, as according to the spec, this makes the final label (such as
     * "com") a child of the <i>root domain</i> of the internet, named "".
     * <i>Always</i> use this feature if you are sending data over the wire
     * using DNS protocol, or you will generate invalid data.
     */
    WRITE_TRAILING_DOT,
    /**
     * Read and append the trailing dot when reading names. Part of the DNS spec
     * (assuming the incoming name is indeed fully qualified), but if you are
     * displaying names in a user interface, you may want to discard trailing
     * dots on names.
     */
    READ_TRAILING_DOT,
    /**
     * Codec for writing names in mDNS's UTF-8 format -
     * https://tools.ietf.org/html/rfc6762 - which encodes labels in UTF-8
     * instead of Ascii or punycode.
     */
    MDNS_UTF_8;

    public static Set<NameCodecFeature> validate(NameCodecFeature... features) {
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
