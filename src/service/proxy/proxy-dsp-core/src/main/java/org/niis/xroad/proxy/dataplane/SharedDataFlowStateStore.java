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
import org.eclipse.edc.spi.result.StoreResult;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.serverconf.impl.ServerConfDatabaseCtx;
import org.niis.xroad.serverconf.impl.dao.DataFlowStateDAOImpl;
import org.niis.xroad.serverconf.impl.entity.DataFlowStateEntity;
import org.niis.xroad.serverconf.model.DataFlowLifecycleState;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * {@link DataFlowStateStore} backed by the {@code dataflow_state} table in the serverconf
 * database, the database every proxy node of a clustered Security Server already shares.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class SharedDataFlowStateStore implements DataFlowStateStore {

    /** States {@link XRoadDataPlaneManager} never transitions out of. */
    private static final Set<DataFlowLifecycleState> TERMINAL_STATES =
            EnumSet.of(DataFlowLifecycleState.COMPLETED, DataFlowLifecycleState.TERMINATED);

    private final ServerConfDatabaseCtx databaseCtx;
    private final DataFlowStateDAOImpl dao = new DataFlowStateDAOImpl();

    @Override
    public StoreResult<Void> save(String flowId, DataFlowLifecycleState state) {
        try {
            upsert(flowId, state);
        } catch (XrdRuntimeException e) {
            // Two nodes can race on the first insert for a new flowId; the loser hits
            // uniq_dataflow_state_flow_id. If the row exists now, retry via the update branch;
            // otherwise this is a genuine failure.
            if (!rowExists(flowId)) {
                throw e;
            }
            upsert(flowId, state);
        }
        return StoreResult.success();
    }

    private void upsert(String flowId, DataFlowLifecycleState state) {
        databaseCtx.doInTransaction(session -> {
            dao.upsertState(session, flowId, state, SharedDataFlowStateStore::isTransitionAllowed);
            return null;
        });
    }

    private boolean rowExists(String flowId) {
        return databaseCtx.doInTransaction(session -> dao.findByFlowId(session, flowId)).isPresent();
    }

    /**
     * A terminal state never transitions again; otherwise a move is allowed only if it does not
     * regress {@link #lifecycleRank}, dropping a write that lost the race to a more advanced state.
     */
    private static boolean isTransitionAllowed(DataFlowLifecycleState current, DataFlowLifecycleState next) {
        if (current == next) {
            return true;
        }
        if (TERMINAL_STATES.contains(current)) {
            return false;
        }
        return lifecycleRank(next) >= lifecycleRank(current);
    }

    /**
     * {@code SUSPENDED} ranks as a side branch of {@code STARTED}, not a step beyond it, so a
     * legitimate completion arriving after a suspend is not blocked.
     */
    private static int lifecycleRank(DataFlowLifecycleState state) {
        return switch (state) {
            case PROVISIONED -> 0;
            case STARTED, SUSPENDED -> 1;
            case COMPLETED, TERMINATED, FAILED -> 2;
        };
    }

    @Override
    public Optional<DataFlowLifecycleState> find(String flowId) {
        return databaseCtx.doInTransaction(session -> dao.findByFlowId(session, flowId))
                .map(DataFlowStateEntity::getState);
    }

}
