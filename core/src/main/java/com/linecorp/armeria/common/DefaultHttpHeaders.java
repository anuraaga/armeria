/*
 * Copyright 2016 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.linecorp.armeria.common;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Objects.requireNonNull;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.base.Ascii;

import io.netty.handler.codec.Headers;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.AsciiString;

/**
 * Default {@link HttpHeaders} implementation.
 */
public final class DefaultHttpHeaders implements HttpHeaders {

    private final Http2Headers delegate;
    private final boolean endOfStream;

    @Nullable
    private HttpMethod method;
    @Nullable
    private HttpStatus status;
    @Nullable
    private MediaType contentType;

    public DefaultHttpHeaders() {
        this(true);
    }

    public DefaultHttpHeaders(boolean validate) {
        this(validate, 16);
    }

    public DefaultHttpHeaders(boolean validate, int initialCapacity) {
        this(new DefaultHttp2Headers(validate, initialCapacity), false);
    }

    public DefaultHttpHeaders(boolean validate, int initialCapacity, boolean endOfStream) {
        this(new DefaultHttp2Headers(validate, initialCapacity), endOfStream);
    }

    public DefaultHttpHeaders(Http2Headers delegate, boolean endOfStream) {
        this.delegate = delegate;
        this.endOfStream = endOfStream;
    }

    @Nullable
    @Override
    public HttpMethod method() {
        final HttpMethod method = this.method;
        if (method != null) {
            return method;
        }

        final String methodStr = get(HttpHeaderNames.METHOD);
        if (methodStr == null) {
            return null;
        }

        return this.method = HttpMethod.isSupported(methodStr) ? HttpMethod.valueOf(methodStr)
                                                               : HttpMethod.UNKNOWN;
    }

    @Override
    public HttpHeaders method(HttpMethod method) {
        requireNonNull(method, "method");
        this.method = method;
        set(HttpHeaderNames.METHOD, method.name());
        return this;
    }

    @Nullable
    @Override
    public String scheme() {
        return get(HttpHeaderNames.SCHEME);
    }

    @Override
    public HttpHeaders scheme(String scheme) {
        requireNonNull(scheme, "scheme");
        set(HttpHeaderNames.SCHEME, scheme);
        return this;
    }

    @Nullable
    @Override
    public String authority() {
        return get(HttpHeaderNames.AUTHORITY);
    }

    @Override
    public HttpHeaders authority(String authority) {
        requireNonNull(authority, "authority");
        set(HttpHeaderNames.AUTHORITY, authority);
        return this;
    }

    @Nullable
    @Override
    public String path() {
        return get(HttpHeaderNames.PATH);
    }

    @Override
    public HttpHeaders path(String path) {
        requireNonNull(path, "path");
        set(HttpHeaderNames.PATH, path);
        return this;
    }

    @Nullable
    @Override
    public HttpStatus status() {
        final HttpStatus status = this.status;
        if (status != null) {
            return status;
        }

        final String statusStr = get(HttpHeaderNames.STATUS);
        if (statusStr == null) {
            return null;
        }

        try {
            return this.status = HttpStatus.valueOf(Integer.valueOf(statusStr));
        } catch (NumberFormatException ignored) {
            throw new IllegalStateException("invalid status: " + statusStr);
        }
    }

    @Override
    public HttpHeaders status(int statusCode) {
        final HttpStatus status = this.status = HttpStatus.valueOf(statusCode);
        set(HttpHeaderNames.STATUS, status.codeAsText());
        return this;
    }

    @Override
    public HttpHeaders status(HttpStatus status) {
        requireNonNull(status, "status");
        set(HttpHeaderNames.STATUS, status.codeAsText());
        return this;
    }

    @Nullable
    @Override
    public MediaType contentType() {
        final String contentTypeString = get(HttpHeaderNames.CONTENT_TYPE);
        if (contentTypeString == null) {
            return null;
        }

        final MediaType contentType = this.contentType;
        if (contentType != null && Ascii.equalsIgnoreCase(contentType.toString(), contentTypeString.trim())) {
            return contentType;
        }

        try {
            this.contentType = MediaType.parse(contentTypeString);
            return this.contentType;
        } catch (IllegalArgumentException unused) {
            // Invalid media type
            return null;
        }
    }

    @Override
    public HttpHeaders contentType(MediaType contentType) {
        requireNonNull(contentType, "contentType");
        this.contentType = contentType;
        return set(HttpHeaderNames.CONTENT_TYPE, contentType.toString());
    }

    @Override
    public boolean isEndOfStream() {
        return endOfStream;
    }

    @Override
    @Nullable
    public String get(AsciiString name) {
        return delegate.get(name).toString();
    }

    @Override
    public String get(AsciiString name, String defaultValue) {
        return delegate.get(name, defaultValue).toString();
    }

    @Override
    @Nullable
    public String getAndRemove(AsciiString name) {
        return delegate.getAndRemove(name).toString();
    }

    @Override
    public String getAndRemove(AsciiString name, String defaultValue) {
        return delegate.getAndRemove(name, defaultValue).toString();
    }

    @Override
    public List<String> getAll(AsciiString name) {
        return delegate.getAll(name).stream().map(CharSequence::toString).collect(toImmutableList());
    }

    @Override
    public List<String> getAllAndRemove(AsciiString name) {
        return delegate.getAllAndRemove(name).stream().map(CharSequence::toString).collect(toImmutableList());
    }

    @Override
    @Nullable
    public Boolean getBoolean(AsciiString name) {
        return delegate.getBoolean(name);
    }

    @Override
    public boolean getBoolean(AsciiString name, boolean defaultValue) {
        return delegate.getBoolean(name, defaultValue);
    }

    @Override
    @Nullable
    public Byte getByte(AsciiString name) {
        return delegate.getByte(name);
    }

    @Override
    public byte getByte(AsciiString name, byte defaultValue) {
        return delegate.getByte(name, defaultValue);
    }

    @Override
    @Nullable
    public Character getChar(AsciiString name) {
        return delegate.getChar(name);
    }

    @Override
    public char getChar(AsciiString name, char defaultValue) {
        return delegate.getChar(name, defaultValue);
    }

    @Override
    @Nullable
    public Short getShort(AsciiString name) {
        return delegate.getShort(name);
    }

    @Override
    public short getShort(AsciiString name, short defaultValue) {
        return delegate.getShort(name, defaultValue);
    }

    @Override
    @Nullable
    public Integer getInt(AsciiString name) {
        return delegate.getInt(name);
    }

    @Override
    public int getInt(AsciiString name, int defaultValue) {
        return delegate.getInt(name, defaultValue);
    }

    @Override
    @Nullable
    public Long getLong(AsciiString name) {
        return delegate.getLong(name);
    }

    @Override
    public long getLong(AsciiString name, long defaultValue) {
        return delegate.getLong(name, defaultValue);
    }

    @Override
    @Nullable
    public Float getFloat(AsciiString name) {
        return delegate.getFloat(name);
    }

    @Override
    public float getFloat(AsciiString name, float defaultValue) {
        return delegate.getFloat(name, defaultValue);
    }

    @Override
    @Nullable
    public Double getDouble(AsciiString name) {
        return delegate.getDouble(name);
    }

    @Override
    public double getDouble(AsciiString name, double defaultValue) {
        return delegate.getDouble(name, defaultValue);
    }

    @Override
    @Nullable
    public Long getTimeMillis(AsciiString name) {
        return delegate.getTimeMillis(name);
    }

    @Override
    public long getTimeMillis(AsciiString name, long defaultValue) {
        return delegate.getTimeMillis(name, defaultValue);
    }

    @Override
    @Nullable
    public Boolean getBooleanAndRemove(AsciiString name) {
        return delegate.getBooleanAndRemove(name);
    }

    @Override
    public boolean getBooleanAndRemove(AsciiString name, boolean defaultValue) {
        return delegate.getBooleanAndRemove(name, defaultValue);
    }

    @Override
    @Nullable
    public Byte getByteAndRemove(AsciiString name) {
        return delegate.getByteAndRemove(name);
    }

    @Override
    public byte getByteAndRemove(AsciiString name, byte defaultValue) {
        return delegate.getByteAndRemove(name, defaultValue);
    }

    @Override
    @Nullable
    public Character getCharAndRemove(AsciiString name) {
        return delegate.getCharAndRemove(name);
    }

    @Override
    public char getCharAndRemove(AsciiString name, char defaultValue) {
        return delegate.getCharAndRemove(name, defaultValue);
    }

    @Override
    @Nullable
    public Short getShortAndRemove(AsciiString name) {
        return delegate.getShortAndRemove(name);
    }

    @Override
    public short getShortAndRemove(AsciiString name, short defaultValue) {
        return delegate.getShortAndRemove(name, defaultValue);
    }

    @Override
    @Nullable
    public Integer getIntAndRemove(AsciiString name) {
        return delegate.getIntAndRemove(name);
    }

    @Override
    public int getIntAndRemove(AsciiString name, int defaultValue) {
        return delegate.getIntAndRemove(name, defaultValue);
    }

    @Override
    @Nullable
    public Long getLongAndRemove(AsciiString name) {
        return delegate.getLongAndRemove(name);
    }

    @Override
    public long getLongAndRemove(AsciiString name, long defaultValue) {
        return delegate.getLongAndRemove(name, defaultValue);
    }

    @Override
    @Nullable
    public Float getFloatAndRemove(AsciiString name) {
        return delegate.getFloatAndRemove(name);
    }

    @Override
    public float getFloatAndRemove(AsciiString name, float defaultValue) {
        return delegate.getFloatAndRemove(name, defaultValue);
    }

    @Override
    @Nullable
    public Double getDoubleAndRemove(AsciiString name) {
        return delegate.getDoubleAndRemove(name);
    }

    @Override
    public double getDoubleAndRemove(AsciiString name, double defaultValue) {
        return delegate.getDoubleAndRemove(name, defaultValue);
    }

    @Override
    @Nullable
    public Long getTimeMillisAndRemove(AsciiString name) {
        return delegate.getTimeMillisAndRemove(name);
    }

    @Override
    public long getTimeMillisAndRemove(AsciiString name, long defaultValue) {
        return delegate.getTimeMillisAndRemove(name, defaultValue);
    }

    @Override
    public boolean contains(AsciiString name) {
        return delegate.contains(name);
    }

    @Override
    public boolean contains(AsciiString name, String value) {
        return delegate.contains(name, value);
    }

    @Override
    public boolean containsObject(AsciiString name, Object value) {
        return delegate.containsObject(name, value);
    }

    @Override
    public boolean containsBoolean(AsciiString name, boolean value) {
        return delegate.containsBoolean(name, value);
    }

    @Override
    public boolean containsByte(AsciiString name, byte value) {
        return delegate.containsByte(name, value);
    }

    @Override
    public boolean containsChar(AsciiString name, char value) {
        return delegate.containsChar(name, value);
    }

    @Override
    public boolean containsShort(AsciiString name, short value) {
        return delegate.containsShort(name, value);
    }

    @Override
    public boolean containsInt(AsciiString name, int value) {
        return delegate.containsInt(name, value);
    }

    @Override
    public boolean containsLong(AsciiString name, long value) {
        return delegate.containsLong(name, value);
    }

    @Override
    public boolean containsFloat(AsciiString name, float value) {
        return delegate.containsFloat(name, value);
    }

    @Override
    public boolean containsDouble(AsciiString name, double value) {
        return delegate.containsDouble(name, value);
    }

    @Override
    public boolean containsTimeMillis(AsciiString name, long value) {
        return delegate.containsTimeMillis(name, value);
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public Set<AsciiString> names() {
        return delegate.names().stream().map(AsciiString::of).collect(toImmutableSet());
    }

    @Override
    public HttpHeaders add(AsciiString name, String value) {
        delegate.add(name, value);
        return this;
    }

    @Override
    public HttpHeaders add(AsciiString name, Iterable<? extends String> values) {
        delegate.add(name, values);
        return this;
    }

    @Override
    public HttpHeaders add(AsciiString name, String... values) {
        delegate.add(name, values);
        return this;
    }

    @Override
    public HttpHeaders addObject(AsciiString name, Object value) {
        delegate.addObject(name, value);
        return this;
    }

    @Override
    public HttpHeaders addObject(AsciiString name, Iterable<?> values) {
        delegate.addObject(name, values);
        return this;
    }

    @Override
    public HttpHeaders addObject(AsciiString name, Object... values) {
        delegate.addObject(name, values);
        return this;
    }

    @Override
    public HttpHeaders addBoolean(AsciiString name, boolean value) {
        delegate.addBoolean(name, value);
        return this;
    }

    @Override
    public HttpHeaders addByte(AsciiString name, byte value) {
        delegate.addByte(name, value);
        return this;
    }

    @Override
    public HttpHeaders addChar(AsciiString name, char value) {
        delegate.addChar(name, value);
        return this;
    }

    @Override
    public HttpHeaders addShort(AsciiString name, short value) {
        delegate.addShort(name, value);
        return this;
    }

    @Override
    public HttpHeaders addInt(AsciiString name, int value) {
        delegate.addInt(name, value);
        return this;
    }

    @Override
    public HttpHeaders addLong(AsciiString name, long value) {
        delegate.addLong(name, value);
        return this;
    }

    @Override
    public HttpHeaders addFloat(AsciiString name, float value) {
        delegate.addFloat(name, value);
        return this;
    }

    @Override
    public HttpHeaders addDouble(AsciiString name, double value) {
        delegate.addDouble(name, value);
        return this;
    }

    @Override
    public HttpHeaders addTimeMillis(AsciiString name, long value) {
        delegate.addTimeMillis(name, value);
        return this;
    }

    @Override
    public HttpHeaders add(Headers<? extends AsciiString, ? extends String, ?> headers) {
        delegate.add(headers);
        return this;
    }

    @Override
    public HttpHeaders set(AsciiString name, String value) {
        delegate.set(name, value);
        return this;
    }

    @Override
    public HttpHeaders set(AsciiString name, Iterable<? extends String> values) {
        delegate.set(name, values);
        return this;
    }

    @Override
    public HttpHeaders set(AsciiString name, String... values) {
        delegate.set(name, values);
        return this;
    }

    @Override
    public HttpHeaders setObject(AsciiString name, Object value) {
        delegate.setObject(name, value);
        return this;
    }

    @Override
    public HttpHeaders setObject(AsciiString name, Iterable<?> values) {
        delegate.setObject(name, values);
        return this;
    }

    @Override
    public HttpHeaders setObject(AsciiString name, Object... values) {
        delegate.setObject(name, values);
        return this;
    }

    @Override
    public HttpHeaders setBoolean(AsciiString name, boolean value) {
        delegate.setBoolean(name, value);
        return this;
    }

    @Override
    public HttpHeaders setByte(AsciiString name, byte value) {
        delegate.setByte(name, value);
        return this;
    }

    @Override
    public HttpHeaders setChar(AsciiString name, char value) {
        delegate.setChar(name, value);
        return this;
    }

    @Override
    public HttpHeaders setShort(AsciiString name, short value) {
        delegate.setShort(name, value);
        return this;
    }

    @Override
    public HttpHeaders setInt(AsciiString name, int value) {
        delegate.setInt(name, value);
        return this;
    }

    @Override
    public HttpHeaders setLong(AsciiString name, long value) {
        delegate.setLong(name, value);
        return this;
    }

    @Override
    public HttpHeaders setFloat(AsciiString name, float value) {
        delegate.setFloat(name, value);
        return this;
    }

    @Override
    public HttpHeaders setDouble(AsciiString name, double value) {
        delegate.setDouble(name, value);
        return this;
    }

    @Override
    public HttpHeaders setTimeMillis(AsciiString name, long value) {
        delegate.setTimeMillis(name, value);
        return this;
    }

    @Override
    public HttpHeaders set(Headers<? extends AsciiString, ? extends String, ?> headers) {
        delegate.set(headers);
        return this;
    }

    @Override
    public HttpHeaders setAll(Headers<? extends AsciiString, ? extends String, ?> headers) {
        delegate.setAll(headers);
        return this;
    }

    @Override
    public boolean remove(AsciiString name) {
        return delegate.remove(name);
    }

    @Override
    public HttpHeaders clear() {
        delegate.clear();
        return this;
    }

    @Override
    public Iterator<Entry<AsciiString, String>> iterator() {
        return new HeaderIterator(delegate.iterator());
    }

    public Http2Headers toNetty() {
        return delegate;
    }

    private static final class HeaderIterator implements Iterator<Entry<AsciiString, String>> {

        private final Iterator<Entry<CharSequence, CharSequence>> delegate;

        private HeaderIterator(Iterator<Entry<CharSequence, CharSequence>> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public Entry<AsciiString, String> next() {
            final Entry<CharSequence, CharSequence> next = delegate.next();
            return new SimpleImmutableEntry<>(AsciiString.of(next.getKey()), next.getValue().toString());
        }
    }
}
