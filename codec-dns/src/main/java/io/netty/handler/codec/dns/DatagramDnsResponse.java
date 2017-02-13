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

import io.netty.channel.AddressedEnvelope;
import io.netty.util.internal.UnstableApi;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Set;

/**
 * A {@link DnsResponse} implementation for UDP/IP.
 */
@UnstableApi
public class DatagramDnsResponse extends DefaultDnsResponse<DatagramDnsResponse>
        implements AddressedEnvelope<DatagramDnsResponse, InetSocketAddress> {

    private final InetSocketAddress sender;
    private final InetSocketAddress recipient;

    /**
     * Creates a new instance with the {@link DnsOpCode#QUERY} {@code opCode} and
     * the {@link DnsResponseCode#NOERROR} {@code RCODE}.
     *
     * @param sender the address of the sender
     * @param recipient the address of the recipient
     * @param id the {@code ID} of the DNS response
     */
    public DatagramDnsResponse(InetSocketAddress sender, InetSocketAddress recipient, int id) {
        this(sender, recipient, id, DnsOpCode.QUERY, DnsResponseCode.NOERROR,
                DnsMessageFlags.setOf(false, DnsMessageFlags.IS_REPLY));
    }

    /**
     * Creates a new instance with the {@link DnsResponseCode#NOERROR} responseCode.
     *
     * @param sender the address of the sender
     * @param recipient the address of the recipient
     * @param id the {@code ID} of the DNS response
     * @param opCode the {@code opCode} of the DNS response
     */
    public DatagramDnsResponse(InetSocketAddress sender, InetSocketAddress recipient, int id, DnsOpCode opCode) {
        this(sender, recipient, id, opCode, DnsResponseCode.NOERROR,
                DnsMessageFlags.setOf(false, DnsMessageFlags.IS_REPLY));
    }

    /**
     * Creates a new instance.
     *
     * @param sender the address of the sender
     * @param recipient the address of the recipient
     * @param id the {@code ID} of the DNS response
     * @param opCode the {@code opCode} of the DNS response
     * @param responseCode the {@code RCODE} of the DNS response
     */
    public DatagramDnsResponse(
            InetSocketAddress sender, InetSocketAddress recipient,
            int id, DnsOpCode opCode, DnsResponseCode responseCode, Set<DnsMessageFlags> flags) {
        super(id, opCode, responseCode, flags);

        if (recipient == null && sender == null) {
            throw new NullPointerException("recipient and sender");
        }

        this.sender = sender;
        this.recipient = recipient;
    }

    public DatagramDnsResponse(InetSocketAddress sender, InetSocketAddress recipient, int id,
            DnsResponse orig) {
        this(sender, recipient, id, orig.opCode(), orig.code(), orig.flags());
        setZ(orig.z());
        for (DnsSection section : DnsSection.values()) {
            int max = orig.count(section);
            for (int i = 0; i < max; i++) {
                DnsRecord record = orig.recordAt(section, i);
                DnsRecord replacement = record;
                if (record instanceof DnsRawRecord) {
                    replacement = ((DnsRawRecord) record).copy();
                }
                addRecord(section, replacement);
            }
        }
    }

    @Override
    public DatagramDnsResponse content() {
        return this;
    }

    @Override
    public InetSocketAddress sender() {
        return sender;
    }

    @Override
    public InetSocketAddress recipient() {
        return recipient;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!super.equals(obj)) {
            return false;
        }

        if (!(obj instanceof AddressedEnvelope)) {
            return false;
        }

        @SuppressWarnings("unchecked")
        final AddressedEnvelope<?, SocketAddress> that = (AddressedEnvelope<?, SocketAddress>) obj;
        if (sender() == null) {
            if (that.sender() != null) {
                return false;
            }
        } else if (!sender().equals(that.sender())) {
            return false;
        }

        if (recipient() == null) {
            if (that.recipient() != null) {
                return false;
            }
        } else if (!recipient().equals(that.recipient())) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int hashCode = super.hashCode();
        if (sender() != null) {
            hashCode = hashCode * 31 + sender().hashCode();
        }
        if (recipient() != null) {
            hashCode = hashCode * 31 + recipient().hashCode();
        }
        return hashCode;
    }
}
