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
package org.niis.xroad.cs.admin.core.dataspace;

import ee.ria.xroad.common.util.process.ExternalProcessRunner;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.niis.xroad.cs.admin.api.dto.HAConfigStatus;
import org.niis.xroad.cs.admin.api.dto.InitialServerConfDto;
import org.niis.xroad.cs.admin.api.dto.IssuerHostState;
import org.niis.xroad.cs.admin.api.dto.IssuerSetUpResult;
import org.niis.xroad.cs.admin.api.facade.SignerProxyFacade;
import org.niis.xroad.cs.admin.api.service.DataspaceIssuerProvisioningService;
import org.niis.xroad.cs.admin.api.service.IssuerHostService;
import org.niis.xroad.cs.admin.api.service.IssuerSetUpLeaseService;
import org.niis.xroad.cs.admin.api.service.IssuerSetUpService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.api.service.TokenPinValidator;
import org.niis.xroad.cs.admin.core.entity.GlobalGroupEntity;
import org.niis.xroad.cs.admin.core.repository.GlobalGroupRepository;
import org.niis.xroad.cs.admin.core.service.InitializationServiceImpl;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.dstls.DsTlsCertificateStatus;
import org.niis.xroad.restapi.service.DsTlsCertificateService;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.protocol.dto.TokenStatusInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Starts the Spring beans of the issuer set-up package plus {@link InitializationServiceImpl}, with every
 * set-up precondition already met before the context starts: host set, issuer unbound, connector TLS
 * certificate ready and the set-up lease free. The node starts fresh (software token not initialised, no
 * system parameters) or, in {@link RestartOfAnInitialisedNode}, already initialised; system parameters and
 * the token keep what initialisation writes. A bean in the scanned package that started set-up during
 * context start-up, or within {@value #QUIET_PERIOD_MS} ms of start-up or initialisation, synchronously or
 * not, would acquire the lease and reach {@link DataspaceIssuerProvisioningService#provisionIssuer()}.
 * Beans outside the scanned package and the mocked collaborators themselves are not covered.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringBootTest(
        classes = IssuerSetUpContextStartupIntegrationTest.ContextUnderTest.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ComponentScan(
        basePackageClasses = IssuerSetUpServiceImpl.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
                DataspaceIssuerRpcConfig.class,
                DataspaceIssuerProvisioningServiceImpl.class,
                DataspaceIssuerDidServiceImpl.class,
                DataspaceMemberDidServiceImpl.class,
                ParticipantValueStoreImpl.class,
                IssuerSetUpLeaseServiceImpl.class,
                IssuerServiceStatusServiceImpl.class
        }))
@EnableScheduling
@Execution(ExecutionMode.SAME_THREAD)
@TestPropertySource(properties = {
        "script.generate-gpg-keypair.path=/bin/true",
        "gpgkeys.gpghome=/tmp"
})
class IssuerSetUpContextStartupIntegrationTest {

    private static final String HOST = "issuer.example.org";
    private static final int GENERAL_NAME_DNS = 2;
    private static final String NODE = "node-a";
    private static final int DEADLINE_AFTER_MS = 60000;
    private static final long QUIET_PERIOD_MS = 2000;
    private static final String NODE_INITIALISED_PROPERTY = "test.node-initialised";

    @Configuration
    @Import(InitializationServiceImpl.class)
    static class ContextUnderTest {
        @Bean
        HAConfigStatus haConfigStatus() {
            return new HAConfigStatus(NODE, false);
        }

        @Bean
        SystemParameterService systemParameterService(
                @Value("${" + NODE_INITIALISED_PROPERTY + ":false}") boolean nodeInitialised) {
            Map<String, String> parameters = new ConcurrentHashMap<>();
            if (nodeInitialised) {
                parameters.put(SystemParameterService.CENTRAL_SERVER_ADDRESS, "cs.example.com");
                parameters.put(SystemParameterService.INSTANCE_IDENTIFIER, "TEST");
            }
            var service = mock(SystemParameterService.class);
            doAnswer(invocation -> {
                parameters.put(invocation.getArgument(0), invocation.getArgument(1));
                return null;
            }).when(service).updateOrCreateParameter(anyString(), anyString());
            when(service.getCentralServerAddress())
                    .thenAnswer(_ -> parameters.getOrDefault(SystemParameterService.CENTRAL_SERVER_ADDRESS, ""));
            when(service.getInstanceIdentifier())
                    .thenAnswer(_ -> parameters.getOrDefault(SystemParameterService.INSTANCE_IDENTIFIER, ""));
            return service;
        }

        @Bean
        SignerProxyFacade signerProxyFacade(
                @Value("${" + NODE_INITIALISED_PROPERTY + ":false}") boolean nodeInitialised) throws Exception {
            var tokenInitialised = new AtomicBoolean(nodeInitialised);
            var tokenInfo = mock(TokenInfo.class);
            when(tokenInfo.getStatus()).thenAnswer(_ ->
                    tokenInitialised.get() ? TokenStatusInfo.OK : TokenStatusInfo.NOT_INITIALIZED);
            var facade = mock(SignerProxyFacade.class);
            when(facade.getToken(any())).thenReturn(tokenInfo);
            doAnswer(_ -> {
                tokenInitialised.set(true);
                return null;
            }).when(facade).initSoftwareToken(any());
            return facade;
        }

        @Bean
        IssuerHostService issuerHostService() {
            var service = mock(IssuerHostService.class);
            when(service.getState()).thenReturn(new IssuerHostState(HOST, false));
            return service;
        }

        @Bean
        DsTlsCertificateService dsTlsCertificateService() throws Exception {
            var certificate = mock(X509Certificate.class);
            when(certificate.getSubjectAlternativeNames()).thenReturn(List.of(List.of(GENERAL_NAME_DNS, HOST)));
            var service = mock(DsTlsCertificateService.class);
            when(service.getStatus()).thenReturn(new DsTlsCertificateStatus(true, certificate));
            return service;
        }

        @Bean
        IssuerSetUpLeaseService issuerSetUpLeaseService() {
            var service = mock(IssuerSetUpLeaseService.class);
            when(service.currentHolder()).thenReturn(Optional.empty());
            when(service.tryAcquire(anyString(), any())).thenReturn(true);
            return service;
        }

        @Bean
        IssuerProvisioningRpcChannelProperties issuerProvisioningRpcChannelProperties() {
            var properties = mock(IssuerProvisioningRpcChannelProperties.class);
            when(properties.deadlineAfter()).thenReturn(DEADLINE_AFTER_MS);
            return properties;
        }

        @Bean
        DataspaceIssuerProvisioningService dataspaceIssuerProvisioningService() {
            return mock(DataspaceIssuerProvisioningService.class);
        }
    }

    @Autowired
    private IssuerSetUpService issuerSetUpService;
    @Autowired
    private InitializationServiceImpl initializationService;
    @Autowired
    private IssuerSetUpLeaseService issuerSetUpLeaseService;
    @Autowired
    private DataspaceIssuerProvisioningService dataspaceIssuerProvisioningService;
    @Autowired
    private SignerProxyFacade signerProxyFacade;

    @MockitoBean
    private GlobalGroupRepository globalGroupRepository;
    @MockitoBean
    private TokenPinValidator tokenPinValidator;
    @MockitoBean
    private AuditDataHelper auditDataHelper;
    @MockitoBean
    private ExternalProcessRunner externalProcessRunner;

    @Test
    void initialisingAFreshNodeNeverStartsSetUp() throws Exception {
        when(globalGroupRepository.getByGroupCode(any())).thenReturn(Optional.of(new GlobalGroupEntity("owners")));
        when(externalProcessRunner.executeAndThrowOnFailure(any(), any(String[].class)))
                .thenReturn(new ExternalProcessRunner.ProcessResult("cmd", 0, List.of()));

        var configDto = new InitialServerConfDto();
        configDto.setCentralServerAddress("cs.example.com");
        configDto.setInstanceIdentifier("TEST");
        configDto.setSoftwareTokenPin("pin123");

        initializationService.initialize(configDto);

        verify(signerProxyFacade).initSoftwareToken(any());
        verifySetUpNeverStarts();
    }

    @Test
    void explicitSetUpCallDoesReachProvisioning() {
        var result = issuerSetUpService.start();

        assertThat(result).isEqualTo(new IssuerSetUpResult.Accepted());
        verify(dataspaceIssuerProvisioningService, timeout(QUIET_PERIOD_MS)).provisionIssuer();
        verify(issuerSetUpLeaseService, timeout(QUIET_PERIOD_MS)).release(eq(NODE));
    }

    private void verifySetUpNeverStarts() {
        verify(issuerSetUpLeaseService, after(QUIET_PERIOD_MS).never()).tryAcquire(anyString(), any());
        verify(dataspaceIssuerProvisioningService, never()).provisionIssuer();
    }

    @Nested
    @TestPropertySource(properties = NODE_INITIALISED_PROPERTY + "=true")
    class RestartOfAnInitialisedNode {

        @Test
        void startingTheContextNeverStartsSetUp() {
            assertThat(initializationService.getInitializationStatus().getCentralServerAddress())
                    .isEqualTo("cs.example.com");
            verifySetUpNeverStarts();
        }
    }
}
