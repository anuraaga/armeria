package com.linecorp.armeria.client.rest;

import java.lang.reflect.Type;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;

public class JacksonConverterFactory implements Converter.Factory {

    private final ObjectMapper objectMapper;

    public JacksonConverterFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Converter<?, String> stringConverter(Type type) {
        return (value, unused) -> value instanceof String ? (String) value
                                                          : objectMapper.writeValueAsString(value);
    }

    @Override
    public Converter<?, ByteBuf> byteBufConverter(Type unusedType) {
        return (value, alloc) -> {
            ByteBuf byteBuf = alloc.ioBuffer();
            ByteBufOutputStream stream = new ByteBufOutputStream(byteBuf);
            objectMapper.writeValue(stream, value);
            return byteBuf;
        };
    }

    @Override
    public Converter<ByteBuf, ?> responseConverter(Class<?> responseType) {
        return (content, unused) -> {
            ByteBufInputStream stream = new ByteBufInputStream(content);
            return objectMapper.readValue(stream, responseType);
        };
    }
}
