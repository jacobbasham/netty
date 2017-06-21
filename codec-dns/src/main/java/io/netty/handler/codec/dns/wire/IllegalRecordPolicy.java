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
package io.netty.handler.codec.dns.wire;

import io.netty.util.internal.UnstableApi;

/**
 * What to do if asked to encode a record which is not spec-compliant, such as
 * including an ANSWER in a question message. Which is right depends on your
 * use-case - if you are simply forwarding data, making a judgement about what
 * to include might be the wrong thing; if you are writing a name server,
 * including invalid records is likely an error and THROW should be the policy;
 * if you are debugging and want to be sure what you're putting on the wire is
 * what you think you are, INCLUDE will do that.
 */
@UnstableApi
public enum IllegalRecordPolicy {
    /**
     * Silently discard records that should not be included.
     */
    DISCARD,
    /**
     * Encode illegal records anyway.
     */
    INCLUDE,
    /**
     * Throw a CorruptedFrameException on encounting an invalid
     * record - the default for things which use this.
     */
    THROW
}
