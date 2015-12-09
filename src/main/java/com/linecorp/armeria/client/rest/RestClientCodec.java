package com.linecorp.armeria.client.rest;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.linecorp.armeria.client.ClientCodec;
import com.linecorp.armeria.client.rest.Converter.Factory;
import com.linecorp.armeria.common.Scheme;
import com.linecorp.armeria.common.SerializationFormat;
import com.linecorp.armeria.common.ServiceInvocationContext;
import com.linecorp.armeria.common.SessionProtocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Promise;

public class RestClientCodec implements ClientCodec {

    private static final AttributeKey<MethodHandler> METHOD_HANDLER =
            AttributeKey.valueOf(RestClientCodec.class, "METHOD_HANDLER");

    private static final Map<SerializationFormat, Factory> serializers;
    static {
        HashMap<SerializationFormat, Factory> serializersBuilder = new HashMap<>();
        serializersBuilder.put(SerializationFormat.JSON, new JacksonConverterFactory(new ObjectMapper()));
        serializers = Collections.unmodifiableMap(serializersBuilder);
    }

    private final String host;
    private final SerializationFormat serializationFormat;
    private final Factory requestSerializer;
    private final Map<Method, MethodHandler> methodHandlers;

    public RestClientCodec(String host, SerializationFormat serializationFormat) {
        this.host = host;
        this.serializationFormat = serializationFormat;
        this.requestSerializer = serializers.get(serializationFormat);
        this.methodHandlers = new HashMap<>();
    }

    @Override
    public <T> void prepareRequest(Method method, Object[] args, Promise<T> resultPromise) {
        // Nothing to do.
    }

    @Override
    public EncodeResult encodeRequest(Channel channel, SessionProtocol sessionProtocol, Method method,
                                      Object[] args) {
        MethodHandler handler = methodHandlers.computeIfAbsent(method,
                                                               m -> MethodHandler.create(m, requestSerializer));
        try {
            FullHttpRequest fullHttpRequest = handler.createRequest(channel.alloc(), args);
            Scheme scheme = Scheme.of(serializationFormat, sessionProtocol);
            RestInvocation invocation =
                    new RestInvocation(channel, scheme, host, fullHttpRequest.uri(), fullHttpRequest,
                                       method, args);
            invocation.attr(METHOD_HANDLER).set(handler);
            return invocation;
        } catch (IOException e) {
            // TODO: Implement failure.
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public <T> T decodeResponse(ServiceInvocationContext ctx, ByteBuf content, Object originalResponse)
            throws Exception {
        // Assume same response type as request type for now.
        MethodHandler handler = ctx.attr(METHOD_HANDLER).get();
        return (T) handler.parseResponse(content, serializers.get(ctx.scheme().serializationFormat()));
    }

    @Override
    public boolean isAsyncClient() {
        return true;
    }
}
