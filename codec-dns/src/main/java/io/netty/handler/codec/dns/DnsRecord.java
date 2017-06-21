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

/**
 * A DNS resource record.
 */
@UnstableApi
public interface DnsRecord {
    /**
     * Returns the name of this resource record.
     */
    CharSequence name();

    /**
     * Returns the type of this resource record.
     */
    DnsRecordType type();

    /**
     * Returns the class of this resource record.
     *
     * @return the class value, usually one of the following:
     *         <ul>
     *             <li>{@link DnsClass.IN}</li>
     *             <li>{@link DnsClass.CSNET}</li>
     *             <li>{@link DnsClass.CHAOS}</li>
     *             <li>{@link DnsClass.HESIOD}</li>
     *             <li>{@link DnsClass.NONE}</li>
     *             <li>{@link DnsClass.ANY}</li>
     *         </ul>
     */
    DnsClass dnsClass();

    /**
     * Returns the time to live after reading for this resource record.
     */
    long timeToLive();

    /**
     * Returns the raw integer value of the dnsClass field (used for
     * other purposes by OPT pseudo-records).
     */
    int dnsClassValue();

    /**
     * Returns either this record, with its time to live and dnsClass
     * values set to the passed values, or a new record (callers should
     * test to see which has happened).  This is needed becuase EDNS
     * breaks encapsulation to store the high 12 bits of the response code in
     * an OPT record in the ADDITIONAL section - a message encoder that has to
     * send the message's response code on the wire and encounters a response
     * code > 15 (e.g. BADCOOKIE) needs to find or create an OPT record and
     * replace 8 bits of its timeToLive field, and also potentially the dns
     * class value with the UDP maximum payload size.
     * <p>
     * Encoders should take care not to overwrite bits they are not using.
     *
     * @param timeToLive The new timeToLive value
     * @param dnsClass The new dnsClass value
     * @return Either this record, updated, or a new record which may replace
     * this one in the outgoing message
     */
    DnsRecord withTimeToLiveAndDnsClass(long timeToLive, int dnsClass);

    /**
     * <strong>mDNS only</strong> For responses, whether or not
     * this is a unicast DNS unicast response;  for questions, whether or
     * not a unicast response is preferred.
     */
    boolean isUnicast();

}
