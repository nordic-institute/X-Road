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
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.COMPLETED;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.PROVISIONED;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.STARTED;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.SUSPENDED;
import static org.eclipse.edc.connector.dataplane.spi.DataFlowStates.TERMINATED;

/**
 * The provider data plane's lifecycle state machine, one entry per signal of the data-flow signaling API.
 * Each transition names the state it lands on, which predecessor states may legally reach it, and whether an
 * unknown (never-seen) flow id is itself a legal predecessor — true only for the two signals that originate a
 * flow. Landing on {@link DataFlowStates#PROVISIONED}, {@link DataFlowStates#STARTED} or
 * {@link DataFlowStates#COMPLETED} is reported to the control plane; {@link DataFlowStates#SUSPENDED} and
 * {@link DataFlowStates#TERMINATED} are not.
 *
 * <p>{@code PROVISIONED} is this runtime's EDC state for what the data-flow table calls {@code PREPARED}; no
 * separate {@code PREPARED} constant exists in {@link DataFlowStates}.
 */
public enum DataFlowTransition {

    PREPARE(PROVISIONED, true, EnumSet.noneOf(DataFlowStates.class), true),
    START(STARTED, true, EnumSet.of(PROVISIONED), true),
    /** The control plane's own started notification to this data plane; recorded, never reported back. */
    NOTIFY_STARTED(STARTED, true, EnumSet.of(PROVISIONED, STARTED), false),
    SUSPEND(SUSPENDED, false, EnumSet.of(STARTED), false),
    RESUME(STARTED, false, EnumSet.of(SUSPENDED), true),
    COMPLETE(COMPLETED, false, EnumSet.of(STARTED), true),
    TERMINATE(TERMINATED, false, EnumSet.of(PROVISIONED, STARTED, SUSPENDED), false);

    private final DataFlowStates targetState;
    private final boolean legalFromUnknownFlow;
    private final Set<DataFlowStates> legalPredecessors;
    private final boolean reported;

    DataFlowTransition(DataFlowStates targetState, boolean legalFromUnknownFlow,
                        Set<DataFlowStates> legalPredecessors, boolean reported) {
        this.targetState = targetState;
        this.legalFromUnknownFlow = legalFromUnknownFlow;
        this.legalPredecessors = legalPredecessors;
        this.reported = reported;
    }

    /**
     * @return the state a flow is in once this transition succeeds
     */
    public DataFlowStates targetState() {
        return targetState;
    }

    /**
     * @return whether landing on {@link #targetState()} is reported to the control plane
     */
    public boolean isReported() {
        return reported;
    }

    /**
     * Validates this transition against a flow's current state.
     *
     * @param currentState the flow's current state, or empty for a flow id never seen before
     * @return {@link #targetState()}
     * @throws XrdRuntimeException if {@code currentState} is not a legal predecessor of this transition
     */
    public DataFlowStates apply(Optional<DataFlowStates> currentState) {
        var legal = currentState.map(legalPredecessors::contains).orElse(legalFromUnknownFlow);
        if (!legal) {
            throw XrdRuntimeException.systemException(ErrorCode.INVALID_REQUEST)
                    .details("Cannot apply transition %s to a data flow in state %s"
                            .formatted(this, currentState.map(Enum::name).orElse("UNKNOWN")))
                    .build();
        }
        return targetState;
    }
}
