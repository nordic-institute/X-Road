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
package org.niis.xroad.proxy.dataplane;

import org.eclipse.edc.connector.dataplane.spi.DataFlowStates;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.COMPLETED;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.PROVISIONED;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.STARTED;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.SUSPENDED;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.TERMINATED;

class DataFlowTransitionTest {

    @Test
    void prepareIsLegalOnlyFromUnknownFlow() {
        assertThat(DataFlowTransition.PREPARE.apply(Optional.empty())).isEqualTo(PROVISIONED);

        for (var state : DataFlowStates.values()) {
            assertIllegal(DataFlowTransition.PREPARE, state);
        }
    }

    @Test
    void startIsLegalFromUnknownFlowOrProvisioned() {
        assertThat(DataFlowTransition.START.apply(Optional.empty())).isEqualTo(STARTED);
        assertThat(DataFlowTransition.START.apply(Optional.of(PROVISIONED))).isEqualTo(STARTED);
    }

    @Test
    void startOnAnythingElseIsIllegal() {
        assertIllegal(DataFlowTransition.START, STARTED);
        assertIllegal(DataFlowTransition.START, SUSPENDED);
        assertIllegal(DataFlowTransition.START, COMPLETED);
        assertIllegal(DataFlowTransition.START, TERMINATED);
    }

    @Test
    void notifyStartedIsLegalFromUnknownProvisionedOrStartedAndIsNotReported() {
        assertThat(DataFlowTransition.NOTIFY_STARTED.apply(Optional.empty())).isEqualTo(STARTED);
        assertThat(DataFlowTransition.NOTIFY_STARTED.apply(Optional.of(PROVISIONED))).isEqualTo(STARTED);
        assertThat(DataFlowTransition.NOTIFY_STARTED.apply(Optional.of(STARTED))).isEqualTo(STARTED);
        assertThat(DataFlowTransition.NOTIFY_STARTED.isReported()).isFalse();

        assertIllegal(DataFlowTransition.NOTIFY_STARTED, SUSPENDED);
        assertIllegal(DataFlowTransition.NOTIFY_STARTED, COMPLETED);
        assertIllegal(DataFlowTransition.NOTIFY_STARTED, TERMINATED);
    }

    @Test
    void suspendIsLegalOnlyFromStarted() {
        assertThat(DataFlowTransition.SUSPEND.apply(Optional.of(STARTED))).isEqualTo(SUSPENDED);

        assertIllegalFromUnknown(DataFlowTransition.SUSPEND);
        assertIllegal(DataFlowTransition.SUSPEND, PROVISIONED);
        assertIllegal(DataFlowTransition.SUSPEND, SUSPENDED);
        assertIllegal(DataFlowTransition.SUSPEND, COMPLETED);
        assertIllegal(DataFlowTransition.SUSPEND, TERMINATED);
    }

    @Test
    void resumeIsLegalOnlyFromSuspended() {
        assertThat(DataFlowTransition.RESUME.apply(Optional.of(SUSPENDED))).isEqualTo(STARTED);

        assertIllegalFromUnknown(DataFlowTransition.RESUME);
        assertIllegal(DataFlowTransition.RESUME, PROVISIONED);
        assertIllegal(DataFlowTransition.RESUME, STARTED);
        assertIllegal(DataFlowTransition.RESUME, COMPLETED);
        assertIllegal(DataFlowTransition.RESUME, TERMINATED);
    }

    @Test
    void completeIsLegalOnlyFromStarted() {
        assertThat(DataFlowTransition.COMPLETE.apply(Optional.of(STARTED))).isEqualTo(COMPLETED);

        assertIllegalFromUnknown(DataFlowTransition.COMPLETE);
        assertIllegal(DataFlowTransition.COMPLETE, PROVISIONED);
        assertIllegal(DataFlowTransition.COMPLETE, SUSPENDED);
        assertIllegal(DataFlowTransition.COMPLETE, COMPLETED);
        assertIllegal(DataFlowTransition.COMPLETE, TERMINATED);
    }

    @Test
    void terminateIsLegalFromAnyActiveState() {
        assertThat(DataFlowTransition.TERMINATE.apply(Optional.of(PROVISIONED))).isEqualTo(TERMINATED);
        assertThat(DataFlowTransition.TERMINATE.apply(Optional.of(STARTED))).isEqualTo(TERMINATED);
        assertThat(DataFlowTransition.TERMINATE.apply(Optional.of(SUSPENDED))).isEqualTo(TERMINATED);

        assertIllegalFromUnknown(DataFlowTransition.TERMINATE);
        assertIllegal(DataFlowTransition.TERMINATE, COMPLETED);
        assertIllegal(DataFlowTransition.TERMINATE, TERMINATED);
    }

    @ParameterizedTest
    @EnumSource(DataFlowTransition.class)
    void terminalStatesRejectEveryTransition(DataFlowTransition transition) {
        assertIllegal(transition, COMPLETED);
        assertIllegal(transition, TERMINATED);
    }

    @Test
    void reportedFlagMatchesTheDataFlowTable() {
        assertThat(DataFlowTransition.PREPARE.isReported()).isTrue();
        assertThat(DataFlowTransition.START.isReported()).isTrue();
        assertThat(DataFlowTransition.RESUME.isReported()).isTrue();
        assertThat(DataFlowTransition.COMPLETE.isReported()).isTrue();
        assertThat(DataFlowTransition.SUSPEND.isReported()).isFalse();
        assertThat(DataFlowTransition.TERMINATE.isReported()).isFalse();
    }

    private static void assertIllegalFromUnknown(DataFlowTransition transition) {
        assertThatThrownBy(() -> transition.apply(Optional.empty()))
                .isInstanceOf(XrdRuntimeException.class);
    }

    private static void assertIllegal(DataFlowTransition transition, DataFlowStates from) {
        assertThatThrownBy(() -> transition.apply(Optional.of(from)))
                .isInstanceOf(XrdRuntimeException.class);
    }
}
