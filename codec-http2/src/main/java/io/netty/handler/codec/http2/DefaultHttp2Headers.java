/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import io.netty.handler.codec.ByteStringValueConverter;
import io.netty.handler.codec.DefaultHeaders;
import io.netty.handler.codec.Headers;
import io.netty.util.ByteString;

public class DefaultHttp2Headers extends DefaultHeaders<ByteString> implements Http2Headers {
    private HeaderEntry<ByteString> firstNonPseudo = head;

    public DefaultHttp2Headers() {
        super(ByteStringValueConverter.INSTANCE);
    }

    @Override
    public Http2Headers add(ByteString name, ByteString value) {
        super.add(name, value);
        return this;
    }

    @Override
    public Http2Headers add(ByteString name, Iterable<? extends ByteString> values) {
        super.add(name, values);
        return this;
    }

    @Override
    public Http2Headers add(ByteString name, ByteString... values) {
        super.add(name, values);
        return this;
    }

    @Override
    public Http2Headers addObject(ByteString name, Object value) {
        super.addObject(name, value);
        return this;
    }

    @Override
    public Http2Headers addObject(ByteString name, Iterable<?> values) {
        super.addObject(name, values);
        return this;
    }

    @Override
    public Http2Headers addObject(ByteString name, Object... values) {
        super.addObject(name, values);
        return this;
    }

    @Override
    public Http2Headers addBoolean(ByteString name, boolean value) {
        super.addBoolean(name, value);
        return this;
    }

    @Override
    public Http2Headers addChar(ByteString name, char value) {
        super.addChar(name, value);
        return this;
    }

    @Override
    public Http2Headers addByte(ByteString name, byte value) {
        super.addByte(name, value);
        return this;
    }

    @Override
    public Http2Headers addShort(ByteString name, short value) {
        super.addShort(name, value);
        return this;
    }

    @Override
    public Http2Headers addInt(ByteString name, int value) {
        super.addInt(name, value);
        return this;
    }

    @Override
    public Http2Headers addLong(ByteString name, long value) {
        super.addLong(name, value);
        return this;
    }

    @Override
    public Http2Headers addFloat(ByteString name, float value) {
        super.addFloat(name, value);
        return this;
    }

    @Override
    public Http2Headers addDouble(ByteString name, double value) {
        super.addDouble(name, value);
        return this;
    }

    @Override
    public Http2Headers addTimeMillis(ByteString name, long value) {
        super.addTimeMillis(name, value);
        return this;
    }

    @Override
    public Http2Headers add(Headers<? extends ByteString> headers) {
        super.add(headers);
        return this;
    }

    @Override
    public Http2Headers set(ByteString name, ByteString value) {
        super.set(name, value);
        return this;
    }

    @Override
    public Http2Headers set(ByteString name, Iterable<? extends ByteString> values) {
        super.set(name, values);
        return this;
    }

    @Override
    public Http2Headers set(ByteString name, ByteString... values) {
        super.set(name, values);
        return this;
    }

    @Override
    public Http2Headers setObject(ByteString name, Object value) {
        super.setObject(name, value);
        return this;
    }

    @Override
    public Http2Headers setObject(ByteString name, Iterable<?> values) {
        super.setObject(name, values);
        return this;
    }

    @Override
    public Http2Headers setObject(ByteString name, Object... values) {
        super.setObject(name, values);
        return this;
    }

    @Override
    public Http2Headers setBoolean(ByteString name, boolean value) {
        super.setBoolean(name, value);
        return this;
    }

    @Override
    public Http2Headers setChar(ByteString name, char value) {
        super.setChar(name, value);
        return this;
    }

    @Override
    public Http2Headers setByte(ByteString name, byte value) {
        super.setByte(name, value);
        return this;
    }

    @Override
    public Http2Headers setShort(ByteString name, short value) {
        super.setShort(name, value);
        return this;
    }

    @Override
    public Http2Headers setInt(ByteString name, int value) {
        super.setInt(name, value);
        return this;
    }

    @Override
    public Http2Headers setLong(ByteString name, long value) {
        super.setLong(name, value);
        return this;
    }

    @Override
    public Http2Headers setFloat(ByteString name, float value) {
        super.setFloat(name, value);
        return this;
    }

    @Override
    public Http2Headers setDouble(ByteString name, double value) {
        super.setDouble(name, value);
        return this;
    }

    @Override
    public Http2Headers setTimeMillis(ByteString name, long value) {
        super.setTimeMillis(name, value);
        return this;
    }

    @Override
    public Http2Headers set(Headers<? extends ByteString> headers) {
        super.set(headers);
        return this;
    }

    @Override
    public Http2Headers setAll(Headers<? extends ByteString> headers) {
        super.setAll(headers);
        return this;
    }

    @Override
    public Http2Headers clear() {
        super.clear();
        return this;
    }

    @Override
    public Http2Headers method(ByteString value) {
        set(PseudoHeaderName.METHOD.value(), value);
        return this;
    }

    @Override
    public Http2Headers scheme(ByteString value) {
        set(PseudoHeaderName.SCHEME.value(), value);
        return this;
    }

    @Override
    public Http2Headers authority(ByteString value) {
        set(PseudoHeaderName.AUTHORITY.value(), value);
        return this;
    }

    @Override
    public Http2Headers path(ByteString value) {
        set(PseudoHeaderName.PATH.value(), value);
        return this;
    }

    @Override
    public Http2Headers status(ByteString value) {
        set(PseudoHeaderName.STATUS.value(), value);
        return this;
    }

    @Override
    public ByteString method() {
        return get(PseudoHeaderName.METHOD.value());
    }

    @Override
    public ByteString scheme() {
        return get(PseudoHeaderName.SCHEME.value());
    }

    @Override
    public ByteString authority() {
        return get(PseudoHeaderName.AUTHORITY.value());
    }

    @Override
    public ByteString path() {
        return get(PseudoHeaderName.PATH.value());
    }

    @Override
    public ByteString status() {
        return get(PseudoHeaderName.STATUS.value());
    }

    @Override
    protected final HeaderEntry<ByteString> newHeaderEntry(int h, ByteString name, ByteString value,
                                                           HeaderEntry<ByteString> next) {
        return new Http2HeaderEntry(h, name, value, next);
    }

    private final class Http2HeaderEntry extends HeaderEntry<ByteString> {
        protected Http2HeaderEntry(int hash, ByteString key, ByteString value, HeaderEntry<ByteString> next) {
            super(hash, key);
            this.value = value;
            this.next = next;

            // Make sure the pseudo headers fields are first in iteration order
            if (!key.isEmpty() && key.byteAt(0) == ':') {
                after = firstNonPseudo;
                before = firstNonPseudo.before();
            } else {
                after = head;
                before = head.before();
                if (firstNonPseudo == head) {
                    firstNonPseudo = this;
                }
            }
            pointNeighborsToThis();
        }

        @Override
        protected void remove() {
            if (this == firstNonPseudo) {
                firstNonPseudo = firstNonPseudo.after();
            }
            super.remove();
        }
    }
}
