/*
 * Copyright 2018 LINE Corporation
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
import static com.linecorp.armeria.internal.ArmeriaHttpUtil.convertHeaderValue;
import static java.util.Objects.requireNonNull;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.base.Ascii;
import com.google.common.collect.Iterators;

import io.netty.handler.codec.Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.AsciiString;

/**
 * {@link HttpHeaders} backed by a {@link Http2Headers}.
 */
public class WrappedHttp2Headers implements HttpHeaders {

    private final Http2Headers wrapped;
    private final boolean endOfStream;

    @Nullable
    private HttpMethod method;
    @Nullable
    private HttpStatus status;
    @Nullable
    private MediaType contentType;

    /**
     * Constructs a {@link WrappedHttp2Headers}.
     */
    public WrappedHttp2Headers(Http2Headers wrapped, boolean endOfStream) {
        this.wrapped = wrapped;
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
    public String get(AsciiString name) {
        return convertHeaderValue(name, wrapped.get(name));
    }

    @Override
    public String get(AsciiString name, String defaultValue) {
        return convertHeaderValue(name, wrapped.get(name, defaultValue));
    }

    @Override
    public String getAndRemove(AsciiString name) {
        return convertHeaderValue(name, wrapped.getAndRemove(name));
    }

    @Override
    public String getAndRemove(AsciiString name, String defaultValue) {
        return convertHeaderValue(name, wrapped.getAndRemove(name, defaultValue));
    }

    @Override
    public List<String> getAll(AsciiString name) {
        return wrapped.getAll(name).stream().map(v -> convertHeaderValue(name, v)).collect(toImmutableList());
    }

    @Override
    public List<String> getAllAndRemove(AsciiString name) {
        return wrapped.getAllAndRemove(name).stream().map(v -> convertHeaderValue(name, v))
                      .collect(toImmutableList());
    }

    @Override
    public Boolean getBoolean(AsciiString name) {
        return wrapped.getBoolean(name);
    }

    @Override
    public boolean getBoolean(AsciiString name, boolean defaultValue) {
        return wrapped.getBoolean(name, defaultValue);
    }

    @Override
    public Byte getByte(AsciiString name) {
        return wrapped.getByte(name);
    }

    @Override
    public byte getByte(AsciiString name, byte defaultValue) {
        return wrapped.getByte(name, defaultValue);
    }

    @Override
    public Character getChar(AsciiString name) {
        return wrapped.getChar(name);
    }

    @Override
    public char getChar(AsciiString name, char defaultValue) {
        return wrapped.getChar(name, defaultValue);
    }

    @Override
    public Short getShort(AsciiString name) {
        return wrapped.getShort(name);
    }

    @Override
    public short getShort(AsciiString name, short defaultValue) {
        return wrapped.getShort(name, defaultValue);
    }

    @Override
    public Integer getInt(AsciiString name) {
        return wrapped.getInt(name);
    }

    @Override
    public int getInt(AsciiString name, int defaultValue) {
        return wrapped.getInt(name, defaultValue);
    }

    @Override
    public Long getLong(AsciiString name) {
        return wrapped.getLong(name);
    }

    @Override
    public long getLong(AsciiString name, long defaultValue) {
        return wrapped.getLong(name, defaultValue);
    }

    @Override
    public Float getFloat(AsciiString name) {
        return wrapped.getFloat(name);
    }

    @Override
    public float getFloat(AsciiString name, float defaultValue) {
        return wrapped.getFloat(name, defaultValue);
    }

    @Override
    public Double getDouble(AsciiString name) {
        return wrapped.getDouble(name);
    }

    @Override
    public double getDouble(AsciiString name, double defaultValue) {
        return wrapped.getDouble(name, defaultValue);
    }

    @Override
    public Long getTimeMillis(AsciiString name) {
        return wrapped.getTimeMillis(name);
    }

    @Override
    public long getTimeMillis(AsciiString name, long defaultValue) {
        return wrapped.getTimeMillis(name, defaultValue);
    }

    @Override
    public Boolean getBooleanAndRemove(AsciiString name) {
        return wrapped.getBooleanAndRemove(name);
    }

    @Override
    public boolean getBooleanAndRemove(AsciiString name, boolean defaultValue) {
        return wrapped.getBooleanAndRemove(name, defaultValue);
    }

    @Override
    public Byte getByteAndRemove(AsciiString name) {
        return wrapped.getByteAndRemove(name);
    }

    @Override
    public byte getByteAndRemove(AsciiString name, byte defaultValue) {
        return wrapped.getByteAndRemove(name, defaultValue);
    }

    @Override
    public Character getCharAndRemove(AsciiString name) {
        return wrapped.getCharAndRemove(name);
    }

    @Override
    public char getCharAndRemove(AsciiString name, char defaultValue) {
        return wrapped.getCharAndRemove(name, defaultValue);
    }

    @Override
    public Short getShortAndRemove(AsciiString name) {
        return wrapped.getShortAndRemove(name);
    }

    @Override
    public short getShortAndRemove(AsciiString name, short defaultValue) {
        return wrapped.getShortAndRemove(name, defaultValue);
    }

    @Override
    public Integer getIntAndRemove(AsciiString name) {
        return wrapped.getIntAndRemove(name);
    }

    @Override
    public int getIntAndRemove(AsciiString name, int defaultValue) {
        return wrapped.getIntAndRemove(name, defaultValue);
    }

    @Override
    public Long getLongAndRemove(AsciiString name) {
        return wrapped.getLongAndRemove(name);
    }

    @Override
    public long getLongAndRemove(AsciiString name, long defaultValue) {
        return wrapped.getLongAndRemove(name, defaultValue);
    }

    @Override
    public Float getFloatAndRemove(AsciiString name) {
        return wrapped.getFloatAndRemove(name);
    }

    @Override
    public float getFloatAndRemove(AsciiString name, float defaultValue) {
        return wrapped.getFloatAndRemove(name, defaultValue);
    }

    @Override
    public Double getDoubleAndRemove(AsciiString name) {
        return wrapped.getDoubleAndRemove(name);
    }

    @Override
    public double getDoubleAndRemove(AsciiString name, double defaultValue) {
        return wrapped.getDoubleAndRemove(name, defaultValue);
    }

    @Override
    public Long getTimeMillisAndRemove(AsciiString name) {
        return wrapped.getTimeMillisAndRemove(name);
    }

    @Override
    public long getTimeMillisAndRemove(AsciiString name, long defaultValue) {
        return wrapped.getTimeMillisAndRemove(name, defaultValue);
    }

    @Override
    public boolean contains(AsciiString name) {
        return wrapped.contains(name);
    }

    @Override
    public boolean contains(AsciiString name, String value) {
        return wrapped.contains(name, value);
    }

    @Override
    public boolean containsObject(AsciiString name, Object value) {
        return wrapped.containsObject(name, value);
    }

    @Override
    public boolean containsBoolean(AsciiString name, boolean value) {
        return wrapped.containsBoolean(name, value);
    }

    @Override
    public boolean containsByte(AsciiString name, byte value) {
        return wrapped.containsByte(name, value);
    }

    @Override
    public boolean containsChar(AsciiString name, char value) {
        return wrapped.containsChar(name, value);
    }

    @Override
    public boolean containsShort(AsciiString name, short value) {
        return wrapped.containsShort(name, value);
    }

    @Override
    public boolean containsInt(AsciiString name, int value) {
        return wrapped.containsInt(name, value);
    }

    @Override
    public boolean containsLong(AsciiString name, long value) {
        return wrapped.containsLong(name, value);
    }

    @Override
    public boolean containsFloat(AsciiString name, float value) {
        return wrapped.containsFloat(name, value);
    }

    @Override
    public boolean containsDouble(AsciiString name, double value) {
        return wrapped.containsDouble(name, value);
    }

    @Override
    public boolean containsTimeMillis(AsciiString name, long value) {
        return wrapped.containsTimeMillis(name, value);
    }

    @Override
    public int size() {
        return wrapped.size();
    }

    @Override
    public boolean isEmpty() {
        return wrapped.isEmpty();
    }

    @Override
    public Set<AsciiString> names() {
        return wrapped.names().stream().map(AsciiString::of).collect(toImmutableSet());
    }

    @Override
    public HttpHeaders add(AsciiString name, String value) {
        wrapped.add(name, value);
        return this;
    }

    @Override
    public HttpHeaders add(AsciiString name, Iterable<? extends String> values) {
        wrapped.add(name, values);
        return this;
    }

    @Override
    public HttpHeaders add(AsciiString name, String... values) {
        wrapped.add(name, values);
        return this;
    }

    @Override
    public HttpHeaders add(Headers<? extends AsciiString, ? extends String, ?> headers) {
        wrapped.add(headers);
        return this;
    }

    @Override
    public HttpHeaders addObject(AsciiString name, Object value) {
        wrapped.addObject(name, value);
        return this;
    }

    @Override
    public HttpHeaders addObject(AsciiString name, Iterable<?> values) {
        wrapped.addObject(name, values);
        return this;
    }

    @Override
    public HttpHeaders addObject(AsciiString name, Object... values) {
        wrapped.addObject(name, values);
        return this;
    }

    @Override
    public HttpHeaders addBoolean(AsciiString name, boolean value) {
        wrapped.addBoolean(name, value);
        return this;
    }

    @Override
    public HttpHeaders addByte(AsciiString name, byte value) {
        wrapped.addByte(name, value);
        return this;
    }

    @Override
    public HttpHeaders addChar(AsciiString name, char value) {
        wrapped.addChar(name, value);
        return this;
    }

    @Override
    public HttpHeaders addShort(AsciiString name, short value) {
        wrapped.addShort(name, value);
        return this;
    }

    @Override
    public HttpHeaders addInt(AsciiString name, int value) {
        wrapped.addInt(name, value);
        return this;
    }

    @Override
    public HttpHeaders addLong(AsciiString name, long value) {
        wrapped.addLong(name, value);
        return this;
    }

    @Override
    public HttpHeaders addFloat(AsciiString name, float value) {
        wrapped.addFloat(name, value);
        return this;
    }

    @Override
    public HttpHeaders addDouble(AsciiString name, double value) {
        wrapped.addDouble(name, value);
        return this;
    }

    @Override
    public HttpHeaders addTimeMillis(AsciiString name, long value) {
        wrapped.addTimeMillis(name, value);
        return this;
    }

    @Override
    public HttpHeaders set(AsciiString name, String value) {
        wrapped.set(name, value);
        return this;
    }

    @Override
    public HttpHeaders set(AsciiString name, Iterable<? extends String> values) {
        wrapped.set(name, values);
        return this;
    }

    @Override
    public HttpHeaders set(AsciiString name, String... values) {
        wrapped.set(name, values);
        return this;
    }

    @Override
    public HttpHeaders set(Headers<? extends AsciiString, ? extends String, ?> headers) {
        wrapped.set(headers);
        return this;
    }

    @Override
    public HttpHeaders setObject(AsciiString name, Object value) {
        wrapped.setObject(name, value);
        return this;
    }

    @Override
    public HttpHeaders setObject(AsciiString name, Iterable<?> values) {
        wrapped.setObject(name, values);
        return this;
    }

    @Override
    public HttpHeaders setObject(AsciiString name, Object... values) {
        wrapped.setObject(name, values);
        return this;
    }

    @Override
    public HttpHeaders setBoolean(AsciiString name, boolean value) {
        wrapped.setBoolean(name, value);
        return this;
    }

    @Override
    public HttpHeaders setByte(AsciiString name, byte value) {
        wrapped.setByte(name, value);
        return this;
    }

    @Override
    public HttpHeaders setChar(AsciiString name, char value) {
        wrapped.setChar(name, value);
        return this;
    }

    @Override
    public HttpHeaders setShort(AsciiString name, short value) {
        wrapped.setShort(name, value);
        return this;
    }

    @Override
    public HttpHeaders setInt(AsciiString name, int value) {
        wrapped.setInt(name, value);
        return this;
    }

    @Override
    public HttpHeaders setLong(AsciiString name, long value) {
        wrapped.setLong(name, value);
        return this;
    }

    @Override
    public HttpHeaders setFloat(AsciiString name, float value) {
        wrapped.setFloat(name, value);
        return this;
    }

    @Override
    public HttpHeaders setDouble(AsciiString name, double value) {
        wrapped.setDouble(name, value);
        return this;
    }

    @Override
    public HttpHeaders setTimeMillis(AsciiString name, long value) {
        wrapped.setTimeMillis(name, value);
        return this;
    }

    @Override
    public HttpHeaders setAll(Headers<? extends AsciiString, ? extends String, ?> headers) {
        wrapped.setAll(headers);
        return this;
    }

    @Override
    public boolean remove(AsciiString name) {
        return wrapped.remove(name);
    }

    @Override
    public HttpHeaders clear() {
        wrapped.clear();
        return this;
    }

    @Override
    public Iterator<Entry<AsciiString, String>> iterator() {
        return Iterators.transform(
                wrapped.iterator(),
                e -> new SimpleImmutableEntry<>(AsciiString.of(e.getKey()), e.getValue().toString()));
    }
}
