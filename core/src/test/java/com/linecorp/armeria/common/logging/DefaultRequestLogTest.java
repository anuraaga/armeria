/*
 * Copyright 2017 LINE Corporation
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

package com.linecorp.armeria.common.logging;

import static com.linecorp.armeria.common.logging.DefaultRequestLog.REQUEST_STRING_BUILDER_CAPACITY;
import static com.linecorp.armeria.common.logging.DefaultRequestLog.RESPONSE_STRING_BUILDER_CAPACITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpHeaders;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.common.RpcRequest;
import com.linecorp.armeria.common.RpcResponse;
import com.linecorp.armeria.common.SerializationFormat;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.testing.internal.AnticipatedException;

import io.netty.channel.Channel;

class DefaultRequestLogTest {

    private static final String VERY_LONG_STRING =
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut " +
            "labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco " +
            "laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in " +
            "voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat " +
            "non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.";

    @Mock
    private RequestContext ctx;

    @Mock
    private Channel channel;

    private DefaultRequestLog log;

    @BeforeEach
    void setUp() {
        log = new DefaultRequestLog(ctx);
    }

    @Test
    void endRequestSuccess() {
        when(ctx.sessionProtocol()).thenReturn(SessionProtocol.H2C);
        log.endRequest();
        assertThat(log.requestDurationNanos()).isZero();
        assertThat(log.requestCause()).isNull();
    }

    @Test
    void endRequestWithoutHeaders() {
        when(ctx.sessionProtocol()).thenReturn(SessionProtocol.H2C);
        log.endRequest();
        final RequestHeaders headers = log.requestHeaders();
        assertThat(headers.scheme()).isEqualTo("http");
        assertThat(headers.authority()).isEqualTo("?");
        assertThat(headers.method()).isSameAs(HttpMethod.UNKNOWN);
        assertThat(headers.path()).isEqualTo("?");
    }

    @Test
    void endResponseSuccess() {
        log.endResponse();
        assertThat(log.responseDurationNanos()).isZero();
        assertThat(log.responseCause()).isNull();
    }

    @Test
    void endResponseFailure() {
        final Throwable error = new Throwable("response failed");
        log.endResponse(error);
        assertThat(log.responseDurationNanos()).isZero();
        assertThat(log.responseCause()).isSameAs(error);
    }

    @Test
    void endResponseWithoutHeaders() {
        log.endResponse();
        assertThat(log.responseHeaders().status()).isEqualTo(HttpStatus.UNKNOWN);
    }

    @Test
    void rpcRequestIsPropagatedToContext() {
        final RpcRequest req = RpcRequest.of(Object.class, "foo");
        when(ctx.rpcRequest()).thenReturn(null);
        log.requestContent(req, null);
        verify(ctx, times(1)).updateRpcRequest(req);
    }

    @Test
    void rpcRequestIsNotPropagatedToContext() {
        final RpcRequest req = RpcRequest.of(Object.class, "foo");
        when(ctx.rpcRequest()).thenReturn(RpcRequest.of(Object.class, "bar"));
        log.requestContent(req, null);
        verify(ctx, never()).updateRpcRequest(any());
    }

    @Test
    void rpcFailure_endResponseWithoutCause() {
        final Throwable error = new Throwable("response failed");
        log.responseContent(RpcResponse.ofFailure(error), null);
        // If user code doesn't call endResponse, the framework automatically does with no cause.
        log.endResponse();
        assertThat(log.responseDurationNanos()).isZero();
        assertThat(log.responseCause()).isSameAs(error);
    }

    @Test
    void rpcFailure_endResponseDifferentCause() {
        final Throwable error = new Throwable("response failed one way");
        final Throwable error2 = new Throwable("response failed a different way?");
        log.responseContent(RpcResponse.ofFailure(error), null);
        log.endResponse(error2);
        assertThat(log.responseDurationNanos()).isZero();
        assertThat(log.responseCause()).isSameAs(error);
    }

    /**
     * The futures must be notified in the following order:
     * - Futures with less properties are notified first.
     *   - It will be unnatural if whenAvailable() is notified later than whenComplete().
     * - Request-related futures are notified first.
     *
     * @see DefaultRequestLog#satisfiedFutures()
     */
    @Test
    void notificationOrder() {
        final List<String> recording = new ArrayList<>();
        log.whenComplete()
           .thenAccept(log -> recording.add("COMPLETE"));
        log.whenAvailable(RequestLogProperty.RESPONSE_TRAILERS)
           .thenAccept(log -> recording.add("RESPONSE_TRAILERS"));
        log.whenAvailable(RequestLogProperty.RESPONSE_HEADERS)
           .thenAccept(log -> recording.add("RESPONSE_HEADERS"));
        log.whenRequestComplete()
           .thenAccept(log -> recording.add("REQUEST_COMPLETE"));
        log.whenAvailable(RequestLogProperty.REQUEST_TRAILERS)
           .thenAccept(log -> recording.add("REQUEST_TRAILERS"));
        log.whenAvailable(RequestLogProperty.REQUEST_HEADERS)
           .thenAccept(log -> recording.add("REQUEST_HEADERS"));
        log.whenAvailable(RequestLogProperty.SCHEME)
           .thenAccept(log -> recording.add("SCHEME"));

        log.startRequest(channel, SessionProtocol.H2C, null);
        log.endRequest();
        log.endResponse();

        assertThat(recording).containsExactly("SCHEME",
                                              "REQUEST_HEADERS",
                                              "REQUEST_TRAILERS",
                                              "REQUEST_COMPLETE",
                                              "RESPONSE_HEADERS",
                                              "RESPONSE_TRAILERS",
                                              "COMPLETE");
    }

    @Test
    void addChild() {
        final DefaultRequestLog child = new DefaultRequestLog(ctx);
        log.addChild(child);
        child.startRequest(channel, SessionProtocol.H2C);
        assertThat(log.requestStartTimeMicros()).isEqualTo(child.requestStartTimeMicros());
        assertThat(log.channel()).isSameAs(channel);
        assertThat(log.sessionProtocol()).isSameAs(SessionProtocol.H2C);

        child.serializationFormat(SerializationFormat.NONE);
        assertThat(log.scheme().serializationFormat()).isSameAs(SerializationFormat.NONE);

        child.requestFirstBytesTransferred();
        assertThat(log.requestFirstBytesTransferredTimeNanos())
                .isEqualTo(child.requestFirstBytesTransferredTimeNanos());

        final RequestHeaders foo = RequestHeaders.of(HttpMethod.GET, "/foo");
        child.requestHeaders(foo);
        assertThat(log.requestHeaders()).isSameAs(foo);

        final String requestContent = "baz";
        final String rawRequestContent = "qux";

        child.requestContent(requestContent, rawRequestContent);
        assertThat(log.requestContent()).isSameAs(requestContent);
        assertThat(log.rawRequestContent()).isSameAs(rawRequestContent);

        child.endRequest();
        assertThat(log.requestDurationNanos()).isEqualTo(child.requestDurationNanos());

        // response-side log are propagated when RequestLogBuilder.endResponseWithLastChild() is invoked
        child.startResponse();
        assertThatThrownBy(() -> log.responseStartTimeMicros())
                .isExactlyInstanceOf(RequestLogAvailabilityException.class);

        child.responseFirstBytesTransferred();
        assertThatThrownBy(() -> log.responseFirstBytesTransferredTimeNanos())
                .isExactlyInstanceOf(RequestLogAvailabilityException.class);

        final ResponseHeaders bar = ResponseHeaders.of(200);
        child.responseHeaders(bar);
        assertThatThrownBy(() -> log.responseHeaders())
                .isExactlyInstanceOf(RequestLogAvailabilityException.class);

        log.endResponseWithLastChild();
        assertThat(log.responseStartTimeMicros()).isEqualTo(child.responseStartTimeMicros());

        assertThat(log.responseFirstBytesTransferredTimeNanos())
                .isEqualTo(child.responseFirstBytesTransferredTimeNanos());
        assertThat(log.responseHeaders()).isSameAs(bar);

        final String responseContent = "baz1";
        final String rawResponseContent = "qux1";
        child.responseContent(responseContent, rawResponseContent);
        assertThat(log.responseContent()).isSameAs(responseContent);
        assertThat(log.rawResponseContent()).isSameAs(rawResponseContent);

        child.endResponse(new AnticipatedException("Oops!"));
        assertThat(log.responseDurationNanos()).isEqualTo(child.responseDurationNanos());
        assertThat(log.totalDurationNanos()).isEqualTo(child.totalDurationNanos());
    }

    @Test
    void toStringRequestBuilderCapacity() {
        final RequestHeaders reqHeaders =
                RequestHeaders.of(HttpMethod.POST, "/armeria/awesome",
                                  HttpHeaderNames.CONTENT_LENGTH, VERY_LONG_STRING.length());
        final HttpRequest req = AggregatedHttpRequest.of(reqHeaders, HttpData.ofUtf8(VERY_LONG_STRING))
                                                     .toHttpRequest();
        final ClientRequestContext ctx = ClientRequestContext.builder(req).build();

        final RequestLogBuilder logBuilder = ctx.logBuilder();
        logBuilder.requestLength(1000000000);
        logBuilder.requestContentPreview(VERY_LONG_STRING);

        final HttpHeaders requestTrailers = HttpHeaders.of(HttpHeaderNames.CONTENT_MD5, VERY_LONG_STRING);
        logBuilder.requestTrailers(requestTrailers);

        final IllegalArgumentException cause = new IllegalArgumentException(VERY_LONG_STRING);
        logBuilder.endRequest(cause);

        assertThat(ctx.log().ensureRequestComplete().toStringRequestOnly().length()).isLessThanOrEqualTo(
                REQUEST_STRING_BUILDER_CAPACITY +
                reqHeaders.toString().length() +
                VERY_LONG_STRING.length() +
                requestTrailers.toString().length() +
                cause.toString().length());
    }

    @Test
    void toStringResponseBuilderCapacity() {
        final RequestHeaders reqHeaders =
                RequestHeaders.of(HttpMethod.POST, "/armeria/awesome",
                                  HttpHeaderNames.CONTENT_LENGTH, VERY_LONG_STRING.length());
        final HttpRequest req = AggregatedHttpRequest.of(reqHeaders, HttpData.ofUtf8(VERY_LONG_STRING))
                                                     .toHttpRequest();
        final ClientRequestContext ctx = ClientRequestContext.builder(req).build();
        final RequestLogBuilder logBuilder = ctx.logBuilder();
        logBuilder.endRequest();

        final ResponseHeaders resHeaders = ResponseHeaders.of(200);
        logBuilder.responseHeaders(resHeaders);

        logBuilder.responseLength(1000000000);
        logBuilder.responseContentPreview(VERY_LONG_STRING);

        final HttpHeaders responseTrailers = HttpHeaders.of(HttpHeaderNames.CONTENT_MD5, VERY_LONG_STRING);
        logBuilder.responseTrailers(responseTrailers);

        final IllegalArgumentException cause = new IllegalArgumentException(VERY_LONG_STRING);
        logBuilder.endResponse(cause);

        assertThat(ctx.log().ensureComplete().toStringResponseOnly().length()).isLessThanOrEqualTo(
                RESPONSE_STRING_BUILDER_CAPACITY +
                resHeaders.toString().length() +
                VERY_LONG_STRING.length() +
                responseTrailers.toString().length() +
                cause.toString().length());
    }
}
