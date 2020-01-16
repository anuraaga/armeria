/*
 * Copyright 2020 LINE Corporation
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

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.linecorp.armeria.common.Request;
import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.RequestId;

/**
 * Provides the access to a {@link RequestLog} or {@link RequestOnlyLog}, while ensuring the interested
 * {@link RequestLogProperty}s are available.
 *
 * <p>The properties provided by {@link RequestLog} are are not always fully available. Use the following
 * methods to access the properties safely:
 * <ul>
 *   <li>{@link #isComplete()} or {@link #completeFuture()} to check if or to get notified when all request
 *       and response properties are available.</li>
 *   <li>{@link #isRequestComplete()} or {@link #requestCompleteFuture()} to check if or to get notified when
 *       all request properties are available.</li>
 *   <li>{@link #isAvailable(RequestLogProperty)}, {@link #isAvailable(RequestLogProperty...)},
 *       {@link #isAvailable(Iterable)}, {@link #partialFuture(RequestLogProperty)},
 *       {@link #partialFuture(RequestLogProperty...)} or {@link #partialFuture(Iterable)} to check if or
 *       to get notified when a certain set of properties are available.</li>
 * </ul></p>
 *
 * <p>If you are sure that certain properties are available, you can convert a {@link RequestLogAccess} into
 * a {@link RequestLog} or {@link RequestOnlyLog} by using the {@code "ensure*()"} methods, such as
 * {@link #ensureComplete()} and {@link #ensureRequestComplete()}.</p>
 */
public interface RequestLogAccess {

    /**
     * Returns {@code true} if the {@link Request} has been processed completely and thus all properties of
     * the {@link RequestLog} have been collected.
     */
    boolean isComplete();

    /**
     * Returns {@code true} if the {@link Request} has been consumed completely and thus all properties of
     * the {@link RequestOnlyLog} have been collected.
     */
    boolean isRequestComplete();

    /**
     * Returns {@code true} if the property signified by the specified {@link RequestLogProperty} is available.
     */
    boolean isAvailable(RequestLogProperty property);

    /**
     * Returns {@code true} if all of the properties signified by the specified {@link RequestLogProperty}s are
     * available.
     *
     * @throws IllegalArgumentException if {@code properties} is empty.
     */
    default boolean isAvailable(RequestLogProperty... properties) {
        requireNonNull(properties, "properties");
        checkArgument(properties.length != 0, "properties is empty.");
        for (RequestLogProperty p : properties) {
            if (!isAvailable(p)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns {@code true} if all of the properties signified by the specified {@link RequestLogProperty}s are
     * available.
     *
     * @throws IllegalArgumentException if {@code properties} is empty.
     */
    default boolean isAvailable(Iterable<RequestLogProperty> properties) {
        requireNonNull(properties, "properties");
        final Iterator<RequestLogProperty> i = properties.iterator();
        checkArgument(i.hasNext(), "properties is empty.");
        do {
            final RequestLogProperty p = i.next();
            if (!isAvailable(p)) {
                return false;
            }
        } while (i.hasNext());
        return true;
    }

    /**
     * Returns a {@link CompletableFuture} which will be completed when the {@link Request} has been processed
     * completely and thus all properties of the {@link RequestLog} have been collected.
     * <pre>{@code
     * logAccess.completeFuture().thenAccept(log -> {
     *     HttpStatus status = log.responseHeaders().status();
     *     if (status == HttpStatus.OK) {
     *         ...
     *     }
     * });
     * }</pre>
     */
    CompletableFuture<RequestLog> completeFuture();

    /**
     * Returns a {@link CompletableFuture} which will be completed when the {@link Request} has been consumed
     * completely and thus all properties of the {@link RequestOnlyLog} have been collected.
     * <pre>{@code
     * logAccess.requestCompleteFuture().thenAccept(log -> {
     *     SerializationFormat serFmt = log.scheme().serializationFormat();
     *     if (serFmt == ThriftSerializationFormats.BINARY) {
     *         ...
     *     }
     * });
     * }</pre>
     */
    CompletableFuture<RequestOnlyLog> requestCompleteFuture();

    /**
     * Returns a {@link CompletableFuture} which will be completed when the property signified by
     * the specified {@link RequestLogProperty} is collected. Note that the completion of the returned
     * {@link CompletableFuture} guarantees only the availability of the specified property, which means
     * any attempt to access other properties than specified may trigger
     * a {@link RequestLogAvailabilityException}. If in doubt, use {@link #completeFuture()} or
     * {@link #requestCompleteFuture()}.
     * <pre>{@code
     * logAccess.partialFuture(RequestLogProperty.REQUEST_HEADERS)
     *          .thenAccept(log -> {
     *              RequestHeaders headers = log.requestHeaders();
     *              if (headers.path().startsWith("/foo/")) {
     *                  ...
     *              }
     *          });
     * }</pre>
     */
    CompletableFuture<RequestLog> partialFuture(RequestLogProperty property);

    /**
     * Returns a {@link CompletableFuture} which will be completed when all the properties signified by
     * the specified {@link RequestLogProperty}s are collected. Note that the completion of the returned
     * {@link CompletableFuture} guarantees only the availability of the specified properties, which means
     * any attempt to access other properties than specified may trigger
     * a {@link RequestLogAvailabilityException}. If in doubt, use {@link #completeFuture()} or
     * {@link #requestCompleteFuture()}.
     * <pre>{@code
     * logAccess.partialFuture(RequestLogProperty.REQUEST_HEADERS,
     *                         RequestLogProperty.RESPONSE_HEADERS)
     *          .thenAccept(log -> {
     *              RequestHeaders reqHeaders = log.requestHeaders();
     *              ResponseHeaders resHeaders = log.responseHeaders();
     *              if (headers.path().startsWith("/foo/") &&
     *                  resHeaders.status() == HttpStatus.OK) {
     *                  ...
     *              }
     *          });
     * }</pre>
     *
     * @throws IllegalArgumentException if {@code properties} is empty.
     */
    CompletableFuture<RequestLog> partialFuture(RequestLogProperty... properties);

    /**
     * Returns a {@link CompletableFuture} which will be completed when all the properties signified by
     * the specified {@link RequestLogProperty}s are collected. Note that the completion of the returned
     * {@link CompletableFuture} guarantees only the availability of the specified properties, which means
     * any attempt to access other properties than specified may trigger
     * a {@link RequestLogAvailabilityException}. If in doubt, use {@link #completeFuture()} or
     * {@link #requestCompleteFuture()}.
     * <pre>{@code
     * logAccess.partialFuture(Lists.of(RequestLogProperty.REQUEST_HEADERS,
     *                                  RequestLogProperty.RESPONSE_HEADERS))
     *          .thenAccept(log -> {
     *              RequestHeaders reqHeaders = log.requestHeaders();
     *              ResponseHeaders resHeaders = log.responseHeaders();
     *              if (headers.path().startsWith("/foo/") &&
     *                  resHeaders.status() == HttpStatus.OK) {
     *                  ...
     *              }
     *          });
     * }</pre>
     *
     * @throws IllegalArgumentException if {@code properties} is empty.
     */
    CompletableFuture<RequestLog> partialFuture(Iterable<RequestLogProperty> properties);

    /**
     * Returns the {@link RequestLog} that is guaranteed to have all properties, for both request and response
     * side.
     *
     * @throws RequestLogAvailabilityException if the {@link Request} was not fully processed yet.
     */
    RequestLog ensureComplete();

    /**
     * Returns the {@link RequestLog} that is guaranteed to have all request-side properties.
     *
     * @throws RequestLogAvailabilityException if the {@link Request} was not fully consumed yet.
     */
    RequestOnlyLog ensureRequestComplete();

    /**
     * Returns the {@link RequestLog} that is guaranteed to have the property signified by the specified
     * {@link RequestLogProperty}.
     *
     * @throws RequestLogAvailabilityException if the specified {@link RequestLogProperty} is not available yet.
     */
    RequestLog ensurePartial(RequestLogProperty property);

    /**
     * Returns the {@link RequestLog} that is guaranteed to have all the properties signified by the specified
     * {@link RequestLogProperty}s.
     *
     * @throws RequestLogAvailabilityException if some of the specified {@link RequestLogProperty}s are
     *                                         not available yet.
     *
     * @throws IllegalArgumentException if {@code properties} is empty.
     */
    RequestLog ensurePartial(RequestLogProperty... properties);

    /**
     * Returns the {@link RequestLog} that is guaranteed to have all the properties signified by the specified
     * {@link RequestLogProperty}s.
     *
     * @throws RequestLogAvailabilityException if some of the specified {@link RequestLogProperty}s are
     *                                         not available yet.
     *
     * @throws IllegalArgumentException if {@code properties} is empty.
     */
    RequestLog ensurePartial(Iterable<RequestLogProperty> properties);

    /**
     * Returns the {@link RequestLog} for the {@link Request}, where all properties may not be available yet.
     * Note that this method is potentially unsafe; an attempt to access a unavailable property will trigger
     * a {@link RequestLogAvailabilityException}. If in doubt, use {@link #completeFuture()} or
     * {@link #requestCompleteFuture()}. Always consider guarding the property access with
     * {@link #isAvailable(RequestLogProperty)} when you have to use this method:
     * <pre>{@code
     * RequestLogAccess logAccess = ...;
     * if (logAccess.isAvailable(RequestLogProperty.REQUEST_HEADERS)) {
     *     RequestHeaders headers = logAccess.partial().requestHeaders();
     * }
     * }</pre>
     */
    RequestLog partial();

    /**
     * Returns an {@code int} representation of the currently available properties of this {@link RequestLog}.
     * This can be useful when needing to quickly compare the availability of the {@link RequestLog} during
     * the processing of the request. Use {@link #isAvailable(RequestLogProperty)} to actually check
     * availability.
     */
    int availabilityStamp();

    /**
     * Returns the {@link RequestContext} associated with the {@link Request} being handled.
     *
     * <p>This method always returns non-{@code null} regardless of what properties are currently available.
     */
    RequestContext context();

    /**
     * Returns the {@link RequestId}. This method is a shortcut for {@code context().id()}.
     *
     * @deprecated Use {@code log.context().id()}.
     */
    @Deprecated
    default RequestId id() {
        return context().id();
    }

    /**
     * Returns the list of {@link RequestLogAccess}es that provide access to the child {@link RequestLog}s,
     * ordered by the time it was added.
     */
    List<RequestLogAccess> children();
}