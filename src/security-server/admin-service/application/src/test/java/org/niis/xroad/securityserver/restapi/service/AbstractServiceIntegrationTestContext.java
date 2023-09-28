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

import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.ServerConfType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.Before;
import org.niis.xroad.securityserver.restapi.cache.CurrentSecurityServerId;
import org.niis.xroad.securityserver.restapi.cache.CurrentSecurityServerSignCertificates;
import org.niis.xroad.securityserver.restapi.config.AbstractFacadeMockingTestContext;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.niis.xroad.securityserver.restapi.wsdl.OpenApiParser;
import org.niis.xroad.securityserver.restapi.wsdl.WsdlValidator;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

import static org.mockito.Mockito.when;

/**
 * Base for all service integration tests that need mocked beans in the application context. All service
 * integration test classes inheriting this will shared the same mock bean configuration, and have a common
 * Spring Application Context therefore drastically reducing the execution time of the integration tests.
 *
 * Extend this when
 * - you are implementing an service layer integration test
 * - you do not want to mock other services
 * - you want to use the real repository layer, and not mock it
 *
 * In case you want to mock some of the non-mocked dependencies (such as some other service) in some specific test,
 * you can consider moving that dependency into this class as a SpyBean (like {@link GlobalConfService})
 * but this should not be a common solution, and all inheriting tests that use the same dependency need to be updated
 * when such change is made.
 *
 * Mocks the usual untestable facades (such as SignerProxyFacade) via {@link AbstractFacadeMockingTestContext}
 *
 */
public abstract class AbstractServiceIntegrationTestContext extends AbstractFacadeMockingTestContext {
    @SpyBean
    GlobalConfService globalConfService;
    @SpyBean
    OpenApiParser openApiParser;

    @MockBean
    CurrentSecurityServerSignCertificates currentSecurityServerSignCertificates;
    @MockBean
    CurrentSecurityServerId currentSecurityServerId;
    @MockBean
    WsdlValidator wsdlValidator;
    @MockBean
    UrlValidator urlValidator;

    static final ClientId.Conf COMMON_OWNER_ID = TestUtils.getClientId("FI", "GOV", "M1", null);

    @Before
    public void setupCommonMocks() {
        ServerConfType sct = new ServerConfType();
        ClientType owner = new ClientType();
        owner.setIdentifier(COMMON_OWNER_ID);
        sct.setOwner(owner);
        sct.setServerCode("SS1");
        when(currentSecurityServerId.getServerId()).thenReturn(SecurityServerId.Conf.create(COMMON_OWNER_ID, "SS1"));
    }
}
