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
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.util.Optional;

/**
 * Stores the lifecycle state of proxy data-plane flows, shared by every proxy node of a
 * clustered Security Server.
 */
public interface DataFlowStateStore {

    /**
     * Applies a transition to a flow as one operation: the current state is read and the new state written
     * under one lock on the flow's record, so transitions racing on the same flow — from two threads or two
     * nodes — serialize, and each is validated against what the previous one committed rather than against a
     * stale read.
     *
     * @param flowId     the flow's process ID
     * @param transition the transition to apply
     * @return the state the flow holds afterwards
     * @throws XrdRuntimeException if the transition is illegal from the flow's current state; nothing is written
     */
    DataFlowStates apply(String flowId, DataFlowTransition transition);

    /**
     * Finds the current state of a flow.
     *
     * @param flowId the flow's process ID
     * @return the current state, or empty if the flow is not known
     */
    Optional<DataFlowStates> find(String flowId);

}
