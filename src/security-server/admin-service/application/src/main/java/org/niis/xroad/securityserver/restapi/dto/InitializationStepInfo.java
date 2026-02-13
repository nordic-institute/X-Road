/*
 * The MIT License
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
package org.niis.xroad.securityserver.restapi.dto;

import java.time.Instant;
import java.util.List;

public record InitializationStepInfo(
        InitializationStep step,
        InitializationStepStatus status,
        Instant startedAt,
        Instant completedAt,
        String errorMessage,
        String errorCode,
        boolean retryable,
        List<String> metadata
) {

    public static InitializationStepInfo notStarted(InitializationStep step) {
        return new InitializationStepInfo(step, InitializationStepStatus.NOT_STARTED,
                null, null, null, null, true, null);
    }

    public static InitializationStepInfo completed(InitializationStep step, Instant completedAt) {
        return new InitializationStepInfo(step, InitializationStepStatus.COMPLETED,
                null, completedAt, null, null, false, null);
    }

    public static InitializationStepInfo failed(InitializationStep step, String errorMessage, String errorCode) {
        return new InitializationStepInfo(step, InitializationStepStatus.FAILED,
                null, Instant.now(), errorMessage, errorCode, true, null);
    }

    public static InitializationStepInfo inProgress(InitializationStep step) {
        return new InitializationStepInfo(step, InitializationStepStatus.IN_PROGRESS,
                Instant.now(), null, null, null, false, null);
    }
}
