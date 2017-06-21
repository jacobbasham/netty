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

import io.netty.util.concurrent.FastThreadLocal;

/**
 * Codec factory for NameCodecs that use pointer compression.
 *
 */
final class CompressingNameCodecFactory implements NameCodecFactory {

    private final FastThreadLocal<NameCodec> writer = new FastThreadLocal<NameCodec>();
    // StandardNameWriter and CompressingNameCodec delegate to the same defaultRead()
    // method and have no state when reading
    private final NameCodec readInstance;
    final boolean writeTrailingDot;

    CompressingNameCodecFactory(boolean readTrailingDot, boolean writeTrailingDot) {
        readInstance = new WriteEnforcingCodecWrapper(readTrailingDot
                ? NameCodec.DEFAULT_TRAILING_DOT : NameCodec.DEFAULT, false);
        this.writeTrailingDot = writeTrailingDot;
    }

    @Override
    public NameCodec getForRead() {
        return readInstance;
    }

    @Override
    public NameCodec getForWrite() {
        NameCodec result;
        if (!writer.isSet()) {
            result = new WriteEnforcingCodecWrapper(new CompressingNameCodec(
                    readInstance == NameCodec.DEFAULT_TRAILING_DOT,
                    writeTrailingDot), true);
            writer.set(result);
        } else {
            result = writer.get();
        }
        return result;
    }

}
