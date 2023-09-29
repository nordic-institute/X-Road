/*
 * The MIT License
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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.conf.serverconf.model.LocalGroupType;

import org.junit.Test;
import org.niis.xroad.securityserver.restapi.config.AbstractFacadeMockingTestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * test that transactions roll back as they should
 * @Transactional(propagation = Propagation.NEVER) settings makes it so that the
 * test method execution is not executed in a transaction, but each service method execution is
 * (otherwise we could not detect a transaction rollback from the test method).
 */
@Transactional(propagation = Propagation.NEVER)
public class TransactionRollbackIntegrationTest extends AbstractFacadeMockingTestContext {

    private static final Long GROUP_ID = 1L;

    @Autowired
    private LocalGroupService localGroupService;

    @Autowired
    private DummyTransactionRollingbackService dummyTransactionRollingbackService;

    @Test
    public void checkedExceptionRollsBackTransaction() throws Exception {
        LocalGroupType localGroupType = localGroupService.getLocalGroup(GROUP_ID);
        String originalDescription = localGroupType.getDescription();
        try {
            dummyTransactionRollingbackService.updateDescriptionAndRollback(GROUP_ID,
                    originalDescription + "_UPDATED",
                    DummyTransactionRollingbackService.ExceptionType.EXCEPTION);
            fail("should throw exception");
        } catch (Exception expected) {
        }

        LocalGroupType updatedGroup = localGroupService.getLocalGroup(GROUP_ID);
        assertEquals(originalDescription, updatedGroup.getDescription());
    }

    @Test
    public void checkedServiceExceptionRollsBackTransaction() throws Exception {
        LocalGroupType localGroupType = localGroupService.getLocalGroup(GROUP_ID);
        String originalDescription = localGroupType.getDescription();
        try {
            dummyTransactionRollingbackService.updateDescriptionAndRollback(GROUP_ID,
                    originalDescription + "_UPDATED",
                    DummyTransactionRollingbackService.ExceptionType.SERVICE_EXCEPTION);
            fail("should throw exception");
        } catch (LocalGroupService.DuplicateLocalGroupCodeException expected) {
        }

        LocalGroupType updatedGroup = localGroupService.getLocalGroup(GROUP_ID);
        assertEquals(originalDescription, updatedGroup.getDescription());
    }

    @Test
    public void uncheckedExceptionRollsBackTransaction() throws Exception {
        LocalGroupType localGroupType = localGroupService.getLocalGroup(GROUP_ID);
        String originalDescription = localGroupType.getDescription();
        try {
            dummyTransactionRollingbackService.updateDescriptionAndRollback(GROUP_ID,
                    originalDescription + "_UPDATED",
                    DummyTransactionRollingbackService.ExceptionType.RUNTIME_EXCEPTION);

            fail("should throw exception");
        } catch (RuntimeException expected) {
        }

        LocalGroupType updatedGroup = localGroupService.getLocalGroup(GROUP_ID);
        assertEquals(originalDescription, updatedGroup.getDescription());
    }

    @Test
    public void transactionCommitsIfNoExceptions() throws Exception {
        LocalGroupType localGroupType = localGroupService.getLocalGroup(GROUP_ID);
        String originalDescription = localGroupType.getDescription();
        dummyTransactionRollingbackService.updateDescriptionAndRollback(GROUP_ID,
                    originalDescription + "_UPDATED",
                    DummyTransactionRollingbackService.ExceptionType.NONE);

        LocalGroupType updatedGroup = localGroupService.getLocalGroup(GROUP_ID);
        assertEquals(originalDescription + "_UPDATED", updatedGroup.getDescription());
        // fix it back
        dummyTransactionRollingbackService.updateDescriptionAndRollback(GROUP_ID,
                originalDescription,
                DummyTransactionRollingbackService.ExceptionType.NONE);
    }
}
