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
import static io.netty.handler.codec.dns.names.NameCodecFeature.PUNYCODE;
import static io.netty.handler.codec.dns.names.NameCodecFeature.READ_TRAILING_DOT;
import static io.netty.handler.codec.dns.names.NameCodecFeature.WRITE_TRAILING_DOT;
import java.nio.charset.UnmappableCharacterException;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * Replicates the tests that used to be in AbstractDnsRecordTest, to ensure that
 * lengths that are legal are not reported as illegal, and vice versa.
 */
public class NameLengthsTest {

    private void testOneName(String name) throws UnmappableCharacterException, DnsDecoderException {
        NameCodec codec = NameCodec.get(READ_TRAILING_DOT, WRITE_TRAILING_DOT, PUNYCODE);
        ByteBuf buf = Unpooled.buffer(name.length() + name.length() / 2);
        codec.writeName(name, buf);
        CharSequence read = codec.readName(buf);
        String lookFor = name;
        if (!name.endsWith(".")) {
            lookFor += '.';
        }
        assertEquals("Non match '" + name + "' returned '" + read + "' by "
                + codec, lookFor, read.toString());
    }

    private void testRawPunycode(String name, String expected)
            throws UnmappableCharacterException, DnsDecoderException {
        NameCodec writeCodec = NameCodec.get(READ_TRAILING_DOT, WRITE_TRAILING_DOT, PUNYCODE);
        NameCodec readCodec = NameCodec.get(READ_TRAILING_DOT, WRITE_TRAILING_DOT);
        ByteBuf buf = Unpooled.buffer(name.length() + name.length() / 2);
        writeCodec.writeName(name, buf);
        CharSequence read = readCodec.readName(buf);
        assertEquals("Non match '" + name + "' returned '" + read + "' by "
                + writeCodec, expected, read.toString());
    }

    @Test
    public void testValidDomainName() throws Throwable {
        testOneName("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    }

    @Test
    public void testValidDomainNameUmlaut() throws Throwable {
        String name = "ä";
        testOneName(name);
        testRawPunycode(name, "xn--4ca.");
    }

    @Test
    public void testValidDomainNameTrailingDot() throws Throwable {
        testOneName("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.");
    }

    @Test
    public void testValidDomainNameUmlautTrailingDot() throws Throwable {
        String name = "ä.";
        testOneName(name);
        testRawPunycode(name, "xn--4ca.");
    }

    @Test(expected = InvalidDomainNameException.class)
    public void testValidDomainNameLength() throws Throwable {
        String name = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        testOneName(name);
    }

    @Test(expected = InvalidDomainNameException.class)
    public void testValidDomainNameUmlautLength() throws Throwable {
        String name = "äaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        testOneName(name);
    }
}
