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

package com.linecorp.armeria.client.endpoint;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import com.google.common.collect.ImmutableList;

import com.linecorp.armeria.client.Endpoint;
import com.linecorp.armeria.common.util.Listenable;
import com.linecorp.armeria.common.util.SafeCloseable;

/**
 * A list of {@link Endpoint}s.
 */
public interface EndpointGroup extends Listenable<List<Endpoint>>, SafeCloseable {

    /**
     * Returns a singleton {@link EndpointGroup} which does not contain any {@link Endpoint}s.
     */
    static EndpointGroup empty() {
        return StaticEndpointGroup.EMPTY;
    }

    static EndpointGroup of(Endpoint... endpoints) {
        requireNonNull(endpoints, "endpoints");
        return of(ImmutableList.copyOf(endpoints));
    }

    static EndpointGroup of(Iterable<Endpoint> endpoints) {
        requireNonNull(endpoints, "endpoints");
        return new StaticEndpointGroup(endpoints);
    }

    static EndpointGroup ofAll(EndpointGroup... endpointGroups) {
        requireNonNull(endpointGroups, "endpointGroups");
        return ofAll(ImmutableList.copyOf(endpointGroups));
    }

    static EndpointGroup ofAll(Iterable<EndpointGroup> endpointGroups) {
        requireNonNull(endpointGroups, "endpointGroups");
        return new CompositeEndpointGroup(endpointGroups);
    }

    /**
     * Return the endpoints held by this {@link EndpointGroup}.
     */
    List<Endpoint> endpoints();

    /**
     * Returns a {@link CompletableFuture} which is completed when the initial {@link Endpoint}s are ready.
     */
    CompletableFuture<List<Endpoint>> initialEndpointsFuture();

    /**
     * Waits until the initial {@link Endpoint}s are ready.
     *
     * @throws java.util.concurrent.CancellationException if {@link #close()} was called before the initial
     * {@link Endpoint}s are set
     */
    default List<Endpoint> awaitInitialEndpoints() throws InterruptedException {
        try {
            return initialEndpointsFuture().get();
        } catch (ExecutionException e) {
            throw new CompletionException(e.getCause());
        }
    }

    @Override
    default void addListener(Consumer<? super List<Endpoint>> listener) {}

    @Override
    default void removeListener(Consumer<?> listener) {}

    @Override
    default void close() {}

    /*
     * Creates a new {@link EndpointGroup} that tries this {@link EndpointGroup} first and then the specified
     * {@link EndpointGroup} when this {@link EndpointGroup} does not have a requested resource.
     *
     * @param nextEndpointGroup the {@link EndpointGroup} to try secondly.
     */
    default EndpointGroup orElse(EndpointGroup nextEndpointGroup) {
        return new OrElseEndpointGroup(this, nextEndpointGroup);
    }
}
