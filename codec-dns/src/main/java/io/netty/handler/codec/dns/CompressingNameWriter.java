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
import io.netty.buffer.ByteBufUtil;
import java.nio.charset.UnmappableCharacterException;
import java.util.HashMap;
import java.util.Map;

/**
 * Name writer which uses DNS message compression pointers.
 */
final class CompressingNameWriter extends NameCodec {

    private final Map<CharSequence, Integer> positions = new HashMap<CharSequence, Integer>();

    @Override
    public void close() {
        positions.clear();
    }

    @Override
    public void writeName(CharSequence name, ByteBuf buf) throws UnmappableCharacterException,
            InvalidDomainNameException {
        checkName(name);
        int max = name.length();
        int lastStart = 0;
        for (int i = 0; i < max; i++) {
            char c;
            if (i == max - 1 || (c = name.charAt(i)) == '.' || c == '@') {
                CharSequence label = name.subSequence(lastStart, i == max - 1 ? max : i);
                int length = label.length();
                if (length == 0) {
                    continue;
                } else if (length > 63) {
                    throw new InvalidDomainNameException(name, "Dns cannot encode labels longer than 63 chars. '"
                            + label + "' has " + label.length());
                }
                // A pointer may start at any *label* and continue to the end of the name
                CharSequence remainder = name.subSequence(lastStart, max);
                Integer pos = positions.get(remainder);
                if (pos == null) {
                    positions.put(remainder, buf.writerIndex());
                    lastStart = i + 1;
                    if (length == 0) {
                        continue;
                    }
                    buf.writeByte(length);
                    ByteBufUtil.writeAscii(buf, label);
                    if (i == max - 1) {
                        buf.writeByte(0);
                    }
                } else {
                    int val = pos | 0xc000;
                    buf.writeChar(val);
                    break;
                }
            }
        }
    }
}
