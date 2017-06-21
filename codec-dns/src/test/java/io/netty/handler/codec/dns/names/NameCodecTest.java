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
import java.nio.charset.UnmappableCharacterException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests NameCodec functionality.
 */
public class NameCodecTest {

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

    private void testOne(String name, NameCodecFeature... features) throws Exception {
        Set<NameCodecFeature> featureSet = EnumSet.noneOf(NameCodecFeature.class);
        featureSet.addAll(Arrays.asList(features));

        String lookFor = name;
        ByteBuf buf = Unpooled.buffer();
        NameCodec nw = NameCodec.get(features);
        nw.writeName(name, buf);
        String received = nw.readName(buf).toString();
        if (featureSet.contains(READ_TRAILING_DOT) && !name.equals(".")) {
            lookFor += '.';
        }
        assertEquals(name + " - String representation", lookFor, received);
        if (!nw.supportsUnicode()) {
            nw = NameCodec.get(features);
            AsciiString sb = new AsciiString(name);
            buf = Unpooled.buffer();
            nw.writeName(sb, buf);
            received = nw.readName(buf).toString();
            assertEquals(name + " - CharSequence representation '" + received + "'", lookFor, received);
        }
    }

    @Test(expected = UnmappableCharacterException.class)
    public void testInvalidChars() throws Exception {
        testOne("продается");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testPunycodeCannotBecomeUtf8() {
        NameCodec.mdnsNameCodec().toPunycodeNameCodec();
    }

    @Test
    public void testWildcardCharacter() throws Exception {
        testOne("*.foo.example");
        testOne("*.foo.example", READ_TRAILING_DOT, WRITE_TRAILING_DOT);
        testOne("*.foo.example", READ_TRAILING_DOT, WRITE_TRAILING_DOT, COMPRESSION);
        testOne("*.foo.example", COMPRESSION);
        testOne("*.foo.example", COMPRESSION, PUNYCODE);
        testOne("*.foo.example", PUNYCODE);
        testOne("*.foo.example", MDNS_UTF_8);
        testOne("*.foo.example", MDNS_UTF_8, COMPRESSION);
    }

    @Test
    public void testSpecialNames() throws UnmappableCharacterException, InvalidDomainNameException {
        assertOnlyZeroWritten("", NameCodec.basicNameCodec());
        assertOnlyZeroWritten("", NameCodec.basicNameCodec().toPunycodeNameCodec());
        assertOnlyZeroWritten("", NameCodec.compressingNameCodec());
        assertOnlyZeroWritten("", NameCodec.compressingNameCodec().toPunycodeNameCodec());
        assertOnlyZeroWritten("", NameCodec.mdnsNameCodec());
        assertOnlyZeroWritten("", NameCodec.factory(READ_TRAILING_DOT, WRITE_TRAILING_DOT, COMPRESSION).getForWrite());

        assertOnlyZeroWritten(".", NameCodec.basicNameCodec());
        assertOnlyZeroWritten(".", NameCodec.basicNameCodec().toPunycodeNameCodec());
        assertOnlyZeroWritten(".", NameCodec.compressingNameCodec());
        assertOnlyZeroWritten(".", NameCodec.compressingNameCodec().toPunycodeNameCodec());
        assertOnlyZeroWritten(".", NameCodec.mdnsNameCodec());
        assertOnlyZeroWritten(".", NameCodec.factory(READ_TRAILING_DOT, WRITE_TRAILING_DOT, COMPRESSION).getForWrite());
    }

    @Test(expected = InvalidDomainNameException.class)
    public void testWhitespace() throws Exception {
        assertOnlyZeroWritten(" ", NameCodec.factory(READ_TRAILING_DOT, WRITE_TRAILING_DOT, COMPRESSION).getForWrite());
    }

    private void assertOnlyZeroWritten(String s, NameCodec codec)
            throws UnmappableCharacterException, InvalidDomainNameException {
        ByteBuf buf = Unpooled.buffer(5);
        codec.writeName(s, buf);
        assertEquals(1, buf.readableBytes());
        assertEquals(0, buf.readByte());
        codec.close();
    }

    @Test
    public void testCodecsReportUnicodeCorrectly() {
        assertTrue(NameCodec.mdnsNameCodec().supportsUnicode());
        assertTrue(NameCodec.compressingNameCodec().toPunycodeNameCodec().supportsUnicode());
        assertTrue(NameCodec.basicNameCodec().toPunycodeNameCodec().supportsUnicode());
        assertFalse(NameCodec.compressingNameCodec().supportsUnicode());
        assertFalse(NameCodec.basicNameCodec().supportsUnicode());

        assertTrue(NameCodec.factory(PUNYCODE).getForWrite().supportsUnicode());
        assertTrue("" + NameCodec.factory(MDNS_UTF_8) + " -> "
                + NameCodec.factory(MDNS_UTF_8).getForWrite(),
                NameCodec.factory(MDNS_UTF_8).getForWrite().supportsUnicode());
        assertTrue(NameCodec.factory(PUNYCODE, COMPRESSION).getForWrite().supportsUnicode());
        assertTrue(NameCodec.factory(MDNS_UTF_8, COMPRESSION).getForWrite().supportsUnicode());

        assertFalse(NameCodec.factory(COMPRESSION).getForWrite().supportsUnicode());
        assertFalse(NameCodec.factory(WRITE_TRAILING_DOT).getForWrite().supportsUnicode());
        assertFalse(NameCodec.factory(READ_TRAILING_DOT).getForWrite().supportsUnicode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUtf8AndPunycodeCannotBeCombined() throws Exception {
        NameCodecFactory f = NameCodec.factory(COMPRESSION,
                PUNYCODE, MDNS_UTF_8);
        fail("Exception should have been thrown but got " + f);
    }

    @Test
    public void testPunycode() throws Exception {
        testOne("продается.com", PUNYCODE);
    }

    @Test
    public void testCompressed() throws Exception {
        testOne("продается.com", COMPRESSION, PUNYCODE);
    }

    @Test
    public void testUtf8() throws Exception {
        testOne("продается.com", MDNS_UTF_8);
    }

    @Test
    public void testUtf8Compressed() throws Exception {
        testOne("продается.com", COMPRESSION, MDNS_UTF_8);
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
}
