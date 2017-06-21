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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import static io.netty.util.internal.ObjectUtil.checkPositive;
import io.netty.util.internal.UnstableApi;

/**
 * Buffer size limits for encoders.
 */
@UnstableApi
public final class Limits {

    private static final int ABSOLUTE_MINIMUM_DNS_PACKET_SIZE = 48;
    private static final int DEFAULT_MIN_PACKET_SIZE = 64;
    private static final int DEFAULT_MAX_PACKET_SIZE = 576;
    private static final int DEFAULT_ABSOLUTE_MAX_PACKET_SIZE = 4096;

    private static final AttributeKey<Integer> MAX_UDP_PAYLOAD_SIZE
            = AttributeKey.newInstance("maxUdpPayloadSize");

    private final int minPacketSize;
    private final int maxPacketSize;
    private final int absoluteMaxPacketSize;

    public static Limits DEFAULT = new Limits(DEFAULT_MIN_PACKET_SIZE,
            DEFAULT_MAX_PACKET_SIZE, DEFAULT_ABSOLUTE_MAX_PACKET_SIZE);

    public Limits(int minPacketSize, int maxPacketSize,
            int absoluteMaxPacketSize) {
        this.minPacketSize = minPacketSize;
        this.maxPacketSize = maxPacketSize;
        this.absoluteMaxPacketSize = absoluteMaxPacketSize;
        if (minPacketSize < ABSOLUTE_MINIMUM_DNS_PACKET_SIZE) {
            throw new IllegalArgumentException("Packet minimum size too small "
                    + "- minimum " + ABSOLUTE_MINIMUM_DNS_PACKET_SIZE + " bytes");
        }
        if (maxPacketSize < ABSOLUTE_MINIMUM_DNS_PACKET_SIZE) {
            throw new IllegalArgumentException("Packet maximum size too small "
                    + "- minimum " + ABSOLUTE_MINIMUM_DNS_PACKET_SIZE + " bytes");
        }
        if (absoluteMaxPacketSize < ABSOLUTE_MINIMUM_DNS_PACKET_SIZE) {
            throw new IllegalArgumentException("Absolute maximum size too small "
                    + "- minimum " + ABSOLUTE_MINIMUM_DNS_PACKET_SIZE + " bytes");
        }
        if (maxPacketSize < minPacketSize) {
            throw new IllegalArgumentException("Minimum packet size "
                    + minPacketSize + " is > passed maxPacketSize "
                    + maxPacketSize);
        }
        if (absoluteMaxPacketSize < maxPacketSize) {
            throw new IllegalArgumentException("Absolute max packet size "
                    + absoluteMaxPacketSize + " is less than max packet size "
                    + maxPacketSize);
        }
    }

    /**
     * Get the minimum packet size for buffer allocations.
     */
    public int minPacketSize() {
        return minPacketSize;
    }

    /**
     * Get the maximum packet size for buffer alloctions, assuming a
     * larger value was not specified by an inbound packet in an EDNS OPT
     * record, and set by calling {@code setMaxUdpPayloadSize()}, in
     * which case the absolute max packet size value applies.
     */
    public int maxPacketSize() {
        return maxPacketSize;
    }

    /**
     * Get the maximum packet size for buffer allocations, even in the
     * case that a larger packet size is possible.
     */
    public int absoluteMaxPacketSize() {
        return absoluteMaxPacketSize;
    }

    /**
     * Allocate a buffer in accordance with the limits set by this
     * instance, using the passed context's byte buf allocator.
     */
    public ByteBuf buffer(ChannelHandlerContext ctx) {
        int maxSize = getMaxUdpPayloadSize(ctx, maxPacketSize,
                absoluteMaxPacketSize);
        return ctx.alloc().ioBuffer(minPacketSize, maxSize);
    }

    /**
     * Set the maximum udp payload size based on an inbound
     * request.  The value is stored as a channel attribute and is
     * retrievable from getMaxUdpPayloadSize().
     */
    public static void setMaxUdpPayloadSize(ChannelHandlerContext ctx,
            int value) {
        ctx.channel().attr(MAX_UDP_PAYLOAD_SIZE).set(value);
    }

    /**
     * Get the maxium UDP payload size, which may have been set
     * via a call to {@code setMaxUdpPayloadSize()}.
     */
    public static int getMaxUdpPayloadSize(ChannelHandlerContext ctx,
            int defaultValue, int maximum) {
        if (ctx == null || ctx.channel() == null) { // tests w/ fake channel
            return defaultValue;
        }
        Attribute<Integer> val = ctx.channel().attr(MAX_UDP_PAYLOAD_SIZE);
        Integer result = val.get();
        if (result == null) {
            return Math.min(maximum, defaultValue);
        }
        return Math.min(maximum, result);
    }

    @Override
    public String toString() {
        return "Limits{" + "minPacketSize=" + minPacketSize
                + ", maxPacketSize=" + maxPacketSize
                + ", absoluteMaxPacketSize=" + absoluteMaxPacketSize + '}';
    }

    /**
     * Create a new LimitsBuilder for creating a Limits instance.
     */
    public static LimitsBuilder builder() {
        return new LimitsBuilder();
    }

    /**
     * A builder for Limits.
     */
    @UnstableApi
    public static final class LimitsBuilder {

        private int minPacketSize = DEFAULT_MIN_PACKET_SIZE;
        private int maxPacketSize = DEFAULT_MAX_PACKET_SIZE;
        private int absoluteMaxPacketSize = DEFAULT_ABSOLUTE_MAX_PACKET_SIZE;

        public LimitsBuilder withMinPacketSize(int size) {
            minPacketSize = checkPositive(size, "size");
            return this;
        }

        public LimitsBuilder withMaxPacketSize(int maxSize) {
            maxPacketSize = checkPositive(maxSize, "maxSize");
            return this;
        }

        public LimitsBuilder withAbsoluteMaxPacketSize(int absMax) {
            absoluteMaxPacketSize = checkPositive(absMax, "absMax");
            return this;
        }

        public Limits build() {
            return new Limits(minPacketSize, maxPacketSize, absoluteMaxPacketSize);
        }
    }
}
