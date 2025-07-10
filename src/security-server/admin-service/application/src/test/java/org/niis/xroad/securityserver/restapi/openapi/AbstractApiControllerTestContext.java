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
package org.niis.xroad.securityserver.restapi.openapi;

import org.junit.After;
import org.junit.Before;
import org.niis.xroad.common.mail.MailService;
import org.niis.xroad.restapi.config.audit.MockableAuditEventLoggingFacade;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.converter.PublicApiKeyDataConverter;
import org.niis.xroad.restapi.service.ApiKeyService;
import org.niis.xroad.securityserver.restapi.cache.CurrentSecurityServerId;
import org.niis.xroad.securityserver.restapi.cache.CurrentSecurityServerSignCertificates;
import org.niis.xroad.securityserver.restapi.config.AbstractFacadeMockingTestContext;
import org.niis.xroad.securityserver.restapi.converter.ClientConverter;
import org.niis.xroad.securityserver.restapi.service.CertificateAuthorityService;
import org.niis.xroad.securityserver.restapi.service.ClientService;
import org.niis.xroad.securityserver.restapi.service.DiagnosticService;
import org.niis.xroad.securityserver.restapi.service.GlobalConfService;
import org.niis.xroad.securityserver.restapi.service.InitializationService;
import org.niis.xroad.securityserver.restapi.service.InternalServerTestService;
import org.niis.xroad.securityserver.restapi.service.InternalTlsCertificateService;
import org.niis.xroad.securityserver.restapi.service.KeyService;
import org.niis.xroad.securityserver.restapi.service.NotificationService;
import org.niis.xroad.securityserver.restapi.service.PossibleActionsRuleEngine;
import org.niis.xroad.securityserver.restapi.service.SecurityServerBackupService;
import org.niis.xroad.securityserver.restapi.service.ServerConfService;
import org.niis.xroad.securityserver.restapi.service.SystemService;
import org.niis.xroad.securityserver.restapi.service.TokenCertificateService;
import org.niis.xroad.securityserver.restapi.service.TokenService;
import org.niis.xroad.securityserver.restapi.service.UrlValidator;
import org.niis.xroad.securityserver.restapi.service.VersionService;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.niis.xroad.securityserver.restapi.wsdl.WsdlValidator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Base for all api controller tests that need mocked beans in the application context. All api controller
 * test classes inheriting this will share the same mock bean configuration, and have a common
 * Spring Application Context therefore drastically reducing the execution time of the tests.
 * <p>
 * Service layer mocking strategy varies
 * - real implementations are used for services not defined as @MockitoBean or @MockitoSpyBean here
 * (example: {@link ClientService})
 * - mocks are always used for services defined as @MockitoBeans
 * (example: {@link CertificateAuthorityService})
 * - mocking depends on a case by case basis when @MockitoSpyBean is used. Some tests use 100% real implementation, others
 * mock some parts
 * (example: {@link KeyService}
 * <p>
 * Mocks the usual untestable facades (such as SignerRpcClient) via {@link AbstractFacadeMockingTestContext}
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractApiControllerTestContext extends AbstractFacadeMockingTestContext {
    @MockitoBean
    CertificateAuthorityService certificateAuthorityService;
    @MockitoBean
    SecurityServerBackupService backupService;
    @MockitoBean
    UrlValidator urlValidator;
    @MockitoBean
    SystemService systemService;
    @MockitoBean
    InternalTlsCertificateService internalTlsCertificateService;
    @MockitoBean
    CurrentSecurityServerSignCertificates currentSecurityServerSignCertificates;
    @MockitoBean
    CurrentSecurityServerId currentSecurityServerId;
    @MockitoBean
    InitializationService initializationService;
    @MockitoBean
    VersionService versionService;
    @MockitoBean
    InternalServerTestService internalServerTestService;
    // temporarily public accessor, I have plan to merge restapi.controller and restapi.openapi packages
    @MockitoBean
    public NotificationService notificationService;
    @MockitoBean
    public ApiKeyService apiKeyService;
    @MockitoBean
    public PublicApiKeyDataConverter publicApiKeyDataConverter;
    @MockitoBean
    JavaMailSender mailSender;

    @MockitoSpyBean
    DiagnosticService diagnosticService;
    @MockitoSpyBean
    GlobalConfService globalConfService;
    @MockitoSpyBean
    KeyService keyService;
    @MockitoSpyBean
    TokenService tokenService;
    @MockitoSpyBean
    TokenCertificateService tokenCertificateService;
    @MockitoSpyBean
    ServerConfService serverConfService;
    @MockitoSpyBean
    MailService mailService;
    @MockitoSpyBean
    WsdlValidator wsdlValidator;
    @MockitoSpyBean
    MockableAuditEventLoggingFacade auditEventLoggingFacade;
    @MockitoSpyBean
    PossibleActionsRuleEngine possibleActionsRuleEngine;
    @MockitoSpyBean
    ClientConverter clientConverter;
    @MockitoSpyBean
    ClientIdConverter clientIdConverter;

    /**
     * Add mock servlet request attributes to the RequestContextHolder. This is because testing a controller method
     * by directly calling it is not actually considered a real request. Some tests will need a 'real' request
     * (e.g. request scoped beans will not work without an existing request)
     */
    @Before
    public void mockServlet() {
        TestUtils.mockServletRequestAttributes();
    }

    @After
    public void cleanUpServlet() {
        RequestContextHolder.resetRequestAttributes();
    }
}
