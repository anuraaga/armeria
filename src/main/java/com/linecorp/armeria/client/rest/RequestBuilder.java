/*
 * Copyright (C) 2012 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.linecorp.armeria.client.rest;

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringEncoder;

final class RequestBuilder {

    private final String method;
    private final boolean hasBody;
    private String relativeUrl;
    private QueryStringEncoder urlBuilder;
    private QueryStringEncoder formBuilder;
    private HttpHeaders headers;
    private ByteBuf body;

    RequestBuilder(String method, String relativeUrl, HttpHeaders headers, boolean hasBody,
                   boolean isFormEncoded) {
        this.method = method;
        this.relativeUrl = relativeUrl;
        this.headers = EmptyHttpHeaders.INSTANCE;
        this.hasBody = hasBody;

        if (headers != null) {
            headers().add(headers);
        }

        if (isFormEncoded) {
            // Will be set to 'body' in 'build'.
            formBuilder = new QueryStringEncoder("/");
        }
    }

    void setRelativeUrl(String relativeUrl) {
        this.relativeUrl = relativeUrl;
    }

    void addHeader(CharSequence name, String value) {
        headers().add(name, value);
    }

    void addPathParam(String name, String value) {
        if (relativeUrl == null) {
            // The relative URL is cleared when the first query parameter is set.
            throw new AssertionError();
        }
        relativeUrl = relativeUrl.replace("{" + name + "}", value);
    }

    void addQueryParam(String name, String value) {
        if (relativeUrl != null) {
            // Do a one-time combination of the built relative URL and the base URL.
            urlBuilder = new QueryStringEncoder(relativeUrl);
            relativeUrl = null;
        }

        urlBuilder.addParam(name, value);
    }

    void addFormField(String name, String value) {
        formBuilder.addParam(name, value);
    }

    void setBody(ByteBuf body) {
        this.body = body;
    }

    FullHttpRequest build(ByteBufAllocator alloc) {
        ByteBuf body = this.body;
        if (body == null) {
            // Try to pull from one of the builders.
            if (formBuilder != null) {
                try {
                    body = alloc.ioBuffer().writeBytes(formBuilder.toUri().getRawQuery().getBytes(
                            // Url-encoded strings are always ascii.
                            StandardCharsets.US_ASCII));
                } catch (URISyntaxException e) {
                    throw new IllegalArgumentException("Could not encode form parameters.", e);
                }
            } else if (hasBody) {
                // Body is absent, make an empty body.
                body = alloc.ioBuffer();
            }
        }

        String url;
        if (urlBuilder != null) {
            url = urlBuilder.toString();
        } else if (relativeUrl != null) {
            url = relativeUrl;
        } else {
            throw new IllegalStateException("Neither urlBuilder nor relativeUrl set, this can't happen");
        }
        DefaultFullHttpRequest request;
        if (body != null) {
            request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(method), url, body);
        } else {
            request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf(method), url);
        }
        request.headers().set(headers);
        return request;
    }

    private HttpHeaders headers() {
        if (headers == EmptyHttpHeaders.INSTANCE) {
            headers = new DefaultHttpHeaders();
        }
        return headers;
    }
}
