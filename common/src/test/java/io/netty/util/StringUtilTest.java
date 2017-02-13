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
package io.netty.util;

import io.netty.util.internal.StringUtil;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Tests string comparison methods.
 */
public class StringUtilTest {

    private final String test = "Netty is awesome!";
    private final String unlike = test + " ";
    private final AsciiString ascii = new AsciiString("Netty is awesome!");
    private final AsciiString upper = new AsciiString("NETTY IS AWESOME!");

    @Test
    public void testEquality() {
        assertTrue(StringUtil.charSequencesEqual(test, ascii, false));
        assertTrue(StringUtil.charSequencesEqual(test, ascii, true));
        assertTrue(StringUtil.charSequencesEqual(test, upper, true));
        assertFalse(StringUtil.charSequencesEqual(test, upper, false));
        assertFalse(StringUtil.charSequencesEqual(test, unlike, false));
        assertFalse(StringUtil.charSequencesEqual(ascii, unlike, false));
        assertFalse(StringUtil.charSequencesEqual(upper, unlike, false));
        assertFalse(StringUtil.charSequencesEqual(test, unlike, true));
        assertFalse(StringUtil.charSequencesEqual(ascii, unlike, true));
        assertFalse(StringUtil.charSequencesEqual(upper, unlike, true));
    }

    @Test
    public void testHashCode() {
        assertEquals(test.hashCode(), StringUtil.charSequenceHashCode(test, false));
        assertEquals(test.toLowerCase().hashCode(), StringUtil.charSequenceHashCode(test, true));
        assertEquals(test.hashCode(), StringUtil.charSequenceHashCode(ascii, false));
        assertNotEquals(test.hashCode(), StringUtil.charSequenceHashCode(unlike, false));
        assertNotEquals(test.hashCode(), StringUtil.charSequenceHashCode(unlike, true));
    }
}
