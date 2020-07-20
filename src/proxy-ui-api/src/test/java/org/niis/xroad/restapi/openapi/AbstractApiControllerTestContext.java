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
package org.niis.xroad.restapi.openapi;

import org.junit.After;
import org.junit.Before;
import org.niis.xroad.restapi.cache.CurrentSecurityServerId;
import org.niis.xroad.restapi.cache.CurrentSecurityServerSignCertificates;
import org.niis.xroad.restapi.config.AbstractFacadeMockingTestContext;
import org.niis.xroad.restapi.config.audit.MockableAuditEventLoggingFacade;
import org.niis.xroad.restapi.converter.ClientConverter;
import org.niis.xroad.restapi.repository.InternalTlsCertificateRepository;
import org.niis.xroad.restapi.service.BackupService;
import org.niis.xroad.restapi.service.CertificateAuthorityService;
import org.niis.xroad.restapi.service.DiagnosticService;
import org.niis.xroad.restapi.service.GlobalConfService;
import org.niis.xroad.restapi.service.InitializationService;
import org.niis.xroad.restapi.service.KeyService;
import org.niis.xroad.restapi.service.NotificationService;
import org.niis.xroad.restapi.service.PossibleActionsRuleEngine;
import org.niis.xroad.restapi.service.RestoreService;
import org.niis.xroad.restapi.service.ServerConfService;
import org.niis.xroad.restapi.service.SystemService;
import org.niis.xroad.restapi.service.TokenCertificateService;
import org.niis.xroad.restapi.service.TokenService;
import org.niis.xroad.restapi.service.UrlValidator;
import org.niis.xroad.restapi.service.VersionService;
import org.niis.xroad.restapi.wsdl.WsdlValidator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.web.context.request.RequestContextHolder;

import static org.niis.xroad.restapi.util.TestUtils.mockServletRequestAttributes;

/**
 * Base for all api controller tests that need injected/mocked beans in the application context. All api controller
 * test classes inheriting this will have a common Spring Application Context therefore drastically reducing
 * the execution time of the api controller tests
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractApiControllerTestContext extends AbstractFacadeMockingTestContext {

    @MockBean
    CertificateAuthorityService certificateAuthorityService;
    @MockBean
    BackupService backupService;
    @MockBean
    RestoreService restoreService;
    @MockBean
    UrlValidator urlValidator;
    @MockBean
    DiagnosticService diagnosticService;
    @MockBean
    SystemService systemService;
    @MockBean
    CurrentSecurityServerSignCertificates currentSecurityServerSignCertificates;
    @MockBean
    CurrentSecurityServerId currentSecurityServerId;
    @MockBean
    InitializationService initializationService;
    @MockBean
    InternalTlsCertificateRepository mockRepository;
    @MockBean
    VersionService versionService;
    // temporarily public accessor, I have plan to merge restapi.controller and restapi.openapi packages
    @MockBean
    public NotificationService notificationService;

    @SpyBean
    GlobalConfService globalConfService;
    @SpyBean
    KeyService keyService;
    @SpyBean
    TokenService tokenService;
    @SpyBean
    TokenCertificateService tokenCertificateService;
    @SpyBean
    ServerConfService serverConfService;
    @SpyBean
    WsdlValidator wsdlValidator;
    @SpyBean
    MockableAuditEventLoggingFacade auditEventLoggingFacade;
    @SpyBean
    PossibleActionsRuleEngine possibleActionsRuleEngine;
    @SpyBean
    ClientConverter clientConverter;

    /**
     * Add mock servlet request attributes to the RequestContextHolder. This is because testing a controller method
     * by directly calling it is not actually considered a real request. Some tests will need a 'real' request
     * (e.g. request scoped beans will not work without an existing request)
     */
    @Before
    public void mockServlet() {
        mockServletRequestAttributes();
    }

    @After
    public void cleanUpServlet() {
        RequestContextHolder.resetRequestAttributes();
    }
}
