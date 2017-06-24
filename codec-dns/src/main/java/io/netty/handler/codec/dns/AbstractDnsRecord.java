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

import static io.netty.handler.codec.dns.DnsMessageUtil.nameHashCode;
import static io.netty.handler.codec.dns.DnsMessageUtil.namesEqual;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.UnstableApi;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * A skeletal implementation of {@link DnsRecord}.
 */
@UnstableApi
public abstract class AbstractDnsRecord implements DnsRecord {

    private final CharSequence name;
    private final DnsRecordType type;
    private final short dnsClass;
    private final int timeToLive;
    private final boolean isUnicastResponse;

    /**
     * Creates a new {@link #CLASS_IN IN-class} record.
     *
     * @param name the domain name
     * @param type the type of the record
     * @param timeToLive the TTL value of the record
     */
    protected AbstractDnsRecord(CharSequence name, DnsRecordType type, long timeToLive) {
        this(name, type, DnsClass.IN, timeToLive);
    }

    /**
     * Creates a new record.
     *
     * @param name the domain name
     * @param type the type of the record
     * @param dnsClass the class of the record, usually one of the following:
     *                 <ul>
     *                     <li>{@link #CLASS_IN}</li>
     *                     <li>{@link #CLASS_CSNET}</li>
     *                     <li>{@link #CLASS_CHAOS}</li>
     *                     <li>{@link #CLASS_HESIOD}</li>
     *                     <li>{@link #CLASS_NONE}</li>
     *                     <li>{@link #CLASS_ANY}</li>
     *                 </ul>
     * @param timeToLive the TTL value of the record
     */
    protected AbstractDnsRecord(CharSequence name, DnsRecordType type, DnsClass dnsClass, long timeToLive) {
        this(name, type, dnsClass.intValue(), timeToLive);
    }

    protected AbstractDnsRecord(CharSequence name, DnsRecordType type, int dnsClass, long timeToLive) {
        this(name, type, dnsClass, timeToLive, false);
    }

    protected AbstractDnsRecord(CharSequence name, DnsRecordType type, int dnsClass, long timeToLive,
            boolean isUnicastResponse) {
        if (timeToLive < 0 && !type.isMetaTypeOrQType()) {
            throw new IllegalArgumentException("timeToLive: " + timeToLive + " (expected: >= 0)");
        }
        this.name = checkNotNull(name, "name");
        this.type = checkNotNull(type, "type");
        this.dnsClass = (short) dnsClass;
        this.timeToLive = (int) timeToLive;
        this.isUnicastResponse = isUnicastResponse;
    }

    @Override
    public boolean isUnicastOrCacheFlushRequested() {
        return isUnicastResponse;
    }

    @Override
    public CharSequence name() {
        return name;
    }

    @Override
    public DnsRecordType type() {
        return type;
    }

    @Override
    public DnsClass dnsClass() {
        return DnsClass.valueOf(dnsClass);
    }

    @Override
    public int dnsClassValue() {
        return dnsClass & 0xFFFF;
    }

    @Override
    public long timeToLive() {
        return (long) timeToLive & 0xffffffffL;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof DnsRecord)) {
            return false;
        }

        final DnsRecord that = (DnsRecord) obj;

        return type().intValue() == that.type().intValue()
                && dnsClassValue() == that.dnsClassValue()
                && timeToLive() == that.timeToLive()
                && namesEqual(name(), that.name());
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + nameHashCode(name);
        hash = 67 * hash + type.hashCode();
        hash = 67 * hash + this.dnsClass;
        hash = 67 * hash + (int) (this.timeToLive ^ (this.timeToLive >>> 32));
        return hash;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(64);

        buf.append(StringUtil.simpleClassName(this))
                .append('(')
                .append(name())
                .append(' ')
                .append(timeToLive())
                .append(' ');

        if (isUnicastOrCacheFlushRequested()) {
            buf.append("(unicast) ");
        }

        DnsMessageUtil.appendRecordClass(buf, dnsClass())
                .append(' ')
                .append(type().name())
                .append(')');

        return buf.toString();
    }

    /**
     * Stub implementation which throws UnsupportedOperationException.
     */
    @Override
    public DnsRecord withTimeToLiveAndDnsClass(long timeToLive, int dnsClass) {
        throw new UnsupportedOperationException("Not supported.");
    }
}
