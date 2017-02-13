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
import io.netty.buffer.Unpooled;
import io.netty.util.AsciiString;
import java.nio.charset.UnmappableCharacterException;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests NameCodec functionality.
 */
public class NameWriterTest {

    @Test
    public void testCompressingNameWriter() throws Exception {
        assertTrue(true);
        NameCodec wri = NameCodec.compressingNameWriter();
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

    private void testOne(String name) throws Exception {
        ByteBuf buf = Unpooled.buffer();
        NameCodec nw = NameCodec.standardNameWriter();
        nw.writeName(name, buf);
        String received = nw.readName(buf).toString();
        assertEquals(name + " - String representation", name, received);
        AsciiString sb = new AsciiString(name);
        buf = Unpooled.buffer();
        nw.writeName(sb, buf);
        received = nw.readName(buf).toString();
        assertEquals(name + " - CharSequence representation '" + received + "'", name.toString(), received);
    }

    @Test(expected = UnmappableCharacterException.class)
    public void testInvalidChars() throws Exception {
        testOne("продается");
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
        NameCodec.DefaultNameCodec nw = new NameCodec.DefaultNameCodec();
        nw.writeName0(sb, buf);
        nw.readName(buf);
    }

    @Test(expected = InvalidDomainNameException.class)
    public void testInvalidLabel() throws Exception {
        ByteBuf buf = Unpooled.buffer();
        NameCodec.DefaultNameCodec nw = new NameCodec.DefaultNameCodec();
        nw.writeName("foo.-bar.com", buf);
    }
}
