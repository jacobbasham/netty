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

import io.netty.handler.codec.dns.DnsMessage;
import io.netty.util.internal.UnstableApi;

/**
 * Used by DnsMessageEncoder to reduce the size of a DnsResponse which is too
 * big for a UDP packet, reducing its payload and setting its truncated flag.
 * Will be called repeatedly, with increasing values of the {@code iteration}
 * parameter until either the message fits the buffer, or the winnower returns
 * null.  The final call should strip all messages out and set the truncated
 * flag - that should be small enough for any UDP packet and will instruct
 * the caller to retry using TCP instead.
 */
@UnstableApi
public class DnsMessageWinnower {

    /**
     * Reduce the size of the passed DnsResponse to try to make it fit in a
     * packet. Return null if no further winnowing can be done.
     * <p>
     * Implementations that remove non-redundant data should set the truncated
     * flag on the result.
     * <p>
     * The returned message may be the same instance as was passed, or a new
     * one.
     * <p>
     * Return {@code null} to indicate the response has been reduced as much as
     * it can be, and this method should not be called again for this message.
     * <p>
     * The default implementation simply returns null.
     *
     * @param resp The response to modify or replace
     * @param maxSize The maximum buffer capacity / packet size
     * @param iteration The number of times this method has been called for this
     * packet
     * @return A DNS response or null if nothing further can be done.
     */
    protected <T extends DnsMessage<?>> T winnow(T resp, int maxSize, int iteration) {
        return null;
    }
}
