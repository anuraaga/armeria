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
package com.linecorp.armeria.client;

import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Supplier;

import com.linecorp.armeria.client.endpoint.EndpointGroup;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpHeaders;
import com.linecorp.armeria.common.HttpHeadersBuilder;
import com.linecorp.armeria.common.RequestId;

class AbstractClientOptionsBuilder {

    private final Map<ClientOption<?>, ClientOptionValue<?>> options = new LinkedHashMap<>();
    private final ClientDecorationBuilder decoration = ClientDecoration.builder();
    private final HttpHeadersBuilder httpHeaders = HttpHeaders.builder();

    /**
     * Creates a new instance with the default options.
     */
    AbstractClientOptionsBuilder() {}

    /**
     * Creates a new instance with the specified base options.
     */
    AbstractClientOptionsBuilder(ClientOptions options) {
        requireNonNull(options, "options");
        options(options);
    }

    /**
     * Adds the specified {@link ClientOptions}.
     */
    public AbstractClientOptionsBuilder options(ClientOptions options) {
        requireNonNull(options, "options");
        options.asMap().values().forEach(this::option);
        return this;
    }

    /**
     * Adds the specified {@link ClientOptionValue}s.
     */
    public AbstractClientOptionsBuilder options(ClientOptionValue<?>... options) {
        requireNonNull(options, "options");
        for (ClientOptionValue<?> o : options) {
            option(o);
        }
        return this;
    }

    /**
     * Adds the specified {@link ClientOptionValue}s.
     */
    public AbstractClientOptionsBuilder options(Iterable<ClientOptionValue<?>> options) {
        requireNonNull(options, "options");
        for (ClientOptionValue<?> o : options) {
            option(o);
        }
        return this;
    }

    /**
     * Adds the specified {@link ClientOption} and its {@code value}.
     */
    public <T> AbstractClientOptionsBuilder option(ClientOption<T> option, T value) {
        requireNonNull(option, "option");
        requireNonNull(value, "value");
        return option(option.newValue(value));
    }

    /**
     * Adds the specified {@link ClientOptionValue}.
     */
    public <T> AbstractClientOptionsBuilder option(ClientOptionValue<T> optionValue) {
        requireNonNull(optionValue, "optionValue");
        final ClientOption<?> opt = optionValue.option();
        if (opt == ClientOption.DECORATION) {
            final ClientDecoration d = (ClientDecoration) optionValue.value();
            d.decorators().forEach(decoration::add);
            d.rpcDecorators().forEach(decoration::addRpc);
        } else if (opt == ClientOption.HTTP_HEADERS) {
            final HttpHeaders h = (HttpHeaders) optionValue.value();
            setHttpHeaders(h);
        } else {
            options.put(opt, optionValue);
        }
        return this;
    }

    /**
     * Sets the {@link ClientFactory} used for creating a client.
     * The default is {@link ClientFactory#ofDefault()}.
     */
    public AbstractClientOptionsBuilder factory(ClientFactory factory) {
        return option(ClientOption.FACTORY, requireNonNull(factory, "factory"));
    }

    /**
     * Sets the timeout of a socket write attempt.
     *
     * @deprecated Use {@link #writeTimeout(Duration)}.
     *
     * @param writeTimeout the timeout. {@code 0} disables the timeout.
     */
    @Deprecated
    public AbstractClientOptionsBuilder defaultWriteTimeout(Duration writeTimeout) {
        return writeTimeoutMillis(requireNonNull(writeTimeout, "writeTimeout").toMillis());
    }

    /**
     * Sets the timeout of a socket write attempt in milliseconds.
     *
     * @deprecated Use {@link #writeTimeoutMillis(long)}.
     *
     * @param writeTimeoutMillis the timeout in milliseconds. {@code 0} disables the timeout.
     */
    @Deprecated
    public AbstractClientOptionsBuilder defaultWriteTimeoutMillis(long writeTimeoutMillis) {
        return writeTimeoutMillis(writeTimeoutMillis);
    }

    /**
     * Sets the timeout of a socket write attempt.
     *
     * @param writeTimeout the timeout. {@code 0} disables the timeout.
     */
    public AbstractClientOptionsBuilder writeTimeout(Duration writeTimeout) {
        return writeTimeoutMillis(requireNonNull(writeTimeout, "writeTimeout").toMillis());
    }

    /**
     * Sets the timeout of a socket write attempt in milliseconds.
     *
     * @param writeTimeoutMillis the timeout in milliseconds. {@code 0} disables the timeout.
     */
    public AbstractClientOptionsBuilder writeTimeoutMillis(long writeTimeoutMillis) {
        return option(ClientOption.WRITE_TIMEOUT_MILLIS, writeTimeoutMillis);
    }

    /**
     * Sets the timeout of a response.
     *
     * @deprecated Use {@link #responseTimeout(Duration)}.
     *
     * @param responseTimeout the timeout. {@code 0} disables the timeout.
     */
    @Deprecated
    public AbstractClientOptionsBuilder defaultResponseTimeout(Duration responseTimeout) {
        return responseTimeoutMillis(requireNonNull(responseTimeout, "responseTimeout").toMillis());
    }

    /**
     * Sets the timeout of a response in milliseconds.
     *
     * @deprecated Use {@link #responseTimeoutMillis(long)}.
     *
     * @param responseTimeoutMillis the timeout in milliseconds. {@code 0} disables the timeout.
     */
    @Deprecated
    public AbstractClientOptionsBuilder defaultResponseTimeoutMillis(long responseTimeoutMillis) {
        return responseTimeoutMillis(responseTimeoutMillis);
    }

    /**
     * Sets the timeout of a response.
     *
     * @param responseTimeout the timeout. {@code 0} disables the timeout.
     */
    public AbstractClientOptionsBuilder responseTimeout(Duration responseTimeout) {
        return responseTimeoutMillis(requireNonNull(responseTimeout, "responseTimeout").toMillis());
    }

    /**
     * Sets the timeout of a response in milliseconds.
     *
     * @param responseTimeoutMillis the timeout in milliseconds. {@code 0} disables the timeout.
     */
    public AbstractClientOptionsBuilder responseTimeoutMillis(long responseTimeoutMillis) {
        return option(ClientOption.RESPONSE_TIMEOUT_MILLIS, responseTimeoutMillis);
    }

    /**
     * Sets the maximum allowed length of a server response in bytes.
     *
     * @deprecated Use {@link #maxResponseLength(long)}.
     *
     * @param maxResponseLength the maximum length in bytes. {@code 0} disables the limit.
     */
    @Deprecated
    public AbstractClientOptionsBuilder defaultMaxResponseLength(long maxResponseLength) {
        return maxResponseLength(maxResponseLength);
    }

    /**
     * Sets the maximum allowed length of a server response in bytes.
     *
     * @param maxResponseLength the maximum length in bytes. {@code 0} disables the limit.
     */
    public AbstractClientOptionsBuilder maxResponseLength(long maxResponseLength) {
        return option(ClientOption.MAX_RESPONSE_LENGTH, maxResponseLength);
    }

    /**
     * Sets the {@link Supplier} that generates a {@link RequestId}.
     */
    public AbstractClientOptionsBuilder requestIdGenerator(Supplier<RequestId> requestIdGenerator) {
       return option(ClientOption.REQUEST_ID_GENERATOR, requestIdGenerator);
    }

    /**
     * Sets a {@link Function} that remaps an {@link Endpoint} into an {@link EndpointGroup}.
     * This {@link ClientOption} is useful when you need to override a single target host into
     * a group of hosts to enable client-side load-balancing, e.g.
     * <pre>{@code
     * MyService.Iface client =
     *     Clients.newClient("tbinary+http://example.com/api",
     *                       MyService.Iface.class);
     *
     * EndpointGroup myGroup = EndpointGroup.of(Endpoint.of("node-1.example.com")),
     *                                          Endpoint.of("node-2.example.com")));
     *
     * MyService.Iface derivedClient =
     *     Clients.newDerivedClient(client, options -> {
     *         return options.toBuilder()
     *                       .endpointRemapper(endpoint -> {
     *                           if (endpoint.host().equals("example.com")) {
     *                               return myGroup;
     *                           } else {
     *                               return endpoint;
     *                           }
     *                       })
     *                       .build();
     *     });
     *
     * // This request goes to 'node-1.example.com' or 'node-2.example.com'.
     * derivedClient.call();
     * }</pre>
     *
     * <p>Note that the remapping does not occur recursively but only once.</p>
     *
     * @see ClientOption#ENDPOINT_REMAPPER
     * @see ClientOptions#endpointRemapper()
     */
    public AbstractClientOptionsBuilder endpointRemapper(
            Function<? super Endpoint, ? extends EndpointGroup> endpointRemapper) {
        requireNonNull(endpointRemapper, "endpointRemapper");
        return option(ClientOption.ENDPOINT_REMAPPER, endpointRemapper);
    }

    /**
     * Adds the specified HTTP-level {@code decorator}.
     *
     * @param decorator the {@link Function} that transforms an {@link HttpClient} to another
     */
    public AbstractClientOptionsBuilder decorator(
            Function<? super HttpClient, ? extends HttpClient> decorator) {
        decoration.add(decorator);
        return this;
    }

    /**
     * Adds the specified HTTP-level {@code decorator}.
     *
     * @param decorator the {@link DecoratingHttpClientFunction} that intercepts an invocation
     */
    public AbstractClientOptionsBuilder decorator(DecoratingHttpClientFunction decorator) {
        decoration.add(decorator);
        return this;
    }

    /**
     * Adds the specified RPC-level {@code decorator}.
     *
     * @param decorator the {@link Function} that transforms an {@link RpcClient} to another
     */
    public AbstractClientOptionsBuilder rpcDecorator(
            Function<? super RpcClient, ? extends RpcClient> decorator) {
        decoration.addRpc(decorator);
        return this;
    }

    /**
     * Adds the specified RPC-level {@code decorator}.
     *
     * @param decorator the {@link DecoratingRpcClientFunction} that intercepts an invocation
     */
    public AbstractClientOptionsBuilder rpcDecorator(DecoratingRpcClientFunction decorator) {
        decoration.addRpc(decorator);
        return this;
    }

    /**
     * Adds the specified HTTP header.
     */
    public AbstractClientOptionsBuilder addHttpHeader(CharSequence name, Object value) {
        requireNonNull(name, "name");
        requireNonNull(value, "value");
        httpHeaders.addObject(HttpHeaderNames.of(name), value);
        return this;
    }

    /**
     * Adds the specified HTTP headers.
     */
    public AbstractClientOptionsBuilder addHttpHeaders(
            Iterable<? extends Entry<? extends CharSequence, ?>> httpHeaders) {
        requireNonNull(httpHeaders, "httpHeaders");
        this.httpHeaders.addObject(httpHeaders);
        return this;
    }

    /**
     * Sets the specified HTTP header.
     */
    public AbstractClientOptionsBuilder setHttpHeader(CharSequence name, Object value) {
        requireNonNull(name, "name");
        requireNonNull(value, "value");
        httpHeaders.setObject(HttpHeaderNames.of(name), value);
        return this;
    }

    /**
     * Sets the specified HTTP headers.
     */
    public AbstractClientOptionsBuilder setHttpHeaders(
            Iterable<? extends Entry<? extends CharSequence, ?>> httpHeaders) {
        requireNonNull(httpHeaders, "httpHeaders");
        this.httpHeaders.setObject(httpHeaders);
        return this;
    }

    final ClientOptions buildOptions() {
        final Collection<ClientOptionValue<?>> optVals = options.values();
        final int numOpts = optVals.size();
        final ClientOptionValue<?>[] optValArray = optVals.toArray(new ClientOptionValue[numOpts + 2]);
        optValArray[numOpts] = ClientOption.DECORATION.newValue(decoration.build());
        optValArray[numOpts + 1] = ClientOption.HTTP_HEADERS.newValue(httpHeaders.build());

        return ClientOptions.of(optValArray);
    }
}
