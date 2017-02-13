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

import static io.netty.handler.codec.dns.DnsMessageFlags.AUTHORITATIVE_ANSWER;
import io.netty.handler.codec.dns.DnsMessageFlags.FlagSet;
import static io.netty.handler.codec.dns.DnsMessageFlags.IS_REPLY;
import static io.netty.handler.codec.dns.DnsMessageFlags.RECURSION_AVAILABLE;
import static io.netty.handler.codec.dns.DnsMessageFlags.RECURSION_DESIRED;
import static io.netty.handler.codec.dns.DnsMessageFlags.TRUNCATED;
import java.util.EnumSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 */
public class DnsMessageFlagsTest {

    @Test
    public void testValues() {
        short val = 0;
        for (DnsMessageFlags flag : DnsMessageFlags.values()) {
            assertFalse(flag.read(val));
            val = flag.write(val, true);
            assertTrue(flag.read(val));
            val = flag.write(val, false);
            assertFalse(flag.read(val));
        }
        FlagSet flags = DnsMessageFlags.setOf(false, AUTHORITATIVE_ANSWER, RECURSION_AVAILABLE, TRUNCATED);

        assertEquals(3, flags.size());

        assertTrue(flags.contains(AUTHORITATIVE_ANSWER));
        assertTrue(flags.contains(RECURSION_AVAILABLE));
        assertTrue(flags.contains(TRUNCATED));
        assertFalse(flags.contains(IS_REPLY));
        assertFalse(flags.contains(RECURSION_DESIRED));

        flags.remove(TRUNCATED);
        assertEquals(2, flags.size());
        assertFalse(flags.contains(TRUNCATED));
        assertTrue(flags.contains(AUTHORITATIVE_ANSWER));
        assertTrue(flags.contains(RECURSION_AVAILABLE));
        assertFalse(flags.contains(IS_REPLY));
        assertFalse(flags.contains(RECURSION_DESIRED));

        flags.retainAll(DnsMessageFlags.setOf(true, AUTHORITATIVE_ANSWER, IS_REPLY));
        assertEquals(1, flags.size());
        assertTrue(flags.contains(AUTHORITATIVE_ANSWER));

        flags.add(IS_REPLY); // bit 15 - will be the sign bit
        assertNotEquals(0, flags.value());
        assertTrue("FLAGS: " + flags + "(" + flags.value() + ")", flags.contains(IS_REPLY));

        flags.removeAll(DnsMessageFlags.setOf(false, AUTHORITATIVE_ANSWER, IS_REPLY));
        assertTrue(flags.isEmpty());

        FlagSet readOnly = flags.unmodifiableView();
        try {
            readOnly.add(RECURSION_DESIRED);
            fail("Exception should have been thrown");
        } catch (UnsupportedOperationException ex) {
            //do nothing
        }

        Set<DnsMessageFlags> nue = EnumSet.of(TRUNCATED, AUTHORITATIVE_ANSWER, IS_REPLY);
        FlagSet fs = DnsMessageFlags.writableCopyOf(nue);
        assertTrue(fs.contains(TRUNCATED));
        assertTrue(fs.contains(AUTHORITATIVE_ANSWER));
        assertTrue(fs.contains(IS_REPLY));

        assertEquals(fs + "", 3, fs.size());
        fs.remove(TRUNCATED);
        assertEquals(2, fs.size());
        fs.remove(IS_REPLY);
        assertEquals(1, fs.size());
        assertTrue(fs.contains(AUTHORITATIVE_ANSWER));

        FlagSet ano = DnsMessageFlags.forFlags((short) -1); // all 1s
        assertEquals(-30848, ano.value());
    }
}
