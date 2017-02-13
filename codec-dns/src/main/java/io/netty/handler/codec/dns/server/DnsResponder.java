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
package io.netty.handler.codec.dns.server;

import io.netty.handler.codec.dns.DnsResponse;

/**
 * Callback interface which you call with the response to a DNS query after it
 * has been (possibly asynchronously) computed (which could involve contacting a
 * delegate DNS server or other activity which a synchronous API would not allow
 * for cleanly).
 * @see DnsServerHandler
 */
public interface DnsResponder {

    /**
     * Pass the response here.
     *
     * @param response The response
     * @throws Exception If something goes wrong or if the response is invalid
     */
    void withResponse(DnsResponse response) throws Exception;

}
