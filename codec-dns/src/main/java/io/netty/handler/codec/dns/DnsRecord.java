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

/**
 * A DNS resource record.
 */
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
}
