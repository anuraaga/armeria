package com.linecorp.armeria.client.rest;

import java.io.IOException;
import java.lang.reflect.Type;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

@FunctionalInterface
public interface Converter<IN, OUT> {
    OUT convert(IN value, ByteBufAllocator alloc) throws IOException;

    interface Factory {
        Converter<?, String> stringConverter(Type valueType);
        Converter<?, ByteBuf> byteBufConverter(Type valueType);
        Converter<ByteBuf, ?> responseConverter(Class<?> responseType);
    }
}
