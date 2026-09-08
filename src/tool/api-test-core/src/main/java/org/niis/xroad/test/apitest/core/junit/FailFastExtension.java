/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.test.apitest.core.junit;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fail-fast for the API suite. A thread-safe global failure counter increments on each failed test;
 * an {@link ExecutionCondition} skips any test that has not yet started once the failure threshold is reached.
 *
 * <p>Under parallel execution the semantics are "stop scheduling new tests" — tests already in flight finish,
 * but nothing new starts. The threshold defaults to {@value #DEFAULT_THRESHOLD}, is configurable via the
 * {@value #THRESHOLD_PROPERTY} system property, and a value of {@code 0} disables fail-fast entirely (collect
 * every failure). The counter is per JVM (per Gradle test task).
 */
@Slf4j
public class FailFastExtension implements ExecutionCondition, TestWatcher {

    static final String THRESHOLD_PROPERTY = "test-framework.fail-fast.threshold";
    static final int DEFAULT_THRESHOLD = 3;

    private static final AtomicInteger FAILURE_COUNT = new AtomicInteger(0);

    private static int threshold() {
        var raw = System.getProperty(THRESHOLD_PROPERTY);
        if (raw == null || raw.isBlank()) {
            return DEFAULT_THRESHOLD;
        }
        try {
            return Math.max(0, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            log.warn("Invalid {} value '{}', falling back to default {}", THRESHOLD_PROPERTY, raw, DEFAULT_THRESHOLD);
            return DEFAULT_THRESHOLD;
        }
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        var threshold = threshold();
        if (threshold == 0) {
            return ConditionEvaluationResult.enabled("Fail-fast disabled");
        }
        var failures = FAILURE_COUNT.get();
        if (failures >= threshold) {
            return ConditionEvaluationResult.disabled(
                    "Fail-fast: skipping, %d failure(s) reached threshold %d".formatted(failures, threshold));
        }
        return ConditionEvaluationResult.enabled("Below fail-fast threshold");
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        var failures = FAILURE_COUNT.incrementAndGet();
        log.warn("Fail-fast: failure {} of threshold {} ({})", failures, threshold(),
                context.getDisplayName());
    }
}
