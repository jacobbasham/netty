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

import java.nio.charset.UnmappableCharacterException;

/**
 * Subclass of UnmappableCharacterException, which is the most descriptive
 * exception we could choose, extended so we can give it a message.
 */
final class UnencodableCharactersException extends UnmappableCharacterException {

    private final CharSequence name;

    UnencodableCharactersException(CharSequence name) {
        super(name.length());
        this.name = name;
    }

    @Override
    public String getMessage() {
        return "Name contains non-ascii character - convert it to punycode first: '" + name + "'";
    }
}
