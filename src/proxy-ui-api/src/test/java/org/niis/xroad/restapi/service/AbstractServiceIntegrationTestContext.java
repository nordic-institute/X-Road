/**
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
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.ServerConfType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.auth.ApiKeyAuthenticationHelper;
import org.niis.xroad.restapi.cache.CurrentSecurityServerId;
import org.niis.xroad.restapi.cache.CurrentSecurityServerSignCertificates;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.facade.SignerProxyFacade;
import org.niis.xroad.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.Mockito.when;

/**
 * Base for all service integration tests that need injected/mocked beans in the application context. All service
 * integration test classes inheriting this will have a common Spring Application Context therefore drastically
 * reducing the execution time of the integration tests.
 *
 * Integration tests do not mock the repository layer.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Transactional
@WithMockUser
public abstract class AbstractServiceIntegrationTestContext {
    @Autowired
    ApiKeyService apiKeyService;
    @Autowired
    ApiKeyAuthenticationHelper apiKeyAuthenticationHelper;
    @Autowired
    ClientService clientService;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    EndpointService endpointService;
    @Autowired
    KeyAndCertificateRequestService keyAndCertificateRequestService;
    @Autowired
    LocalGroupService localGroupService;

    @MockBean
    GlobalConfFacade globalConfFacade;
    @MockBean
    ManagementRequestSenderService managementRequestSenderService;
    @MockBean
    CurrentSecurityServerSignCertificates currentSecurityServerSignCertificates;
    @MockBean
    SignerProxyFacade signerProxyFacade;
    @MockBean
    CurrentSecurityServerId currentSecurityServerId;

    static final ClientId commonOwnerId = TestUtils.getClientId("FI", "GOV", "M1", null);

    @Before
    public void setupCommonMocks() {
        ServerConfType sct = new ServerConfType();
        ClientType owner = new ClientType();
        owner.setIdentifier(commonOwnerId);
        sct.setOwner(owner);
        sct.setServerCode("SS1");
        when(currentSecurityServerId.getServerId()).thenReturn(SecurityServerId.create(commonOwnerId, "SS1"));
    }
}
