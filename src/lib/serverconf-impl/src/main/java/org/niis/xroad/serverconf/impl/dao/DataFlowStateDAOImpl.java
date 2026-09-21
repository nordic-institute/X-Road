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

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.hibernate.Session;
import org.niis.xroad.common.jpa.dao.AbstractDAOImpl;
import org.niis.xroad.serverconf.impl.entity.DataFlowStateEntity;

import java.util.Optional;
import java.util.function.BiPredicate;

/**
 * Data access object for shared proxy data-plane flow state.
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
        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<DataFlowStateEntity> query = cb.createQuery(DataFlowStateEntity.class);
        final Root<DataFlowStateEntity> root = query.from(DataFlowStateEntity.class);

        query.select(root).where(cb.equal(root.get("flowId"), flowId));

        return session.createQuery(query).uniqueResultOptional();
    }

    /**
     * Creates the state row for a flow, or updates it via {@code transitionAllowed} if one exists.
     * The DAO has no notion of what a "state" or a valid transition is — that judgment is the
     * caller's, passed in as a predicate, so this module stays decoupled from any state-machine semantics.
     *
     * @param session           the Hibernate session
     * @param flowId            the flow's process ID
     * @param state             the new state
     * @param transitionAllowed tested against (current state, new state); ignored when creating a new row
     * @return {@code true} if the row was created or updated, {@code false} if the transition was rejected
     */
    public boolean upsertState(Session session, String flowId, String state, BiPredicate<String, String> transitionAllowed) {
        var existing = findByFlowId(session, flowId);
        if (existing.isPresent()) {
            var entity = existing.get();
            if (!transitionAllowed.test(entity.getState(), state)) {
                return false;
            }
            entity.setState(state);
        } else {
            var entity = new DataFlowStateEntity();
            entity.setFlowId(flowId);
            entity.setState(state);
            session.persist(entity);
        }
        return true;
    }

}
