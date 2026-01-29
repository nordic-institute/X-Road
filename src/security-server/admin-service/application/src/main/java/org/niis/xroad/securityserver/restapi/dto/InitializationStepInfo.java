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

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * DTO containing detailed status information for a single initialization step.
 */
@Data
@Builder
public class InitializationStepInfo {
    /**
     * The initialization step this info pertains to.
     */
    private InitializationStep step;

    /**
     * Current status of this step.
     */
    private InitializationStepStatus status;

    /**
     * Timestamp when this step started executing.
     */
    private Instant startedAt;

    /**
     * Timestamp when this step completed (successfully or with failure).
     */
    private Instant completedAt;

    /**
     * Human-readable error message if the step failed.
     */
    private String errorMessage;

    /**
     * Machine-readable error code if the step failed.
     */
    private String errorCode;

    /**
     * Whether this step can be retried after a failure.
     */
    private boolean retryable;

    /**
     * Additional metadata or context information.
     */
    private List<String> metadata;

    /**
     * Creates an info object for a step that has not started.
     */
    public static InitializationStepInfo notStarted(InitializationStep step) {
        return InitializationStepInfo.builder()
                .step(step)
                .status(InitializationStepStatus.NOT_STARTED)
                .retryable(true)
                .build();
    }

    /**
     * Creates an info object for a completed step.
     */
    public static InitializationStepInfo completed(InitializationStep step, Instant completedAt) {
        return InitializationStepInfo.builder()
                .step(step)
                .status(InitializationStepStatus.COMPLETED)
                .completedAt(completedAt)
                .retryable(false)
                .build();
    }

    /**
     * Creates an info object for a failed step.
     */
    public static InitializationStepInfo failed(InitializationStep step, String errorMessage, String errorCode) {
        return InitializationStepInfo.builder()
                .step(step)
                .status(InitializationStepStatus.FAILED)
                .completedAt(Instant.now())
                .errorMessage(errorMessage)
                .errorCode(errorCode)
                .retryable(true)
                .build();
    }

    /**
     * Creates an info object for an in-progress step.
     */
    public static InitializationStepInfo inProgress(InitializationStep step) {
        return InitializationStepInfo.builder()
                .step(step)
                .status(InitializationStepStatus.IN_PROGRESS)
                .startedAt(Instant.now())
                .retryable(false)
                .build();
    }
}
