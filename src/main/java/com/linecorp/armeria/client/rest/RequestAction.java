/*
 * Copyright (C) 2015 Square, Inc.
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

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

abstract class RequestAction<T> {
    abstract void perform(RequestBuilder builder, T value, ByteBufAllocator alloc) throws IOException;

    final RequestAction<Iterable<T>> iterable() {
        return new RequestAction<Iterable<T>>() {
            @Override
            void perform(RequestBuilder builder, Iterable<T> values, ByteBufAllocator alloc)
                    throws IOException {
                if (values == null) {
                    return; // Skip null values.
                }

                for (T value : values) {
                    RequestAction.this.perform(builder, value, alloc);
                }
            }
        };
    }

    final RequestAction<Object> array() {
        return new RequestAction<Object>() {
            @Override
            void perform(RequestBuilder builder, Object values, ByteBufAllocator alloc) throws IOException {
                if (values == null) {
                    return; // Skip null values.
                }

                for (int i = 0, size = Array.getLength(values); i < size; i++) {
                    //noinspection unchecked
                    RequestAction.this.perform(builder, (T) Array.get(values, i), alloc);
                }
            }
        };
    }

    static final class Url extends RequestAction<String> {
        @Override
        void perform(RequestBuilder builder, String value, ByteBufAllocator alloc) {
            builder.setRelativeUrl(value);
        }
    }

    static final class Header<T> extends RequestAction<T> {
        private final String name;
        private final Converter<T, String> valueConverter;

        Header(String name, Converter<T, String> valueConverter) {
            this.name = requireNonNull(name, "name == null");
            this.valueConverter = valueConverter;
        }

        @Override
        void perform(RequestBuilder builder, T value, ByteBufAllocator alloc) throws IOException {
            if (value == null) {
                return; // Skip null values.
            }
            builder.addHeader(name, valueConverter.convert(value, alloc));
        }
    }

    static final class Path<T> extends RequestAction<T> {
        private final String name;
        private final Converter<T, String> valueConverter;
        private final boolean encoded;

        Path(String name, Converter<T, String> valueConverter, boolean encoded) {
            this.name = requireNonNull(name, "name == null");
            this.valueConverter = valueConverter;
            this.encoded = encoded;
        }

        @Override
        void perform(RequestBuilder builder, T value, ByteBufAllocator alloc) throws IOException {
            if (value == null) {
                throw new IllegalArgumentException(
                        "Path parameter \"" + name + "\" value must not be null.");
            }
            builder.addPathParam(name, valueConverter.convert(value, alloc));
        }
    }

    static final class Query<T> extends RequestAction<T> {
        private final String name;
        private final Converter<T, String> valueConverter;
        private final boolean encoded;

        Query(String name, Converter<T, String> valueConverter, boolean encoded) {
            this.name = requireNonNull(name, "name == null");
            this.valueConverter = valueConverter;
            this.encoded = encoded;
        }

        @Override
        void perform(RequestBuilder builder, T value, ByteBufAllocator alloc) throws IOException {
            if (value == null) {
                return; // Skip null values.
            }
            builder.addQueryParam(name, valueConverter.convert(value, alloc));
        }
    }

    static final class QueryMap<T> extends RequestAction<Map<String, T>> {
        private final Converter<T, String> valueConverter;
        private final boolean encoded;

        QueryMap(Converter<T, String> valueConverter, boolean encoded) {
            this.valueConverter = valueConverter;
            this.encoded = encoded;
        }

        @Override
        void perform(RequestBuilder builder, Map<String, T> value, ByteBufAllocator alloc) throws IOException {
            if (value == null) {
                return; // Skip null values.
            }

            for (Map.Entry<String, T> entry : value.entrySet()) {
                String entryKey = entry.getKey();
                if (entryKey == null) {
                    throw new IllegalArgumentException("Query map contained null key.");
                }
                T entryValue = entry.getValue();
                if (entryValue != null) { // Skip null values.
                    builder.addQueryParam(entryKey, valueConverter.convert(entryValue, alloc));
                }
            }
        }
    }

    static final class Field<T> extends RequestAction<T> {
        private final String name;
        private final Converter<T, String> valueConverter;
        private final boolean encoded;

        Field(String name, Converter<T, String> valueConverter, boolean encoded) {
            this.name = requireNonNull(name, "name == null");
            this.valueConverter = valueConverter;
            this.encoded = encoded;
        }

        @Override
        void perform(RequestBuilder builder, T value, ByteBufAllocator alloc) throws IOException {
            if (value == null) {
                return; // Skip null values.
            }
            builder.addFormField(name, valueConverter.convert(value, alloc));
        }
    }

    static final class FieldMap<T> extends RequestAction<Map<String, T>> {
        private final Converter<T, String> valueConverter;
        private final boolean encoded;

        FieldMap(Converter<T, String> valueConverter, boolean encoded) {
            this.valueConverter = valueConverter;
            this.encoded = encoded;
        }

        @Override
        void perform(RequestBuilder builder, Map<String, T> value, ByteBufAllocator alloc) throws IOException {
            if (value == null) {
                return; // Skip null values.
            }

            for (Map.Entry<String, T> entry : value.entrySet()) {
                String entryKey = entry.getKey();
                if (entryKey == null) {
                    throw new IllegalArgumentException("Field map contained null key.");
                }
                T entryValue = entry.getValue();
                if (entryValue != null) { // Skip null values.
                    builder.addFormField(entryKey, valueConverter.convert(entryValue, alloc));
                }
            }
        }
    }

    static final class Body<T> extends RequestAction<T> {
        private final Converter<T, ByteBuf> converter;

        Body(Converter<T, ByteBuf> converter) {
            this.converter = converter;
        }

        @Override
        void perform(RequestBuilder builder, T value, ByteBufAllocator alloc) {
            if (value == null) {
                throw new IllegalArgumentException("Body parameter value must not be null.");
            }
            ByteBuf body;
            try {
                body = converter.convert(value, alloc);
            } catch (IOException e) {
                throw new IllegalArgumentException("Unable to convert " + value + " to RequestBody", e);
            }
            builder.setBody(body);
        }
    }
}
