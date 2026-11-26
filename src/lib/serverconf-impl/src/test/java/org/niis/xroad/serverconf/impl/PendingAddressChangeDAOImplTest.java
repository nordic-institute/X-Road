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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.serverconf.impl.dao.PendingAddressChangeDAOImpl;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingAddressChangeDAOImplTest {

    private static final DatabaseCtx DATABASE_CTX = new ServerConfDatabaseCtx(TestUtil.serverConfDbProperties);
    private static final String ADDRESS = "new.example.org";

    private final PendingAddressChangeDAOImpl dao = new PendingAddressChangeDAOImpl();

    @BeforeEach
    void emptyTable() {
        DATABASE_CTX.doInTransaction(dao::delete);
    }

    @AfterAll
    static void afterAll() {
        DATABASE_CTX.destroy();
    }

    @Test
    void findIsEmptyWhenNothingIsStored() {
        assertFalse(DATABASE_CTX.doInTransaction(dao::find).isPresent());
    }

    @Test
    void savedRecordSurvivesReload() {
        var requestedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        DATABASE_CTX.doInTransaction(session -> dao.save(session, ADDRESS, requestedAt));

        var found = DATABASE_CTX.doInTransaction(dao::find);

        assertTrue(found.isPresent());
        assertEquals(ADDRESS, found.get().getRequestedAddress());
        assertEquals(requestedAt, found.get().getRequestedAt());
    }

    @Test
    void secondSaveIsRefusedAndTheStoredRecordIsUnchanged() {
        DATABASE_CTX.doInTransaction(session -> dao.save(session, ADDRESS, Instant.now()));

        assertThrows(XrdRuntimeException.class,
                () -> DATABASE_CTX.doInTransaction(session -> dao.save(session, "other.example.org", Instant.now())));

        assertEquals(ADDRESS, DATABASE_CTX.doInTransaction(dao::find).orElseThrow().getRequestedAddress());
        long rowCount = DATABASE_CTX.doInTransaction(session ->
                session.createQuery("select count(e) from PendingAddressChangeEntity e", Long.class).getSingleResult());
        assertEquals(1L, rowCount);
    }

    @Test
    void deleteLeavesTheTableEmpty() {
        DATABASE_CTX.doInTransaction(session -> dao.save(session, ADDRESS, Instant.now()));

        assertTrue(DATABASE_CTX.doInTransaction(dao::delete));

        assertFalse(DATABASE_CTX.doInTransaction(dao::find).isPresent());
    }

    @Test
    void secondDeleteChangesNothing() {
        DATABASE_CTX.doInTransaction(session -> dao.save(session, ADDRESS, Instant.now()));
        DATABASE_CTX.doInTransaction(dao::delete);

        assertFalse(DATABASE_CTX.doInTransaction(dao::delete));

        assertFalse(DATABASE_CTX.doInTransaction(dao::find).isPresent());
    }

}
