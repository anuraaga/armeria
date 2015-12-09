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

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;

final class RequestFactory {
  private final String method;
  private final String relativeUrl;
  private final HttpHeaders headers;
  private final boolean hasBody;
  private final boolean isFormEncoded;
  private final RequestAction[] requestActions;

  RequestFactory(String method, String relativeUrl, HttpHeaders headers,
      boolean hasBody, boolean isFormEncoded, RequestAction[] requestActions) {
    this.method = method;
    this.relativeUrl = relativeUrl;
    this.headers = headers;
    this.hasBody = hasBody;
    this.isFormEncoded = isFormEncoded;
    this.requestActions = requestActions;
  }

  FullHttpRequest create(ByteBufAllocator alloc, Object... args) throws IOException {
    RequestBuilder requestBuilder =
        new RequestBuilder(method, relativeUrl, headers, hasBody, isFormEncoded);

    if (args != null) {
      RequestAction[] actions = requestActions;
      if (actions.length != args.length) {
        throw new IllegalArgumentException("Argument count ("
            + args.length
            + ") doesn't match action count ("
            + actions.length
            + ")");
      }
      for (int i = 0, count = args.length; i < count; i++) {
        actions[i].perform(requestBuilder, args[i], alloc);
      }
    }

    return requestBuilder.build(alloc);
  }
}
