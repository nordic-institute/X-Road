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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.Test;
import org.niis.xroad.restapi.util.PersistenceUtils;
import org.niis.xroad.securityserver.restapi.config.AbstractFacadeMockingTestContext;
import org.niis.xroad.securityserver.restapi.repository.ClientRepository;
import org.niis.xroad.securityserver.restapi.repository.ServerConfRepository;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.niis.xroad.serverconf.impl.entity.ServerConfEntity;
import org.niis.xroad.serverconf.impl.ownserver.OwnIdentity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doAnswer;

/**
 * test AdminOwnServerIdentitySource
 */
public class AdminOwnServerIdentitySourceIntegrationTest extends AbstractFacadeMockingTestContext {

    private static final ClientId.Conf SEED_OWNER = TestUtils.getClientId("FI", "GOV", "M1", null);
    private static final ClientId.Conf OTHER_MEMBER = TestUtils.getClientId("FI", "GOV", "M2", null);
    private static final ClientId.Conf OTHER_MEMBER_SUBSYSTEM = TestUtils.getClientId("FI", "GOV", "M2", "SS5");

    @Autowired
    private AdminOwnServerIdentitySource identitySource;
    @MockitoSpyBean
    private ServerConfRepository serverConfRepository;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private PersistenceUtils persistenceUtils;

    @Test
    public void readsSeededOwnerAndServerCode() {
        assertEquals(knownId(SEED_OWNER), identitySource.read());
    }

    @Test
    public void seesUncommittedOwnerChangeInSameTransaction() {
        ServerConfEntity serverConf = serverConfRepository.getServerConf();
        serverConf.setOwner(clientRepository.getClient(OTHER_MEMBER_SUBSYSTEM));

        assertEquals(knownId(OTHER_MEMBER), identitySource.read());
    }

    @Test
    public void nullOwnerIsOwnerNotInitialised() {
        serverConfRepository.getServerConf().setOwner(null);

        assertEquals(new OwnIdentity.OwnerNotInitialised(), identitySource.read());
    }

    @Test
    public void missingServerConfRowIsOwnerNotInitialised() {
        var session = persistenceUtils.getCurrentSession();
        session.createMutationQuery("update ClientEntity set conf = null").executeUpdate();
        session.createMutationQuery("delete from ServerConfEntity").executeUpdate();

        assertEquals(new OwnIdentity.OwnerNotInitialised(), identitySource.read());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void opensOwnReadOnlyTransactionWhenCallerHasNone() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        AtomicBoolean readInReadOnlyTransaction = new AtomicBoolean();
        doAnswer(invocation -> {
            readInReadOnlyTransaction.set(TransactionSynchronizationManager.isActualTransactionActive()
                    && TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return invocation.callRealMethod();
        }).when(serverConfRepository).getServerConf();

        assertEquals(knownId(SEED_OWNER), identitySource.read());
        assertTrue(readInReadOnlyTransaction.get());
    }

    private static OwnIdentity knownId(ClientId owner) {
        return new OwnIdentity.Known(SecurityServerId.Conf.create(owner, "TEST-INMEM-SS"));
    }
}
