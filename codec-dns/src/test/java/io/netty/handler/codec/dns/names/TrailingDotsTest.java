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
import java.util.Arrays;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class TrailingDotsTest {

    @Test
    public void testDots() throws Throwable {
        NameCodec c = new NonCompressingNameCodec(true, false);
        ByteBuf b = Unpooled.buffer(12);
        byte[] junk = new byte[12];
        Arrays.fill(junk, Byte.MIN_VALUE);
        b.setBytes(0, junk);
        c.writeName("netty.io.", b);
        CharSequence s = c.readName(b).toString();
        assertEquals("netty.io.", s.toString());
    }

    @Test
    public void testMultiDots() throws Throwable {
        NameCodec c = new NonCompressingNameCodec(true, false);
        ByteBuf b = Unpooled.buffer(12);
        byte[] junk = new byte[12];
        Arrays.fill(junk, Byte.MIN_VALUE);
        b.setBytes(0, junk);
        c.writeName("netty.io....", b);
        CharSequence s = c.readName(b).toString();
        assertEquals("netty.io.", s.toString());
    }

    @Test
    public void testNothingButDots() throws Throwable {
        NameCodec c = new NonCompressingNameCodec(true, false);
        ByteBuf b = Unpooled.buffer(12);
        byte[] junk = new byte[12];
        Arrays.fill(junk, Byte.MIN_VALUE);
        b.setBytes(0, junk);
        c.writeName("....", b);
        CharSequence s = c.readName(b).toString();
        assertEquals(".", s.toString());
    }

    @Test
    public void testLeadingDots() throws Throwable {
        NameCodec c = new NonCompressingNameCodec(true, false);
        ByteBuf b = Unpooled.buffer(12);
        byte[] junk = new byte[12];
        Arrays.fill(junk, Byte.MIN_VALUE);
        b.setBytes(0, junk);
        c.writeName("..foo", b);
        CharSequence s = c.readName(b).toString();
        assertEquals("..foo.", s);
    }
}
