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
package org.niis.xroad.serverconf.impl;

import ee.ria.xroad.common.db.DatabaseCtx;

import org.hibernate.Session;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.niis.xroad.serverconf.impl.dao.DataFlowStateDAOImpl;
import org.niis.xroad.serverconf.impl.entity.DataFlowStateEntity;
import org.niis.xroad.serverconf.model.DataFlowLifecycleState;

import java.util.Optional;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataFlowStateDAOImplTest {

    private static final BiPredicate<DataFlowLifecycleState, DataFlowLifecycleState> ALWAYS_ALLOWED = (current, next) -> true;

    private static final DatabaseCtx DATABASE_CTX = new ServerConfDatabaseCtx(TestUtil.serverConfDbProperties);

    private final DataFlowStateDAOImpl dao = new DataFlowStateDAOImpl();

    @AfterAll
    static void afterAll() {
        DATABASE_CTX.destroy();
    }

    @Test
    void findByFlowIdIsEmptyForUnknownFlow() {
        Optional<?> found = DATABASE_CTX.doInTransaction(session -> dao.findByFlowId(session, "unknown-flow"));

        assertFalse(found.isPresent());
    }

    @Test
    void upsertStateCreatesRowWhenAbsent() {
        DATABASE_CTX.doInTransaction(session -> {
            dao.upsertState(session, "flow-created", DataFlowLifecycleState.STARTED, ALWAYS_ALLOWED);
            return null;
        });

        Optional<?> found = DATABASE_CTX.doInTransaction(session -> dao.findByFlowId(session, "flow-created"));

        assertTrue(found.isPresent());
    }

    @Test
    void upsertStateOverwritesExistingRowInsteadOfCreatingASecondOne() {
        DATABASE_CTX.doInTransaction(session -> {
            dao.upsertState(session, "flow-lifecycle", DataFlowLifecycleState.PROVISIONED, ALWAYS_ALLOWED);
            return null;
        });
        DATABASE_CTX.doInTransaction(session -> {
            dao.upsertState(session, "flow-lifecycle", DataFlowLifecycleState.STARTED, ALWAYS_ALLOWED);
            return null;
        });
        DATABASE_CTX.doInTransaction(session -> {
            dao.upsertState(session, "flow-lifecycle", DataFlowLifecycleState.TERMINATED, ALWAYS_ALLOWED);
            return null;
        });

        var found = DATABASE_CTX.doInTransaction(session -> dao.findByFlowId(session, "flow-lifecycle"));

        assertTrue(found.isPresent());
        assertEquals(DataFlowLifecycleState.TERMINATED, found.get().getState());

        long rowCount = DATABASE_CTX.doInTransaction(session ->
                session.createQuery("select count(e) from DataFlowStateEntity e where e.flowId = :flowId", Long.class)
                        .setParameter("flowId", "flow-lifecycle")
                        .getSingleResult());
        assertEquals(1L, rowCount);
    }

    @Test
    void upsertStateReturnsTrueWhenTransitionAllowedAndAppliesIt() {
        DATABASE_CTX.doInTransaction(session -> {
            dao.upsertState(session, "flow-guard-allow", DataFlowLifecycleState.PROVISIONED, ALWAYS_ALLOWED);
            return null;
        });

        boolean applied = DATABASE_CTX.doInTransaction(session ->
                dao.upsertState(session, "flow-guard-allow", DataFlowLifecycleState.STARTED, ALWAYS_ALLOWED));

        assertTrue(applied);
        var found = DATABASE_CTX.doInTransaction(session -> dao.findByFlowId(session, "flow-guard-allow"));
        assertEquals(DataFlowLifecycleState.STARTED, found.get().getState());
    }

    @Test
    void upsertStateReturnsFalseAndLeavesRowUnchangedWhenTransitionRejected() {
        DATABASE_CTX.doInTransaction(session -> {
            dao.upsertState(session, "flow-guard-reject", DataFlowLifecycleState.STARTED, ALWAYS_ALLOWED);
            return null;
        });

        boolean applied = DATABASE_CTX.doInTransaction(session ->
                dao.upsertState(session, "flow-guard-reject", DataFlowLifecycleState.PROVISIONED, (current, next) -> false));

        assertFalse(applied);
        var found = DATABASE_CTX.doInTransaction(session -> dao.findByFlowId(session, "flow-guard-reject"));
        assertEquals(DataFlowLifecycleState.STARTED, found.get().getState());
    }

    @Test
    void upsertStateAppliesEvenWhenGuardRejectsForANewRowSinceTheGuardOnlyAppliesToUpdates() {
        boolean applied = DATABASE_CTX.doInTransaction(session ->
                dao.upsertState(session, "flow-guard-new-row", DataFlowLifecycleState.STARTED, (current, next) -> false));

        assertTrue(applied);
        var found = DATABASE_CTX.doInTransaction(session -> dao.findByFlowId(session, "flow-guard-new-row"));
        assertEquals(DataFlowLifecycleState.STARTED, found.get().getState());
    }

    /**
     * Two proxy nodes racing to persist the same brand-new flow would both find no existing row and
     * both insert; {@code uniq_dataflow_state_flow_id} is what stops that from leaving two rows.
     */
    @Test
    void rejectsDuplicateFlowIdInsertedDirectly() {
        Session session = DATABASE_CTX.beginTransaction();
        try {
            session.persist(newEntity("flow-race"));
            session.flush();

            assertThrows(ConstraintViolationException.class, () -> {
                session.persist(newEntity("flow-race"));
                session.flush();
            });
        } finally {
            DATABASE_CTX.rollbackTransaction();
        }
    }

    private static DataFlowStateEntity newEntity(String flowId) {
        var entity = new DataFlowStateEntity();
        entity.setFlowId(flowId);
        entity.setState(DataFlowLifecycleState.STARTED);
        return entity;
    }

}
