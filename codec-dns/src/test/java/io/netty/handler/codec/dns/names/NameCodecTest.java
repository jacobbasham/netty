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
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.dns.DnsDecoderException;
import static io.netty.handler.codec.dns.names.NameCodecFeature.*;
import io.netty.util.AsciiString;
import java.net.IDN;
import java.nio.charset.UnmappableCharacterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests NameCodec functionality.
 */
public class NameCodecTest {

    static final NameCodecFeature[][] FEATURE_COMBINATIONS;
    static final NameCodecFeature[][] UNICODE_FEATURE_COMBINATIONS;

    static {
        List<List<NameCodecFeature>> all = new ArrayList<List<NameCodecFeature>>();
        List<List<NameCodecFeature>> unicode = new ArrayList<List<NameCodecFeature>>();
        featuresCartesianProduct(all, new ArrayList<NameCodecFeature>());
        for (List<NameCodecFeature> l : all) {
            if (l.contains(MDNS_UTF_8) || l.contains(PUNYCODE)) {
                unicode.add(l);
            }
        }
        FEATURE_COMBINATIONS = new NameCodecFeature[all.size()][];
        UNICODE_FEATURE_COMBINATIONS = new NameCodecFeature[unicode.size()][];
        for (int i = 0; i < all.size(); i++) {
            List<NameCodecFeature> curr = all.get(i);
            FEATURE_COMBINATIONS[i] = curr.toArray(new NameCodecFeature[curr.size()]);
        }
        for (int i = 0; i < unicode.size(); i++) {
            List<NameCodecFeature> curr = unicode.get(i);
            UNICODE_FEATURE_COMBINATIONS[i] = curr.toArray(new NameCodecFeature[curr.size()]);
        }
        // Sort these so that if a feature is failing, it fails while running
        // against the smallest number of other features, so the source of failure
        // is more easily diagnosed
        Comparator<NameCodecFeature[]> comparator = new FeatureListComparator();
        Arrays.sort(FEATURE_COMBINATIONS, comparator);
        Arrays.sort(UNICODE_FEATURE_COMBINATIONS, comparator);
    }

    static final NameCodecFeature[][] combinationsContaining(NameCodecFeature f) {
        List<NameCodecFeature[]> result = new ArrayList<NameCodecFeature[]>();
        for (int i = 0; i < FEATURE_COMBINATIONS.length; i++) {
            for (int j = 0; j < FEATURE_COMBINATIONS[i].length; j++) {
                NameCodecFeature ff = FEATURE_COMBINATIONS[i][j];
                if (ff == f) {
                    result.add(FEATURE_COMBINATIONS[i]);
                }
            }
        }
        Collections.sort(result, new FeatureListComparator());
        return result.toArray(new NameCodecFeature[result.size()][]);
    }

    static final class FeatureListComparator implements Comparator<NameCodecFeature[]> {

        @Override
        public int compare(NameCodecFeature[] o1, NameCodecFeature[] o2) {
            Integer a = o1.length;
            Integer b = o2.length;
            int result = a.compareTo(b);
            if (result == 0) {
                String an = o1[o1.length - 1].name();
                String bn = o2[o2.length - 1].name();
                result = an.compareTo(bn);
            } else if (o1.length == 1 && o2.length == 1) {
                String an = o1[0].name();
                String bn = o2[0].name();
                result = an.compareTo(bn);
            }
            return result;
        }
    }

    static void featuresCartesianProduct(List<List<NameCodecFeature>> all, List<NameCodecFeature> curr) {
        Collections.sort(curr); // For visual comparability
        if (!all.contains(curr)) {
            all.add(new ArrayList<NameCodecFeature>(curr));
        }
        skip:
        for (NameCodecFeature a : NameCodecFeature.values()) {
            if (curr.contains(a)) {
                continue;
            }
            for (NameCodecFeature f : curr) {
                if (!f.canCoexistWith(a)) {
                    continue skip;
                }
            }
            List<NameCodecFeature> next = new ArrayList<NameCodecFeature>(curr);
            next.add(a);
            Collections.sort(next);
            if (!all.contains(next)) {
                all.add(next);
            }
            featuresCartesianProduct(all, next);
        }
    }

    static class DummyFactory implements NameCodecFactory {

        private final NameCodec codec;

        public DummyFactory(NameCodec codec) {
            this.codec = codec;
        }

        @Override
        public NameCodec getForRead() {
            return codec;
        }

        @Override
        public NameCodec getForWrite() {
            return codec;
        }
    }

    static void testOne(String name, NameCodecFeature... features) throws Exception {
        Set<NameCodecFeature> featureSet = EnumSet.noneOf(NameCodecFeature.class);
        featureSet.addAll(Arrays.asList(features));
//        System.out.println("TEST '" + name + "' " + featureSet);

        String lookFor = name;
        ByteBuf buf = Unpooled.buffer();
        NameCodecFactory[] factories = new NameCodecFactory[]{new DummyFactory(NameCodec.get(features)),
            NameCodec.factory(features)};
        if (featureSet.contains(READ_TRAILING_DOT) && !name.equals(".") && !name.endsWith(".")) {
            lookFor += '.';
        }
        if (featureSet.contains(CASE_CONVERSION)) {
            lookFor = lookFor.toLowerCase();
        }

        for (int c = 0; c < factories.length; c++) {
            NameCodec readCodec = factories[c].getForRead();
            NameCodec writeCodec = factories[c].getForWrite();
            String prefix = c == 0 ? "(using NameCodec.get()) " : "(using NameCodec.factory()) ";
            try {
                if (featureSet.contains(CASE_CONVERSION)) {
                    assertTrue(prefix + "Got non-case-converting instance for " + featureSet + " - "
                            + readCodec, readCodec.convertsCase());
                    assertTrue(prefix + "Got non-case-converting instance for " + featureSet + " - "
                            + writeCodec, writeCodec.convertsCase());
                } else {
                    assertFalse(prefix + "Got case-converting instance for " + featureSet + " - "
                            + readCodec, readCodec.convertsCase());
                    assertFalse(prefix + "Got case-converting instance for " + featureSet + " - "
                            + writeCodec, writeCodec.convertsCase());
                }
                if (featureSet.contains(MDNS_UTF_8) || featureSet.contains(PUNYCODE)) {
                    assertTrue(prefix + "Got non-unicode instance for " + featureSet + " - " + readCodec, readCodec
                            .supportsUnicode());
                    assertTrue(prefix + "Got non-unicode instance for " + featureSet + " - " + writeCodec, writeCodec
                            .supportsUnicode());
                } else {
                    assertFalse(prefix + "Got unicode instance for " + featureSet + " - " + readCodec, readCodec
                            .supportsUnicode());
                    assertFalse(prefix + "Got unicode instance for " + featureSet + " - " + writeCodec, writeCodec
                            .supportsUnicode());
                }
                if (featureSet.contains(MDNS_UTF_8)) {
                    assertTrue(prefix + "Got non-whitespace-allowing instance for " + featureSet + " - " + readCodec,
                            readCodec.allowsWhitespace());
                    assertTrue(prefix + "Got non-whitespace-allowing instance for " + featureSet + " - " + writeCodec,
                            writeCodec.allowsWhitespace());
                } else {
                    assertFalse(prefix + "Got a whitespace-allowing instance for " + featureSet + " - " + readCodec,
                            readCodec.allowsWhitespace());
                    assertFalse(prefix + "Got a whitespace-allowing instance for " + featureSet + " - " + writeCodec,
                            writeCodec.allowsWhitespace());
                }
                if (featureSet.contains(READ_TRAILING_DOT)) {
                    assertTrue(prefix + "Got non-trailing-dot-reading instance for " + featureSet + " - "
                            + readCodec, readCodec.readsTrailingDot());
                    assertTrue(prefix + "Got non-trailing-dot-reading instance for " + featureSet + " - "
                            + writeCodec, writeCodec.readsTrailingDot());
                } else {
                    assertFalse(prefix + "Got trailing-dot-reading instance for " + featureSet + " - "
                            + readCodec, readCodec.readsTrailingDot());
                    assertFalse(prefix + "Got trailing-dot-reading instance for " + featureSet + " - "
                            + writeCodec, writeCodec.readsTrailingDot());
                }
                if (featureSet.contains(WRITE_TRAILING_DOT)) {
                    assertTrue(prefix + "Got non-trailing-dot-reading instance for " + featureSet + " - "
                            + readCodec, readCodec.writesTrailingDot());
                    assertTrue(prefix + "Got non-trailing-dot-reading instance for " + featureSet + " - "
                            + writeCodec, writeCodec.writesTrailingDot());
                } else {
                    assertFalse(prefix + "Got trailing-dot-reading instance for " + featureSet + " - "
                            + readCodec, readCodec.writesTrailingDot());
                    assertFalse(prefix + "Got trailing-dot-reading instance for " + featureSet + " - "
                            + writeCodec, writeCodec.writesTrailingDot());
                }

                Set<NameCodecFeature> actualFeatures = NameCodecFeature.featuresOf(readCodec);
                Set<NameCodecFeature> surprises = EnumSet.copyOf(actualFeatures);
                surprises.removeAll(featureSet);
                assertTrue(prefix + "Unrequested feature implemented by returned READ codec - requested " + featureSet
                        + " but got " + actualFeatures + " which includes unrequested " + surprises, surprises
                                .isEmpty());

                actualFeatures = NameCodecFeature.featuresOf(writeCodec);
                surprises = EnumSet.copyOf(actualFeatures);
                surprises.removeAll(featureSet);
                assertTrue(prefix + "Unrequested feature implemented by returned WRITE codec - requested " + featureSet
                        + " but got " + actualFeatures + " which includes unrequested " + surprises, surprises
                                .isEmpty());
                try {
                    writeCodec.writeName(name, buf);
                } catch (UnencodableCharactersException ex) {
                    throw new UnencodableCharactersException(prefix + "Could not encode '" + name + "' with "
                            + featureSet + " - " + readCodec, ex);
                }
                String received = readCodec.readName(buf).toString();
                if (c == 0 && featureSet.contains(PUNYCODE) && received.contains("--")) {
                    lookFor = IDN.toASCII(name);
                }
                assertEquals(prefix + "For '" + name + "' with " + featureSet + " - " + readCodec
                        + ", - got wrong encoding result ", lookFor, received);
                if (!readCodec.supportsUnicode()) {
                    writeCodec.close();
                    writeCodec = factories[c].getForWrite();
                    AsciiString sb = new AsciiString(name);
                    buf = Unpooled.buffer();
                    writeCodec.writeName(sb, buf);
                    received = readCodec.readName(buf).toString();
                    assertEquals(prefix + "For '" + name + "' with " + featureSet + ", - String representation",
                            lookFor, received);
                }
//                  System.out.println(prefix + "PASSED '" + name + "' with '" + received + "' for "
//                 + featureSet + " with " + writeCodec);
            } finally {
                readCodec.close();
                writeCodec.close();
            }
        }
    }

    static void testAll(String str) throws Exception {
        for (int i = 0; i < FEATURE_COMBINATIONS.length; i++) {
            NameCodecFeature[] features = FEATURE_COMBINATIONS[i];
            testOne(str, features);
        }
    }

    static void testUnicodeOnly(String str) throws Exception {
        for (int i = 0; i < UNICODE_FEATURE_COMBINATIONS.length; i++) {
            NameCodecFeature[] features = UNICODE_FEATURE_COMBINATIONS[i];
            testOne(str, features);
        }
    }

    @Test
    public void testTrailingDotSynthesizedIfNotWritten() throws UnmappableCharacterException, DnsDecoderException {
        NameCodec nc = NameCodec.get(READ_TRAILING_DOT, CASE_CONVERSION);
        ByteBuf buf = Unpooled.buffer();
        nc.writeName("foo.com", buf);
        CharSequence result = nc.readName(buf);
        if (!result.toString().endsWith(".")) {
            fail("Missing trailing dot - should be synthesized if not present");
        }
    }

    @Test
    public void testUnicode() throws Exception {
        testUnicodeOnly("продается.com");
    }

    @Test(expected = UnmappableCharacterException.class)
    public void testInvalidChars() throws Exception {
//        testOne("продается");
        NameCodec codec = NameCodec.compressingNameCodec();
        try {
            codec.writeName("продается", Unpooled.buffer());
        } finally {
            codec.close();
        }
    }

    @Test
    public void testPunycodeCannotBecomeUtf8() {
        for (NameCodecFeature[] features : NameCodecTest.combinationsContaining(MDNS_UTF_8)) {
            try {
                NameCodec codec = NameCodec.get(features);
                NameCodec punycode = codec.toPunycodeNameCodec();
                fail("Exception should have been thrown, but got " + punycode
                        + " for features " + Arrays.asList(features));
            } catch (UnsupportedOperationException ex) {
                // expected
            }
        }
    }

    @Test
    public void testCompressingNameWriter() throws Exception {
        assertTrue(true);
        NameCodec wri = NameCodec.get(COMPRESSION, WRITE_TRAILING_DOT);
        ByteBuf buf = Unpooled.buffer(1000, 1000);
        wri.writeName("foo.bar.com", buf);
        wri.writeName("moo.bar.com", buf);
        wri.writeName("baz.bar.com", buf);

        String name1 = wri.readName(buf).toString();
        assertEquals("foo.bar.com", name1);

        String name2 = wri.readName(buf).toString();
        assertEquals("moo.bar.com", name2);

        String name3 = wri.readName(buf).toString();
        assertEquals("baz.bar.com", name3);
    }

    @Test
    public void testVaryingLengths() throws Exception {
        testOne("foo");
        testOne("matrix.timboudreau.com");
        testOne("foo.bar.baz.whatever");
        testOne("g");
        testOne("129.3.20.20202");
    }

    @Test
    public void testTrailingDot2() throws Exception {
        NameCodec codec = NameCodec.get(COMPRESSION, READ_TRAILING_DOT, WRITE_TRAILING_DOT);
        ByteBuf buf = Unpooled.buffer();
        codec.writeName("netty.io", buf);
        CharSequence read = codec.readName(buf);
        assertEquals("netty.io.", read.toString());
    }

    @Test
    public void testTrailingDot() throws Exception {
        testOne("netty.io", COMPRESSION, READ_TRAILING_DOT, WRITE_TRAILING_DOT);
        testOne(".", COMPRESSION, READ_TRAILING_DOT, WRITE_TRAILING_DOT);
    }

    @Test
    public void testWildcardCharacter() throws Exception {
        testAll("*.foo.EXAMPLE");
    }

    @Test
    public void testSpecialNames() throws UnmappableCharacterException, InvalidDomainNameException {
        assertOnlyZeroWritten("", NameCodec.nonCompressingNameCodec());
        assertOnlyZeroWritten("", NameCodec.nonCompressingNameCodec().toPunycodeNameCodec());
        assertOnlyZeroWritten("", NameCodec.compressingNameCodec());
        assertOnlyZeroWritten("", NameCodec.compressingNameCodec().toPunycodeNameCodec());
        assertOnlyZeroWritten("", NameCodec.mdnsNameCodec());
        assertOnlyZeroWritten("", NameCodec.factory(
                READ_TRAILING_DOT, WRITE_TRAILING_DOT, COMPRESSION).getForWrite());
        assertOnlyZeroWritten("", NameCodec.factory(
                READ_TRAILING_DOT, WRITE_TRAILING_DOT, COMPRESSION, CASE_CONVERSION).getForWrite());

        assertOnlyZeroWritten(".", NameCodec.nonCompressingNameCodec());
        assertOnlyZeroWritten(".", NameCodec.nonCompressingNameCodec().toPunycodeNameCodec());
        assertOnlyZeroWritten(".", NameCodec.compressingNameCodec());
        assertOnlyZeroWritten(".", NameCodec.compressingNameCodec().toPunycodeNameCodec());
        assertOnlyZeroWritten(".", NameCodec.mdnsNameCodec());
        assertOnlyZeroWritten(".", NameCodec.factory(
                READ_TRAILING_DOT, WRITE_TRAILING_DOT, COMPRESSION).getForWrite());
        assertOnlyZeroWritten(".", NameCodec.factory(
                READ_TRAILING_DOT, WRITE_TRAILING_DOT, COMPRESSION, CASE_CONVERSION).getForWrite());
    }

    @Test(expected = InvalidDomainNameException.class)
    public void testWhitespace() throws Exception {
        assertOnlyZeroWritten(" ", NameCodec.factory(READ_TRAILING_DOT,
                WRITE_TRAILING_DOT, COMPRESSION).getForWrite());
    }

    private void assertOnlyZeroWritten(String s, NameCodec codec)
            throws UnmappableCharacterException, InvalidDomainNameException {
        ByteBuf buf = Unpooled.buffer(5);
        codec.writeName(s, buf);
        assertEquals(1, buf.readableBytes());
        assertEquals(0, buf.readByte());
        codec.close();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUtf8AndPunycodeCannotBeCombined() throws Exception {
        NameCodecFactory f = NameCodec.factory(COMPRESSION,
                PUNYCODE, MDNS_UTF_8);
        fail("Exception should have been thrown but got " + f);
    }

    @Test
    public void testMdnsCodecIsCaseInsensitive() throws UnmappableCharacterException,
            InvalidDomainNameException, DnsDecoderException {
        ByteBuf buf1 = Unpooled.buffer();
        ByteBuf buf2 = Unpooled.buffer();
        NameCodec c1 = NameCodec.mdnsNameCodec();
        NameCodec c2 = NameCodec.mdnsNameCodec();
        assertNotSame(c1, c2);
        c1.writeName("sun.COM", buf1);
        c2.writeName("sun.com", buf2);
        CharSequence r1 = c1.readName(buf1);
        CharSequence r2 = c1.readName(buf2);
        assertEquals(r1, r2);
    }

    @Test(expected = InvalidDomainNameException.class)
    public void testInvalidLength() throws Exception {
        StringBuilder sb = new StringBuilder("foo.");
        for (int i = 0; i < 64; i++) {
            sb.append('a');
        }
        sb.append(".biz");
        testOne(sb.toString());
    }

    @Test(expected = DnsDecoderException.class)
    public void testTooLongName() throws Exception {
        StringBuilder sb = new StringBuilder("com");
        while (sb.length() < 254) {
            sb.insert(0, "foo.");
        }
        ByteBuf buf = Unpooled.buffer();
        NonCompressingNameCodec nw = new NonCompressingNameCodec(false, true);
        nw.writeName0(sb, buf);
        nw.readName(buf);
    }

    @Test(expected = InvalidDomainNameException.class)
    public void testInvalidLabel() throws Exception {
        ByteBuf buf = Unpooled.buffer();
        NonCompressingNameCodec nw = new NonCompressingNameCodec(false, true);
        nw.writeName("foo.-bar.com", buf);
    }

    @Test
    public void testTrailingDotsNotDoubled() throws Throwable {
        NameCodecFeature[][] featureSets = combinationsContaining(READ_TRAILING_DOT);
        Throwable thrown = null;
        for (int i = 0; i < featureSets.length; i++) {
            NameCodecFeature[] features = featureSets[i];
            try {
                testTrailingDot("netty.io.", features);
            } catch (DnsDecoderException ex) {
                if (thrown == null) {
                    thrown = ex;
                }
                ex.printStackTrace(System.err);
            } catch (AssertionError ex) {
                if (thrown == null) {
                    thrown = ex;
                }
                ex.printStackTrace(System.err);
            }
        }
        if (thrown != null) {
            throw thrown;
        }
    }

    private void testTrailingDot(String s, NameCodecFeature... features)
            throws UnmappableCharacterException, DnsDecoderException {
        assertTrue(s.endsWith("."));
        NameCodec codec = NameCodec.get(features);
        ByteBuf buf = Unpooled.buffer();
        byte[] bytes = null;
        try {
            codec.writeName(s, buf);
            bytes = new byte[buf.readableBytes()];
            buf.getBytes(0, bytes);
            String result = codec.readName(buf).toString();
            assertFalse("Doubled dots on '" + result + "' with "
                    + Arrays.asList(features) + " and " + codec, result.endsWith(".."));
            assertTrue("Missing dot on '" + result
                    + "' with " + Arrays.asList(features) + " and " + codec, result.endsWith("."));
            assertEquals("Non matching result '" + result + "' with "
                    + Arrays.asList(features) + " and " + codec + " - buffer: "
                    + toHexOrCharString(bytes), s, result);
        } catch (DnsDecoderException ex) {
            throw new DnsDecoderException(ex.code(), "Exception decoding output for '"
                    + s + "' with features " + Arrays.asList(features)
                    + " with " + codec + " - output \n"
                    + toHexOrCharString(bytes), ex);
        } finally {
            codec.close();
        }
    }

    static String toHexOrCharString(ByteBuf buf) {
        byte[] b = new byte[buf.capacity()];
        buf.getBytes(0, b);
        return toHexOrCharString(b);
    }

    static String toHexOrCharString(byte[] b) {
        if (b == null) {
            return "<no data>";
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            byte curr = b[i];
            CharSequence hx = toCharOrString(curr);
            result.append(" ").append(hx);
        }
        return result.toString();
    }

    private static String toCharOrString(byte b) {
        int val = b & 0xFF;
        if ((val >= '0' && val <= '9')
                || (val >= 'A' && val <= 'Z')
                || (val >= 'a' && val <= 'z')
                || val == '=' || val == '\'' || val == '.') {
            return "'" + new String(new char[]{(char) val}) + "'";
        }
        return Integer.toString(val);
    }
}
