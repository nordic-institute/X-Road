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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.identifier.ClientId;

import org.junit.Before;
import org.mockito.stubbing.Answer;
import org.niis.xroad.common.identifiers.jpa.entity.ClientIdEntity;
import org.niis.xroad.common.identifiers.jpa.entity.MemberIdEntity;
import org.niis.xroad.restapi.common.backup.repository.BackupRepository;
import org.niis.xroad.securityserver.restapi.config.AbstractFacadeMockingTestContext;
import org.niis.xroad.securityserver.restapi.repository.ClientRepository;
import org.niis.xroad.securityserver.restapi.repository.IdentifierRepository;
import org.niis.xroad.securityserver.restapi.repository.LocalGroupRepository;
import org.niis.xroad.securityserver.restapi.repository.ServerConfRepository;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.niis.xroad.serverconf.impl.entity.ClientEntity;
import org.niis.xroad.serverconf.impl.entity.ServerConfEntity;
import org.niis.xroad.serverconf.model.Client;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Base for all service tests that need mocked beans in the application context. All service
 * test classes inheriting this will shared the same mock bean configuration, and have a common
 * Spring Application Context therefore drastically reducing the execution time of the tests.
 * <p>
 * Do not introduce new @MockitoBean or @SpyBean dependencies in the inherited classes. Doing so will mean Spring
 * creates a different applicationContext for the inherited class and other AbstractServiceTestContext classes,
 * and the performance improvement from using this base class is not realized. If possible, define all mocks and spies
 * in this base class instead.
 * <p>
 * Extend this when
 * - you are implementing an service layer test
 * - you do not want to mock other services
 * - you want to mock the repository layer, instead of using the real repositories and data
 * <p>
 * In case you want to mock some of the non-mocked dependencies (such as some other service) in some specific test,
 * you can consider moving that dependency into this class as a SpyBean (example in
 * {@link AbstractServiceIntegrationTestContext.globalConfService}) but this should not
 * be a common solution, and all inheriting tests that use the same dependency need to be updated
 * when such change is made.
 * <p>
 * Mocks the usual untestable facades (such as SignerRpcClient) via {@link AbstractFacadeMockingTestContext}
 */
public abstract class AbstractServiceTestContext extends AbstractFacadeMockingTestContext {
    @MockitoBean
    BackupRepository backupRepository;
    @MockitoBean
    ClientRepository clientRepository;
    @MockitoBean
    ServerConfRepository serverConfRepository;
    @MockitoBean
    IdentifierRepository identifierRepository;
    @MockitoBean
    LocalGroupRepository localGroupRepository;
    @MockitoBean
    ServerConfEntity serverConfEntity;
    @MockitoBean
    TokenPinValidator tokenPinValidator;

    static final ClientIdEntity COMMON_OWNER_ID = MemberIdEntity.create("FI", "GOV", "M1");

    @Before
    public void setupCommonMocks() {
        ServerConfEntity sct = new ServerConfEntity();
        ClientEntity owner = new ClientEntity();
        owner.setIdentifier(COMMON_OWNER_ID);
        sct.setOwner(owner);
        sct.setServerCode("SS1");
        when(serverConfRepository.getServerConf()).thenReturn(sct);
        when(globalConfProvider.getMemberName(any())).thenAnswer((Answer<String>) invocation -> {
            Object[] args = invocation.getArguments();
            ClientId identifier = (ClientId) args[0];
            return identifier.getSubsystemCode() != null ? TestUtils.NAME_FOR + identifier.getSubsystemCode()
                    : TestUtils.NAME_FOR + "test-member";
        });
        when(clientRepository.getClient(any(ClientId.Conf.class))).thenAnswer((Answer<Client>) invocation -> {
            Object[] args = invocation.getArguments();
            ClientId.Conf identifier = (ClientId.Conf) args[0];
            Client client = new Client();
            client.setIdentifier(identifier);
            return client;
        });
    }
}
