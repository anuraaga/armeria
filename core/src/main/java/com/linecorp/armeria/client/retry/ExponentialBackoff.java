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

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.base.MoreObjects;

final class ExponentialBackoff extends AbstractBackoff {
    private final long initialDelayMillis;
    private final long maxDelayMillis;
    private final double multiplier;

    @Nullable
    private final long[] precomputedDelays;

    ExponentialBackoff(long initialDelayMillis, long maxDelayMillis, double multiplier) {
        checkArgument(multiplier > 1.0, "multiplier: %s (expected: > 1.0)", multiplier);
        checkArgument(initialDelayMillis >= 0,
                      "initialDelayMillis: %s (expected: >= 0)", initialDelayMillis);
        checkArgument(initialDelayMillis <= maxDelayMillis, "maxDelayMillis: %s (expected: >= %s)",
                      maxDelayMillis, initialDelayMillis);
        this.initialDelayMillis = initialDelayMillis;
        this.maxDelayMillis = maxDelayMillis;
        this.multiplier = multiplier;

        if (computeNextDelayMillis(30) > maxDelayMillis) {
            // We'll only have a maximum of 30 different delays, so may as well precompute them.
            List<Long> precomputed = new ArrayList<>();
            for (int i = 1; i <= 30; i++) {
                long delay = computeNextDelayMillis(i);
                if (delay < maxDelayMillis) {
                    precomputed.add(delay);
                } else {
                    precomputed.add(maxDelayMillis);
                    break;
                }
            }
            precomputedDelays = precomputed.stream().mapToLong(l -> l).toArray();

        } else {
            precomputedDelays = null;
        }
    }

    @Override
    protected long doNextDelayMillis(int numAttemptsSoFar) {
        if (precomputedDelays != null) {
            int index = numAttemptsSoFar - 1;
            return precomputedDelays[Math.min(index, precomputedDelays.length - 1)];
        }

        return computeNextDelayMillis(numAttemptsSoFar);
    }

    private long computeNextDelayMillis(int numAttemptsSoFar) {
        if (numAttemptsSoFar == 1) {
            return initialDelayMillis;
        }
        final long nextDelay =
                saturatedMultiply(initialDelayMillis, Math.pow(multiplier, numAttemptsSoFar - 1));
        return Math.min(nextDelay, maxDelayMillis);
    }

    private static long saturatedMultiply(long left, double right) {
        final double result = left * right;
        return result >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) result;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                          .add("initialDelayMillis", initialDelayMillis)
                          .add("maxDelayMillis", maxDelayMillis)
                          .add("multiplier", multiplier)
                          .toString();
    }
}
