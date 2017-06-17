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

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.dns.DnsMessage;
import io.netty.handler.codec.dns.DnsQuery;

/**
 * Answers questions in response to DNS requests. Used with DnsServerHandler to
 * answer DNS questions asked of a DNS server, perhaps by reading files or
 * querying another DNS server or whatever.
 */
public interface DnsAnswerProvider {

    /**
     * Called when a DNS query has been received. Note that in the event this
     * answer provider fails asynchronously in some way - i.e. an exception is
     * thrown in some asynchronously called method or some other failure occurs,
     * <i>this answer provider is still responsible</i> for calling the passed
     * <i>DnsResponder</i> with a SERVFAIL or more specific response.
     * <p>
     * If an exception is thrown from this method, an error response will be
     * automatically sent.
     * </p><p>
     * It is expacted that this call complete <b>asynchronously</b>- that is
     * that the <code>callback</code> will not be invoked within the body of the
     * implementation of this method. If you <i>could</i>
     * return a result synchronously, throw that invocation into a callable on
     * the event loop instead. For reasons why this is important, see
     * <a href="http://blog.izs.me/post/59142742143/designing-apis-for-asynchrony">this
     * article</a>
     * or
     * <a href="https://blog.ometer.com/2011/07/24/callbacks-synchronous-and-asynchronous/">this
     * one</a>.
     * </p>
     *
     * @param query The DNS query
     * @param ctx The channel context
     * @param callback Callback to call with a DnsResponse
     * @throws Exception if something goes wrong
     */
    void respond(DnsQuery query, ChannelHandlerContext ctx, DnsResponder callback) throws Exception;

    /**
     * Called when the {@link DnsServerHandler} encounters an exception.
     *
     * @param ctx The context
     * @param cause The exception
     */
    void exceptionCaught(ChannelHandlerContext ctx, DnsMessage message, Throwable cause);

}
