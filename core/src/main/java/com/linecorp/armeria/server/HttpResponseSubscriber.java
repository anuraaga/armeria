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

package com.linecorp.armeria.server;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linecorp.armeria.common.AggregatedHttpMessage;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaders;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpObject;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.HttpStatusClass;
import com.linecorp.armeria.common.logging.RequestLog;
import com.linecorp.armeria.common.logging.RequestLogBuilder;
import com.linecorp.armeria.internal.HttpObjectEncoder;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.util.ReferenceCountUtil;

final class HttpResponseSubscriber implements Subscriber<HttpObject>, RequestTimeoutChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(HttpResponseSubscriber.class);

    private static final AggregatedHttpMessage INTERNAL_SERVER_ERROR_MESSAGE =
            AggregatedHttpMessage.of(HttpStatus.INTERNAL_SERVER_ERROR);
    private static final AggregatedHttpMessage SERVICE_UNAVAILABLE_MESSAGE =
            AggregatedHttpMessage.of(HttpStatus.SERVICE_UNAVAILABLE);

    enum State {
        NEEDS_HEADERS,
        NEEDS_DATA_OR_TRAILING_HEADERS,
        DONE,
    }

    private final ChannelHandlerContext ctx;
    private final HttpObjectEncoder responseEncoder;
    private final DecodedHttpRequest req;
    private final DefaultServiceRequestContext reqCtx;
    private final Consumer<RequestLog> accessLogWriter;
    private final long startTimeNanos;

    private Subscription subscription;
    private ScheduledFuture<?> timeoutFuture;
    private State state = State.NEEDS_HEADERS;
    private boolean isComplete;

    HttpResponseSubscriber(ChannelHandlerContext ctx, HttpObjectEncoder responseEncoder,
                           DefaultServiceRequestContext reqCtx, DecodedHttpRequest req,
                           Consumer<RequestLog> accessLogWriter) {
        this.ctx = ctx;
        this.responseEncoder = responseEncoder;
        this.req = req;
        this.reqCtx = reqCtx;
        this.accessLogWriter = accessLogWriter;
        startTimeNanos = System.nanoTime();
    }

    private Service<?, ?> service() {
        return reqCtx.service();
    }

    private RequestLogBuilder logBuilder() {
        return reqCtx.logBuilder();
    }

    @Override
    public void onRequestTimeoutChange(long newRequestTimeoutMillis) {
        // Cancel the previously scheduled timeout, if exists.
        cancelTimeout();

        if (newRequestTimeoutMillis > 0 && state != State.DONE) {
            // Calculate the amount of time passed since the creation of this subscriber.
            final long passedTimeMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNanos);

            if (passedTimeMillis < newRequestTimeoutMillis) {
                timeoutFuture = ctx.channel().eventLoop().schedule(
                        this::onTimeout,
                        newRequestTimeoutMillis - passedTimeMillis, TimeUnit.MILLISECONDS);
            } else {
                // We went past the dead line set by the new timeout already.
                onTimeout();
            }
        }
    }

    private void onTimeout() {
        if (state != State.DONE) {
            reqCtx.setTimedOut();
            Runnable requestTimeoutHandler = reqCtx.requestTimeoutHandler();
            if (requestTimeoutHandler != null) {
                requestTimeoutHandler.run();
            } else {
                failAndRespond(RequestTimeoutException.get(),
                               SERVICE_UNAVAILABLE_MESSAGE, Http2Error.INTERNAL_ERROR);
            }
        }
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        assert this.subscription == null;
        this.subscription = subscription;

        // Schedule the initial request timeout.
        onRequestTimeoutChange(reqCtx.requestTimeoutMillis());

        // Start consuming.
        subscription.request(1);
    }

    @Override
    public void onNext(HttpObject o) {
        if (!(o instanceof HttpData) && !(o instanceof HttpHeaders)) {
            throw newIllegalStateException(
                    "published an HttpObject that's neither HttpHeaders nor HttpData: " + o +
                    " (service: " + service() + ')');
        }

        boolean endOfStream = o.isEndOfStream();
        switch (state) {
            case NEEDS_HEADERS: {
                logBuilder().startResponse();
                if (!(o instanceof HttpHeaders)) {
                    throw newIllegalStateException(
                            "published an HttpData without a preceding Http2Headers: " + o +
                            " (service: " + service() + ')');
                }

                final HttpHeaders headers = (HttpHeaders) o;
                final HttpStatus status = headers.status();
                if (status == null) {
                    throw newIllegalStateException("published an HttpHeaders without status: " + o +
                                                   " (service: " + service() + ')');
                }

                if (status.codeClass() == HttpStatusClass.INFORMATIONAL) {
                    // Needs non-informational headers.
                    break;
                }

                final int statusCode = status.code();
                logBuilder().responseHeaders(headers);

                if (req.method() == HttpMethod.HEAD) {
                    // HEAD responses always close the stream with the initial headers, even if not explicitly
                    // set.
                    endOfStream = true;
                    break;
                }

                switch (statusCode) {
                    case 204:
                    case 205:
                    case 304:
                        // These responses are not allowed to have content so we always close the stream even if
                        // not explicitly set.
                        endOfStream = true;
                        break;
                    default:
                        state = State.NEEDS_DATA_OR_TRAILING_HEADERS;
                }
                break;
            }
            case NEEDS_DATA_OR_TRAILING_HEADERS: {
                if (o instanceof HttpHeaders) {
                    final HttpHeaders trailingHeaders = (HttpHeaders) o;
                    if (trailingHeaders.status() != null) {
                        throw newIllegalStateException(
                                "published a trailing HttpHeaders with status: " + o +
                                " (service: " + service() + ')');
                    }

                    // Trailing headers always end the stream even if not explicitly set.
                    endOfStream = true;
                }
                break;
            }
            case DONE:
                ReferenceCountUtil.safeRelease(o);
                return;
        }

        write(o, endOfStream, true);
    }

    @Override
    public void onError(Throwable cause) {
        if (cause instanceof HttpResponseException) {
            // Timeout may occur when the aggregation of the error response takes long.
            // If timeout occurs, respond with 503 Service Unavailable.
            ((HttpResponseException) cause).httpResponse()
                                           .aggregate(ctx.executor())
                                           .whenCompleteAsync((message, throwable) -> {
                                               if (throwable != null) {
                                                   failAndRespond(throwable,
                                                                  INTERNAL_SERVER_ERROR_MESSAGE,
                                                                  Http2Error.CANCEL);
                                               } else {
                                                   failAndRespond(cause, message, Http2Error.CANCEL);
                                               }
                                           }, ctx.executor());
        } else if (cause instanceof HttpStatusException) {
            failAndRespond(cause,
                           AggregatedHttpMessage.of(((HttpStatusException) cause).httpStatus()),
                           Http2Error.CANCEL);
        } else {
            logger.warn("{} Unexpected exception from a service or a response publisher: {}",
                        ctx.channel(), service(), cause);

            failAndRespond(cause, INTERNAL_SERVER_ERROR_MESSAGE, Http2Error.INTERNAL_ERROR);
        }
    }

    @Override
    public void onComplete() {
        if (!cancelTimeout() && reqCtx.requestTimeoutHandler() == null) {
            // We have already returned a failed response due to a timeout.
            return;
        }

        if (wroteNothing(state)) {
            logger.warn("{} Published nothing (or only informational responses): {}", ctx.channel(), service());
            responseEncoder.writeReset(ctx, req.id(), req.streamId(), Http2Error.INTERNAL_ERROR);
            return;
        }

        if (state != State.DONE) {
            write(HttpData.EMPTY_DATA, true, true);
        }
    }

    private void write(HttpObject o, boolean endOfStream, boolean flush) {
        final Channel ch = ctx.channel();
        if (endOfStream) {
            setDone();
        }

        ch.eventLoop().execute(() -> write0(o, endOfStream, flush));
    }

    private void write0(HttpObject o, boolean endOfStream, boolean flush) {
        final ChannelFuture future;
        if (o instanceof HttpData) {
            final HttpData data = (HttpData) o;
            future = responseEncoder.writeData(ctx, req.id(), req.streamId(), data, endOfStream);
            logBuilder().increaseResponseLength(data.length());
        } else if (o instanceof HttpHeaders) {
            future = responseEncoder.writeHeaders(ctx, req.id(), req.streamId(), (HttpHeaders) o, endOfStream);
        } else {
            // Should never reach here because we did validation in onNext().
            throw new Error();
        }

        future.addListener((ChannelFuture f) -> {
            // Write an access log if:
            // - every message has been sent successfully.
            // - any write operation is failed with a cause.
            if (f.isSuccess()) {
                if (endOfStream && tryComplete()) {
                    logBuilder().endResponse();
                    accessLogWriter.accept(reqCtx.log());
                }
                if (state != State.DONE) {
                    subscription.request(1);
                }
                return;
            }

            if (tryComplete()) {
                setDone();
                logBuilder().endResponse(f.cause());
                subscription.cancel();
                accessLogWriter.accept(reqCtx.log());
            }
            HttpServerHandler.CLOSE_ON_FAILURE.operationComplete(f);
        });

        if (flush) {
            ctx.flush();
        }

        if (state == State.DONE) {
            subscription.cancel();
        }
    }

    private void setDone() {
        cancelTimeout();
        state = State.DONE;
    }

    private void failAndRespond(Throwable cause, AggregatedHttpMessage message, Http2Error error) {
        final HttpHeaders headers = message.headers();
        final HttpData content = message.content();

        logBuilder().responseHeaders(headers);
        logBuilder().increaseResponseLength(content.length());

        final State state = this.state; // Keep the state before calling fail() because it updates state.
        setDone();
        subscription.cancel();

        final int id = req.id();
        final int streamId = req.streamId();

        final ChannelFuture future;
        if (wroteNothing(state)) {
            // Did not write anything yet; we can send an error response instead of resetting the stream.
            if (content.isEmpty()) {
                future = responseEncoder.writeHeaders(ctx, id, streamId, headers, true);
            } else {
                responseEncoder.writeHeaders(ctx, id, streamId, headers, false);
                future = responseEncoder.writeData(ctx, id, streamId, content, true);
            }
        } else {
            // Wrote something already; we have to reset/cancel the stream.
            future = responseEncoder.writeReset(ctx, id, streamId, error);
        }

        if (state != State.DONE) {
            future.addListener(unused -> {
                // Write an access log always with a cause. Respect the first specified cause.
                if (tryComplete()) {
                    logBuilder().endResponse(cause);
                    accessLogWriter.accept(reqCtx.log());
                }
            });
        }
        ctx.flush();
    }

    private boolean tryComplete() {
        if (isComplete) {
            return false;
        }
        isComplete = true;
        return true;
    }

    private static boolean wroteNothing(State state) {
        return state == State.NEEDS_HEADERS;
    }

    private boolean cancelTimeout() {
        final ScheduledFuture<?> timeoutFuture = this.timeoutFuture;
        if (timeoutFuture == null) {
            return true;
        }

        this.timeoutFuture = null;
        return timeoutFuture.cancel(false);
    }

    private IllegalStateException newIllegalStateException(String msg) {
        final IllegalStateException cause = new IllegalStateException(msg);
        failAndRespond(cause, INTERNAL_SERVER_ERROR_MESSAGE, Http2Error.INTERNAL_ERROR);
        return cause;
    }
}
