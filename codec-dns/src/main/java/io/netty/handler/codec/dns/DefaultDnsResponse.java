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

import io.netty.util.internal.UnstableApi;
import static io.netty.handler.codec.dns.DnsMessageFlags.AUTHORITATIVE_ANSWER;
import static io.netty.handler.codec.dns.DnsMessageFlags.IS_REPLY;
import static io.netty.handler.codec.dns.DnsMessageFlags.RECURSION_AVAILABLE;
import static io.netty.handler.codec.dns.DnsMessageFlags.TRUNCATED;
import io.netty.util.ReferenceCounted;
import io.netty.util.internal.ObjectUtil;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import java.util.Set;

/**
 * The default {@link DnsResponse} implementation.
 */
@UnstableApi
public class DefaultDnsResponse<M extends ReferenceCounted & DnsResponse<M>>
        extends AbstractDnsMessage<M> implements DnsResponse<M> {

    private DnsResponseCode code;

    /**
     * Creates a new instance with the {@link DnsOpCode#QUERY} {@code opCode} and
     * the {@link DnsResponseCode#NOERROR} {@code RCODE}.
     *
     * @param id the {@code ID} of the DNS response
     */
    public DefaultDnsResponse(int id) {
        this(id, DnsOpCode.QUERY, DnsResponseCode.NOERROR);
    }

    /**
     * Creates a new instance with the {@link DnsResponseCode#NOERROR} {@code RCODE}.
     *
     * @param id the {@code ID} of the DNS response
     * @param opCode the {@code opCode} of the DNS response
     */
    public DefaultDnsResponse(int id, DnsOpCode opCode) {
        this(id, opCode, DnsResponseCode.NOERROR);
    }

    /**
     * Creates a new instance.
     *
     * @param id the {@code ID} of the DNS response
     * @param opCode the {@code opCode} of the DNS response
     * @param code the {@code RCODE} of the DNS response
     */
    public DefaultDnsResponse(int id, DnsOpCode opCode, DnsResponseCode code, DnsMessageFlags... flags) {
        super(id, opCode, flags);
        this.flags.add(IS_REPLY);
        this.code = ObjectUtil.checkNotNull(code, "code");
    }

    /**
     * Creates a new instance.
     *
     * @param id the {@code ID} ofDefaultDnsResponse the DNS response
     * @param opCode the {@code opCode} of the DNS response
     * @param code the {@code RCODE} of the DNS response
     * @param flags
     */
    public DefaultDnsResponse(int id, DnsOpCode opCode, DnsResponseCode code, Set<DnsMessageFlags> flags) {
        super(id, opCode, flags);
        this.flags.add(IS_REPLY);
        this.code = ObjectUtil.checkNotNull(code, "code");
    }

    @Override
    public boolean isAuthoritativeAnswer() {
        return flags.contains(AUTHORITATIVE_ANSWER);
    }

    @Override
    public M setAuthoritativeAnswer(boolean authoritativeAnswer) {
        if (authoritativeAnswer) {
            flags.add(AUTHORITATIVE_ANSWER);
        } else {
            flags.remove(AUTHORITATIVE_ANSWER);
        }
        return cast(this);
    }

    @Override
    public boolean isTruncated() {
        return flags.contains(TRUNCATED);
    }

    @Override
    public M setTruncated(boolean truncated) {
        if (truncated) {
            flags.add(TRUNCATED);
        } else {
            flags.remove(TRUNCATED);
        }
        return cast(this);
    }

    @Override
    public boolean isRecursionAvailable() {
        return flags.contains(RECURSION_AVAILABLE);
    }

    @Override
    public M setRecursionAvailable(boolean recursionAvailable) {
        if (recursionAvailable) {
            flags.add(RECURSION_AVAILABLE);
        } else {
            flags.remove(RECURSION_AVAILABLE);
        }
        return cast(this);
    }

    @Override
    public DnsResponseCode code() {
        return code;
    }

    @Override
    public M setCode(DnsResponseCode code) {
        this.code = checkNotNull(code, "code");
        return cast(this);
    }

    @Override
    public String toString() {
        return DnsMessageUtil.appendResponse(new StringBuilder(128), this).toString();
    }
}
