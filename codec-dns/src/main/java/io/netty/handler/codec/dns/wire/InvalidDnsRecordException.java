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

import io.netty.handler.codec.EncoderException;

/**
 * Thrown when the encoder is asked to encode something that will result
 * in an invalid DNS packet, if the encoder's IllegalRecordPolicy is
 * THROW.
 * @see IllegalRecordPolicy
 */
public final class InvalidDnsRecordException extends EncoderException {

    public InvalidDnsRecordException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidDnsRecordException(String message) {
        super(message);
    }

    public InvalidDnsRecordException(Throwable cause) {
        super(cause);
    }

}
