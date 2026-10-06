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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.COMPLETED;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.PROVISIONED;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.STARTED;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.SUSPENDED;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.TERMINATED;
import static org.niis.xroad.proxy.dataplane.DataFlowTransition.COMPLETE;
import static org.niis.xroad.proxy.dataplane.DataFlowTransition.NOTIFY_STARTED;
import static org.niis.xroad.proxy.dataplane.DataFlowTransition.PREPARE;
import static org.niis.xroad.proxy.dataplane.DataFlowTransition.START;
import static org.niis.xroad.proxy.dataplane.DataFlowTransition.SUSPEND;
import static org.niis.xroad.proxy.dataplane.DataFlowTransition.TERMINATE;

class DataFlowTransitionTest {

    @Test
    void prepareIsLegalFromUnknownFlowAndAsARepeatOnAProvisionedOne() {
        assertThat(PREPARE.apply(Optional.empty())).isEqualTo(PROVISIONED);
        assertThat(PREPARE.apply(Optional.of(PROVISIONED))).isEqualTo(PROVISIONED);

        assertIllegal(PREPARE, STARTED);
        assertIllegal(PREPARE, SUSPENDED);
        assertIllegal(PREPARE, COMPLETED);
        assertIllegal(PREPARE, TERMINATED);
    }

    @Test
    void startIsLegalFromUnknownProvisionedOrSuspendedAndAsARepeatOnAStartedFlow() {
        assertThat(START.apply(Optional.empty())).isEqualTo(STARTED);
        assertThat(START.apply(Optional.of(PROVISIONED))).isEqualTo(STARTED);
        assertThat(START.apply(Optional.of(SUSPENDED))).isEqualTo(STARTED);
        assertThat(START.apply(Optional.of(STARTED))).isEqualTo(STARTED);
    }

    @Test
    void startOnATerminalFlowIsIllegal() {
        assertIllegal(START, COMPLETED);
        assertIllegal(START, TERMINATED);
    }

    @Test
    void notifyStartedIsLegalFromUnknownProvisionedOrStartedAndIsNotReported() {
        assertThat(NOTIFY_STARTED.apply(Optional.empty())).isEqualTo(STARTED);
        assertThat(NOTIFY_STARTED.apply(Optional.of(PROVISIONED))).isEqualTo(STARTED);
        assertThat(NOTIFY_STARTED.apply(Optional.of(STARTED))).isEqualTo(STARTED);
        assertThat(NOTIFY_STARTED.isReported()).isFalse();

        assertIllegal(NOTIFY_STARTED, SUSPENDED);
        assertIllegal(NOTIFY_STARTED, COMPLETED);
        assertIllegal(NOTIFY_STARTED, TERMINATED);
    }

    @Test
    void suspendIsLegalFromStartedAndAsARepeatOnASuspendedFlow() {
        assertThat(SUSPEND.apply(Optional.of(STARTED))).isEqualTo(SUSPENDED);
        assertThat(SUSPEND.apply(Optional.of(SUSPENDED))).isEqualTo(SUSPENDED);

        assertIllegalFromUnknown(SUSPEND);
        assertIllegal(SUSPEND, PROVISIONED);
        assertIllegal(SUSPEND, COMPLETED);
        assertIllegal(SUSPEND, TERMINATED);
    }

    @Test
    void completeIsLegalFromStartedAndAsARepeatOnACompletedFlow() {
        assertThat(COMPLETE.apply(Optional.of(STARTED))).isEqualTo(COMPLETED);
        assertThat(COMPLETE.apply(Optional.of(COMPLETED))).isEqualTo(COMPLETED);

        assertIllegalFromUnknown(COMPLETE);
        assertIllegal(COMPLETE, PROVISIONED);
        assertIllegal(COMPLETE, SUSPENDED);
        assertIllegal(COMPLETE, TERMINATED);
    }

    @Test
    void terminateIsLegalFromAnyActiveStateAndAsARepeatOnATerminatedFlow() {
        assertThat(TERMINATE.apply(Optional.of(PROVISIONED))).isEqualTo(TERMINATED);
        assertThat(TERMINATE.apply(Optional.of(STARTED))).isEqualTo(TERMINATED);
        assertThat(TERMINATE.apply(Optional.of(SUSPENDED))).isEqualTo(TERMINATED);
        assertThat(TERMINATE.apply(Optional.of(TERMINATED))).isEqualTo(TERMINATED);

        assertIllegalFromUnknown(TERMINATE);
        assertIllegal(TERMINATE, COMPLETED);
    }

    @ParameterizedTest
    @EnumSource(DataFlowTransition.class)
    void terminalStatesRejectEveryTransitionExceptTheRepeatOfTheirOwn(DataFlowTransition transition) {
        for (var terminal : List.of(COMPLETED, TERMINATED)) {
            if (transition.targetState() == terminal) {
                assertThat(transition.apply(Optional.of(terminal))).isEqualTo(terminal);
            } else {
                assertIllegal(transition, terminal);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(DataFlowTransition.class)
    void applyThrowsExactlyWhereIsLegalFromSaysNo(DataFlowTransition transition) {
        assertThat(transition.isLegalFrom(Optional.empty())).isEqualTo(applies(transition, Optional.empty()));
        for (var state : DataFlowStates.values()) {
            assertThat(transition.isLegalFrom(Optional.of(state))).isEqualTo(applies(transition, Optional.of(state)));
        }
    }

    @Test
    void reportedFlagMatchesTheDataFlowTable() {
        assertThat(PREPARE.isReported()).isTrue();
        assertThat(START.isReported()).isTrue();
        assertThat(COMPLETE.isReported()).isTrue();
        assertThat(NOTIFY_STARTED.isReported()).isFalse();
        assertThat(SUSPEND.isReported()).isFalse();
        assertThat(TERMINATE.isReported()).isFalse();
    }

    private static boolean applies(DataFlowTransition transition, Optional<DataFlowStates> from) {
        try {
            transition.apply(from);
            return true;
        } catch (XrdRuntimeException e) {
            return false;
        }
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
