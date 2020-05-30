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

package com.linecorp.armeria.client.retry;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.time.Duration;
import java.util.function.Function;

import com.google.common.base.MoreObjects.ToStringHelper;

import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.common.HttpResponse;

/**
 * Builds a new {@link RetryingClient} or its decorator function.
 */
public final class RetryingClientBuilder extends AbstractRetryingClientBuilder<HttpResponse> {

    private static final int DEFAULT_MAX_CONTENT_LENGTH = Integer.MAX_VALUE;

    private boolean useRetryAfter;

    private int maxContentLength = DEFAULT_MAX_CONTENT_LENGTH;
    private boolean isMaxContentLengthSet;
    private final boolean needsContentInRule;

    /**
     * Creates a new builder with the specified {@link RetryRule}.
     */
    RetryingClientBuilder(RetryRule retryRule) {
        super(retryRule);
        needsContentInRule = false;
        maxContentLength = 0;
    }

    /**
     * Creates a new builder with the specified {@link RetryRuleWithContent}.
     */
    RetryingClientBuilder(RetryRuleWithContent<HttpResponse> retryRuleWithContent) {
        super(retryRuleWithContent);
        needsContentInRule = true;
    }

    /**
     * Creates a new builder with the specified {@link RetryRuleWithContent}.
     */
    RetryingClientBuilder(RetryRuleWithContent<HttpResponse> retryRuleWithContent, int maxContentLength) {
        super(retryRuleWithContent);
        needsContentInRule = true;
        isMaxContentLengthSet = true;
        this.maxContentLength = maxContentLength;
    }

    /**
     * Whether retry should be attempted according to the {@code retryHeader} from the server or not.
     * The web server may request a client to retry after specific time with {@code retryAfter} header.
     * If you want to follow the direction from the server not by your {@link Backoff}, invoke this method
     * with the {@code useRetryAfter} with {@code true} to request after the specified delay.
     *
     * @param useRetryAfter {@code true} if you want to retry after using the {@code retryAfter} header.
     *                      {@code false} otherwise
     * @return {@link RetryingClientBuilder} to support method chaining
     */
    public RetryingClientBuilder useRetryAfter(boolean useRetryAfter) {
        this.useRetryAfter = useRetryAfter;
        return this;
    }

    /**
     * Sets the length of content required to determine whether to retry or not. If the total length of content
     * exceeds this length and there's no retry condition matched, it will hand over the stream to the client.
     * Note that this property is useful only if you specified {@link RetryRuleWithContent} when calling
     * this builder's constructor. The default value of this property is {@value #DEFAULT_MAX_CONTENT_LENGTH}.
     *
     * @throws IllegalStateException if this builder is created with a {@link RetryRule} rather than
     *                               {@link RetryRuleWithContent}
     * @throws IllegalArgumentException if the specified {@code maxContentLength} is equal to or
     *                                  less than {@code 0}
     *
     * @deprecated Use {@link RetryingClient#builder(RetryRuleWithContent, int)}.
     */
    @Deprecated
    public RetryingClientBuilder contentPreviewLength(int maxContentLength) {
        checkState(!isMaxContentLengthSet, "maxContentLength is already set by " +
                                           "RetryingClient.builder(RetryRuleWithContent, maxContentLength)");
        checkState(needsContentInRule, "cannot set maxContentLength when RetryRule is used; " +
                                       "Use RetryRuleWithContent to enable this feature.");
        checkArgument(maxContentLength > 0,
                      "maxContentLength: %s (expected: > 0)", maxContentLength);
        this.maxContentLength = maxContentLength;
        return this;
    }

    /**
     * Returns a newly-created {@link RetryingClient} based on the properties of this builder.
     */
    public RetryingClient build(HttpClient delegate) {
        if (needsContentInRule) {
            return new RetryingClient(delegate, retryRuleWithContent(), maxTotalAttempts(),
                                      responseTimeoutMillisForEachAttempt(), useRetryAfter, maxContentLength);
        }

        return new RetryingClient(delegate, retryRule(), maxTotalAttempts(),
                                  responseTimeoutMillisForEachAttempt(), useRetryAfter);
    }

    /**
     * Returns a newly-created decorator that decorates an {@link HttpClient} with a new
     * {@link RetryingClient} based on the properties of this builder.
     */
    public Function<? super HttpClient, RetryingClient> newDecorator() {
        return this::build;
    }

    @Override
    public String toString() {
        final ToStringHelper stringHelper = toStringHelper().add("useRetryAfter", useRetryAfter);
        if (needsContentInRule) {
            stringHelper.add("maxContentLength", maxContentLength);
        }
        return stringHelper.toString();
    }

    // Methods that were overridden to change the return type.

    @Override
    public RetryingClientBuilder maxTotalAttempts(int maxTotalAttempts) {
        return (RetryingClientBuilder) super.maxTotalAttempts(maxTotalAttempts);
    }

    @Override
    public RetryingClientBuilder responseTimeoutMillisForEachAttempt(
            long responseTimeoutMillisForEachAttempt) {
        return (RetryingClientBuilder)
                super.responseTimeoutMillisForEachAttempt(responseTimeoutMillisForEachAttempt);
    }

    @Override
    public RetryingClientBuilder responseTimeoutForEachAttempt(Duration responseTimeoutForEachAttempt) {
        return (RetryingClientBuilder) super.responseTimeoutForEachAttempt(responseTimeoutForEachAttempt);
    }
}
