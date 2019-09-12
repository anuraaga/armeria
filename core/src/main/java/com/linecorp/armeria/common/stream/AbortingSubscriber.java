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

package com.linecorp.armeria.common.stream;

import javax.annotation.Nullable;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import com.linecorp.armeria.common.Flags;

final class AbortingSubscriber<T> implements Subscriber<T> {

    private static final AbortingSubscriber<Object> INSTANCE = new AbortingSubscriber<>(null);

    @SuppressWarnings("unchecked")
    static <T> AbortingSubscriber<T> get(Throwable error) {
        if (error instanceof AbortedStreamException && !Flags.verboseExceptions()) {
            // With verboseExceptions off, AbortedStreamException is a singleton so this subscriber can be too.
            return (AbortingSubscriber<T>) INSTANCE;
        } else {
            return new AbortingSubscriber<>(error);
        }
    }

    @Nullable
    private final Throwable error;

    private AbortingSubscriber(@Nullable Throwable error) {
        this.error = error;
    }

    Throwable getError() {
        return error != null ? error : AbortedStreamException.get();
    }

    @Override
    public void onSubscribe(Subscription s) {
        s.cancel();
    }

    @Override
    public void onNext(T o) {}

    @Override
    public void onError(Throwable cause) {}

    @Override
    public void onComplete() {}
}
