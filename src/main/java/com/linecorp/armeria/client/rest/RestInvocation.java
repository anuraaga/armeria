/*
 * Copyright 2015 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
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

package com.linecorp.armeria.client.rest;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.linecorp.armeria.client.ClientCodec.EncodeResult;
import com.linecorp.armeria.client.http.SimpleHttpClient;
import com.linecorp.armeria.common.Scheme;
import com.linecorp.armeria.common.ServiceInvocationContext;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpRequest;

/**
 * A container for the parameters of this client invocation.
 */
class RestInvocation extends ServiceInvocationContext implements EncodeResult {

    private static final AtomicInteger nextInvocationId = new AtomicInteger();

    private final int invocationId = nextInvocationId.incrementAndGet();
    private String invocationIdStr;


    private final FullHttpRequest content;
    private final List<Class<?>> paramTypes;
    private final Class<?> returnType;
    private final List<Object> params;

    RestInvocation(Channel ch, Scheme scheme,
                   String host, String path,
                   FullHttpRequest content,
                   Method method,
                   Object[] args) {
        super(ch, scheme, host, path, path, SimpleHttpClient.class.getName(), null);
        this.content = content;
        paramTypes = Arrays.asList(method.getParameterTypes());
        returnType = method.getReturnType();
        params = Arrays.asList(args);
    }

    @Override
    public String invocationId() {
        String invocationIdStr = this.invocationIdStr;
        if (invocationIdStr == null) {
            this.invocationIdStr = invocationIdStr = Long.toString(invocationId & 0xFFFFFFFFL, 16);
        }

        return invocationIdStr;
    }

    @Override
    public String method() {
        return path();
    }

    @Override
    public List<Class<?>> paramTypes() {
        return paramTypes;
    }

    @Override
    public Class<?> returnType() {
        return returnType;
    }

    @Override
    public List<Object> params() {
        return params;
    }

    @Override
    public boolean isSuccess() {
        return true;
    }

    @Override
    public ServiceInvocationContext invocationContext() {
        return this;
    }

    @Override
    public FullHttpRequest content() {
        return content;
    }

    @Override
    public Throwable cause() {
        return new IllegalStateException("A successful result does not have a cause.");
    }

    @Override
    public Optional<String> encodedHost() {
        return Optional.of(host());
    }

    @Override
    public Optional<String> encodedPath() {
        return Optional.of(path());
    }

    @Override
    public Optional<Scheme> encodedScheme() {
        return Optional.of(scheme());
    }
}
