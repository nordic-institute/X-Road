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

import java.util.ArrayList;
import java.util.List;

/**
 * DTO containing the complete initialization status with granular step tracking.
 */
@Data
@Builder
public class InitializationStatusV2 {

    /**
     * Overall status summarized from individual step statuses.
     */
    public enum OverallStatus {
        /**
         * No initialization steps have been started.
         */
        NOT_STARTED,

        /**
         * At least one step is currently in progress.
         */
        IN_PROGRESS,

        /**
         * Some steps are complete, but others are still pending or failed.
         */
        PARTIALLY_COMPLETED,

        /**
         * A critical step has failed, blocking further progress.
         */
        FAILED,

        /**
         * All required initialization steps have completed successfully.
         */
        COMPLETED
    }

    /**
     * Overall initialization status.
     */
    private OverallStatus overallStatus;

    /**
     * Whether the configuration anchor has been imported (prerequisite for all steps).
     */
    private boolean anchorImported;

    /**
     * Detailed status for each initialization step.
     */
    @Builder.Default
    private List<InitializationStepInfo> steps = new ArrayList<>();

    /**
     * List of steps that are pending (not started or failed and retryable).
     */
    @Builder.Default
    private List<InitializationStep> pendingSteps = new ArrayList<>();

    /**
     * List of steps that have failed.
     */
    @Builder.Default
    private List<InitializationStep> failedSteps = new ArrayList<>();

    /**
     * List of steps that have completed successfully.
     */
    @Builder.Default
    private List<InitializationStep> completedSteps = new ArrayList<>();

    /**
     * Whether the security server is fully initialized (all required steps completed).
     */
    private boolean fullyInitialized;

    /**
     * The security server ID if serverconf has been initialized.
     */
    private String securityServerId;

    /**
     * Whether token PIN policy is enforced.
     */
    private Boolean tokenPinPolicyEnforced;

    /**
     * Get the status info for a specific step.
     */
    public InitializationStepInfo getStepInfo(InitializationStep step) {
        return steps.stream()
                .filter(info -> info.getStep() == step)
                .findFirst()
                .orElse(null);
    }

    /**
     * Check if a specific step is completed.
     */
    public boolean isStepCompleted(InitializationStep step) {
        InitializationStepInfo info = getStepInfo(step);
        return info != null && info.getStatus() == InitializationStepStatus.COMPLETED;
    }

    /**
     * Check if a specific step can be executed (prerequisites met).
     */
    public boolean canExecuteStep(InitializationStep step) {
        if (!anchorImported) {
            return false;
        }
        for (InitializationStep prereq : step.getPrerequisites()) {
            if (!isStepCompleted(prereq)) {
                return false;
            }
        }
        return true;
    }
}
