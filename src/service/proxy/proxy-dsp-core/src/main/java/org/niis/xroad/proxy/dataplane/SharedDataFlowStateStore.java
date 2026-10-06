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

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.eclipse.edc.connector.dataplane.spi.DataFlowStates;
import org.hibernate.Session;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.serverconf.impl.ServerConfDatabaseCtx;
import org.niis.xroad.serverconf.impl.dao.DataFlowStateDAOImpl;
import org.niis.xroad.serverconf.model.DataFlowLifecycleState;

import java.util.Optional;

/**
 * {@link DataFlowStateStore} backed by the {@code dataflow_state} table in the serverconf
 * database, the database every proxy node of a clustered Security Server already shares.
 * A transition's legality is checked and its target state written inside one transaction that holds
 * the flow's row locked ({@code SELECT ... FOR UPDATE}), so a node that lost a race on the same flow
 * validates against the state the winner committed, never against what it read before the race.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class SharedDataFlowStateStore implements DataFlowStateStore {

    private final ServerConfDatabaseCtx databaseCtx;
    private final DataFlowStateDAOImpl dao = new DataFlowStateDAOImpl();

    @Override
    public DataFlowTransitionOutcome apply(String flowId, DataFlowTransition transition) {
        LockedTransition attempt;
        try {
            attempt = transitionInTransaction(flowId, transition);
        } catch (XrdRuntimeException e) {
            // A flow with no row yet cannot be locked, so two nodes can race on its first insert; the loser
            // hits uniq_dataflow_state_flow_id. If the row exists now, retry against it, which locks it.
            if (!find(flowId).isPresent()) {
                throw e;
            }
            attempt = transitionInTransaction(flowId, transition);
        }
        if (!attempt.applied()) {
            throw transition.illegalFrom(attempt.stateBefore());
        }
        return new DataFlowTransitionOutcome(attempt.stateBefore(), transition.targetState());
    }

    @Override
    public Optional<DataFlowStates> find(String flowId) {
        return databaseCtx.doInTransaction(session -> dao.findByFlowId(session, flowId))
                .map(entity -> toEdcState(entity.getState()));
    }

    private LockedTransition transitionInTransaction(String flowId, DataFlowTransition transition) {
        return databaseCtx.doInTransaction(session -> transitionLockedRow(session, flowId, transition));
    }

    private LockedTransition transitionLockedRow(Session session, String flowId, DataFlowTransition transition) {
        var row = dao.findByFlowIdForUpdate(session, flowId);
        var stateBefore = row.map(entity -> toEdcState(entity.getState()));
        if (!transition.isLegalFrom(stateBefore)) {
            return new LockedTransition(stateBefore, false);
        }
        var nextState = toLifecycleState(transition.targetState());
        row.ifPresentOrElse(entity -> entity.setState(nextState), () -> dao.insert(session, flowId, nextState));
        return new LockedTransition(stateBefore, true);
    }

    private static DataFlowStates toEdcState(DataFlowLifecycleState state) {
        return DataFlowStates.valueOf(state.name());
    }

    private static DataFlowLifecycleState toLifecycleState(DataFlowStates state) {
        return DataFlowLifecycleState.valueOf(state.name());
    }

    /** What one locked read-then-write found and did: the state before it, and whether it wrote the transition. */
    private record LockedTransition(Optional<DataFlowStates> stateBefore, boolean applied) {
    }

}
