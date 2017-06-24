/*
 * Copyright 2016 The Netty Project
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

import static io.netty.handler.codec.dns.DnsMessageUtil.nameHashCode;
import static io.netty.handler.codec.dns.DnsMessageUtil.namesEqual;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class AbstractDnsRecordTest {

    @Test
    public void testHashCode() {
        assertEquals(nameHashCode("netty.io."), nameHashCode("netty.io"));
        assertNotEquals(nameHashCode("netty.io."), nameHashCode("netty.io.foo"));
        assertEquals(nameHashCode("netty.io...."), nameHashCode("netty.io"));
        assertNotEquals(0, nameHashCode("netty.io."));
        assertNotEquals(0, nameHashCode("netty.i."));
        assertEquals(0, nameHashCode(""));
        assertEquals(0, nameHashCode("."));
        assertEquals(nameHashCode("."), nameHashCode("...."));
    }

    @Test
    public void testEquals() {
        assertTrue(namesEqual("netty.io.", "netty.io"));
        assertTrue(namesEqual("netty.io.", "netty.io..."));
        assertFalse(namesEqual("netty.io.", "netty.i"));
        assertFalse(namesEqual("netty.io.", "netty.i."));
        assertTrue(namesEqual(".", "."));
        assertTrue(namesEqual("", ""));
    }
}
