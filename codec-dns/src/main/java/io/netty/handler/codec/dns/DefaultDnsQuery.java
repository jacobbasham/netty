/*
 * Copyright 2015 The Netty Project
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

import io.netty.util.ReferenceCounted;
import io.netty.util.internal.UnstableApi;
import java.util.Set;

/**
 * The default {@link DnsQuery} implementation.
 */
@UnstableApi
public class DefaultDnsQuery<M extends ReferenceCounted & DnsQuery<M>>
        extends AbstractDnsMessage<M> implements DnsQuery<M> {

    /**
     * Creates a new instance with the {@link DnsOpCode#QUERY} {@code opCode}.
     *
     * @param id the {@code ID} of the DNS query
     */
    public DefaultDnsQuery(int id) {
        this(id, DnsOpCode.QUERY);
    }

    /**
     * Creates a new instance.
     *
     * @param id the {@code ID} of the DNS query
     * @param opCode the {@code opCode} of the DNS query
     * @param flags the boolean message flags as a set
     */
    public DefaultDnsQuery(int id, DnsOpCode opCode, Set<DnsMessageFlags> flags) {
        super(id, opCode, flags);
        if (this.flags.contains(DnsMessageFlags.IS_REPLY)) {
            throw new IllegalArgumentException("Cannot pass the IS_REPLY flag to a question");
        }
    }

    public DefaultDnsQuery(int id, DnsOpCode opCode, DnsMessageFlags... flags) {
        this(id, opCode, DnsMessageFlags.setOf(true, flags));
    }

    public DefaultDnsQuery(int id, DnsMessageFlags... flags) {
        this(id, DnsOpCode.QUERY, DnsMessageFlags.setOf(true, flags));
    }
    @Override
    public String toString() {
        return DnsMessageUtil.appendQuery(new StringBuilder(128), this).toString();
    }
}
