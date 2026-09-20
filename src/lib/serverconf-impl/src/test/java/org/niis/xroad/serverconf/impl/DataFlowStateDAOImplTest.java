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
import org.junit.AfterClass;
import org.junit.Test;
import org.niis.xroad.serverconf.impl.dao.DataFlowStateDAOImpl;
import org.niis.xroad.serverconf.impl.entity.DataFlowStateEntity;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the {@code dataflow_state} table: the shared record every proxy node of a clustered
 * Security Server reads and writes.
 */
public class DataFlowStateDAOImplTest {

    private static final DatabaseCtx DATABASE_CTX = new ServerConfDatabaseCtx(TestUtil.serverConfDbProperties);

    private final DataFlowStateDAOImpl dao = new DataFlowStateDAOImpl();

    @AfterClass
    public static void afterClass() {
        DATABASE_CTX.destroy();
    }

    @Test
    public void findByFlowIdIsEmptyForUnknownFlow() {
        Optional<?> found = DATABASE_CTX.doInTransaction(session -> dao.findByFlowId(session, "unknown-flow"));

        assertFalse(found.isPresent());
    }

    @Test
    public void upsertStateCreatesRowWhenAbsent() {
        DATABASE_CTX.doInTransaction(session -> {
            dao.upsertState(session, "flow-created", "STARTED");
            return null;
        });

        Optional<?> found = DATABASE_CTX.doInTransaction(session -> dao.findByFlowId(session, "flow-created"));

        assertTrue(found.isPresent());
    }

    @Test
    public void upsertStateOverwritesExistingRowInsteadOfCreatingASecondOne() {
        DATABASE_CTX.doInTransaction(session -> {
            dao.upsertState(session, "flow-lifecycle", "PROVISIONED");
            return null;
        });
        DATABASE_CTX.doInTransaction(session -> {
            dao.upsertState(session, "flow-lifecycle", "STARTED");
            return null;
        });
        DATABASE_CTX.doInTransaction(session -> {
            dao.upsertState(session, "flow-lifecycle", "TERMINATED");
            return null;
        });

        var found = DATABASE_CTX.doInTransaction(session -> dao.findByFlowId(session, "flow-lifecycle"));

        assertTrue(found.isPresent());
        assertEquals("TERMINATED", found.get().getState());

        long rowCount = DATABASE_CTX.doInTransaction(session ->
                session.createQuery("select count(e) from DataFlowStateEntity e where e.flowId = :flowId", Long.class)
                        .setParameter("flowId", "flow-lifecycle")
                        .getSingleResult());
        assertEquals(1L, rowCount);
    }

    /**
     * {@code upsertState} never inserts a second row for a known {@code flow_id} (previous test), but this
     * proves the backstop for the case that matters most for XRDDEV-3341's shared-store guarantee: two proxy
     * nodes racing to persist the same brand-new flow. Both would find no existing row and both would insert;
     * the {@code uniq_dataflow_state_flow_id} constraint from the changelog is what stops that race from ever
     * leaving two rows for the same flow.
     */
    @Test
    public void rejectsDuplicateFlowIdInsertedDirectly() {
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
        entity.setState("STARTED");
        return entity;
    }

}
