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
package org.niis.xroad.serverconf.impl.dao;

import jakarta.persistence.LockModeType;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.niis.xroad.common.jpa.dao.AbstractDAOImpl;
import org.niis.xroad.serverconf.impl.entity.DataFlowStateEntity;
import org.niis.xroad.serverconf.model.DataFlowLifecycleState;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Data access object for shared proxy data-plane flow state. It defines no lifecycle semantics: the caller
 * decides which state follows which.
 */
public class DataFlowStateDAOImpl extends AbstractDAOImpl<DataFlowStateEntity> {

    private static final List<String> FINISHED_STATES = List.of(
            DataFlowLifecycleState.COMPLETED.name(), DataFlowLifecycleState.TERMINATED.name());

    /**
     * Finds the current state row for a flow.
     *
     * @param session the Hibernate session
     * @param flowId  the flow's process ID
     * @return the current row, if the flow is known
     */
    public Optional<DataFlowStateEntity> findByFlowId(Session session, String flowId) {
        return queryByFlowId(session, flowId).uniqueResultOptional();
    }

    /**
     * Finds the current state row for a flow and locks it ({@code SELECT ... FOR UPDATE}) for the rest of the
     * caller's transaction, so concurrent updates to the same {@code flow_id} serialize. A nonexistent row
     * cannot be locked; the first insert for a new {@code flow_id} is guarded only by the unique constraint.
     *
     * @param session the Hibernate session
     * @param flowId  the flow's process ID
     * @return the current row, locked, if the flow is known
     */
    public Optional<DataFlowStateEntity> findByFlowIdForUpdate(Session session, String flowId) {
        return queryByFlowId(session, flowId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .uniqueResultOptional();
    }

    /**
     * Creates the state row for a flow that has none yet.
     *
     * @param session the Hibernate session
     * @param flowId  the flow's process ID
     * @param state   the flow's first state
     * @return the new, managed row
     */
    public DataFlowStateEntity insert(Session session, String flowId, DataFlowLifecycleState state) {
        var entity = new DataFlowStateEntity();
        entity.setFlowId(flowId);
        entity.setState(state);
        session.persist(entity);
        return entity;
    }

    /**
     * Deletes the rows of flows that finished ({@code COMPLETED} or {@code TERMINATED}) and were last updated
     * longer ago than {@code finishedAge}, and the rows in any state last updated longer ago than {@code maxAge}.
     * The age is measured on the database clock against {@code updated_at}, so it does not depend on the
     * caller's clock. Rows locked by a concurrent transition or a concurrent caller are skipped, so each row is
     * deleted by exactly one caller. PostgreSQL only.
     *
     * @param session     the Hibernate session
     * @param finishedAge how long a finished flow's row is kept
     * @param maxAge      how long a row in any state is kept
     * @return the number of rows deleted
     */
    public int deleteOlderThan(Session session, Duration finishedAge, Duration maxAge) {
        return session.createNativeMutationQuery("""
                        DELETE FROM dataflow_state WHERE id IN (
                            SELECT id FROM dataflow_state
                            WHERE (state IN (:finishedStates)
                                   AND updated_at < CURRENT_TIMESTAMP - CAST(:finishedSeconds AS bigint) * INTERVAL '1 second')
                               OR updated_at < CURRENT_TIMESTAMP - CAST(:maxSeconds AS bigint) * INTERVAL '1 second'
                            ORDER BY id
                            FOR UPDATE SKIP LOCKED)
                        """)
                .setParameterList("finishedStates", FINISHED_STATES)
                .setParameter("finishedSeconds", finishedAge.toSeconds())
                .setParameter("maxSeconds", maxAge.toSeconds())
                .executeUpdate();
    }

    private Query<DataFlowStateEntity> queryByFlowId(Session session, String flowId) {
        var cb = session.getCriteriaBuilder();
        var query = cb.createQuery(DataFlowStateEntity.class);
        var root = query.from(DataFlowStateEntity.class);
        query.select(root).where(cb.equal(root.get("flowId"), flowId));
        return session.createQuery(query);
    }

}
