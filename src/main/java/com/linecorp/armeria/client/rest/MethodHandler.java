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

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.FullHttpRequest;

final class MethodHandler<T> {
  @SuppressWarnings("unchecked")
  static MethodHandler<?> create(Method method, Converter.Factory converterFactory) {
    Type responseType = getResponseType(method);
    RequestFactory requestFactory = RequestFactoryParser.parse(method, responseType, converterFactory);
    return new MethodHandler<>(requestFactory, Utils.getRawType(responseType));
  }

  private static Type getResponseType(Method method) {
    Type returnType = method.getGenericReturnType();
    if (Utils.hasUnresolvableType(returnType)) {
      throw Utils.methodError(method,
          "Method return type must not include a type variable or wildcard: %s", returnType);
    }
    if (returnType == void.class) {
      throw Utils.methodError(method, "Service methods cannot return void.");
    }
    try {
      return Utils.getFutureReturnType(returnType);
    } catch (RuntimeException e) { // Wide exception range because factories are user code.
      throw Utils.methodError(e, method, "Unable to create call adapter for %s", returnType);
    }
  }

  private final RequestFactory requestFactory;
  private final Class<?> responseRawType;

  private MethodHandler(RequestFactory requestFactory, Class<?> responseRawType) {
    this.requestFactory = requestFactory;
    this.responseRawType = responseRawType;
  }

  FullHttpRequest createRequest(ByteBufAllocator alloc, Object... args) throws IOException {
    return requestFactory.create(alloc, args);
  }

  <T> T parseResponse(ByteBuf content, Converter.Factory converterFactory) throws IOException {
    return (T) converterFactory.responseConverter(responseRawType).convert(content, null);
  }
}
