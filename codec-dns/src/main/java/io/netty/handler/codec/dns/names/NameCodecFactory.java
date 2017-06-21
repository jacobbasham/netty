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

import io.netty.util.internal.UnstableApi;

/**
 * NameCodecFactory instances are providers of NameCodec, which can ensure you
 * always get a NameCodec that does not retain any state (such as the list of
 * offsets for compression pointers) from a previous use.
 */
@UnstableApi
public interface NameCodecFactory {

    /**
     * Get a NameCodec for reading. The result of this call may not be used for
     * writing, and may throw an exception to prevent that.
     *
     * @return A namewriter
     */
    NameCodec getForRead();

    /**
     * Get a NameCodec for writing. The result of this call may not be used for
     * reading, and may throw an exception to prevent that.
     *
     * @return A namewriter
     */
    NameCodec getForWrite();
}
