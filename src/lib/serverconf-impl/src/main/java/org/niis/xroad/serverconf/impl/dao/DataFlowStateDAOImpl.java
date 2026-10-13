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

import java.util.Optional;

/**
 * Data access object for shared proxy data-plane flow state. It defines no lifecycle semantics: the caller
 * decides which state follows which.
 */
public class DataFlowStateDAOImpl extends AbstractDAOImpl<DataFlowStateEntity> {

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

    private Query<DataFlowStateEntity> queryByFlowId(Session session, String flowId) {
        var cb = session.getCriteriaBuilder();
        var query = cb.createQuery(DataFlowStateEntity.class);
        var root = query.from(DataFlowStateEntity.class);
        query.select(root).where(cb.equal(root.get("flowId"), flowId));
        return session.createQuery(query);
    }

}
