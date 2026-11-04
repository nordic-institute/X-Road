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
package org.niis.xroad.securityserver.restapi.scheduling;

import com.apicatalog.did.Did;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.rpc.VaultKeyProvider;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.securityserver.restapi.cache.CurrentSecurityServerId;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.repository.DsParticipantRepository;
import org.niis.xroad.securityserver.restapi.service.DataspaceDidAuthority;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantContext;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantKind;
import org.niis.xroad.securityserver.restapi.service.DataspaceReadinessPredicates;
import org.niis.xroad.securityserver.restapi.service.DidRegistrationSender;
import org.niis.xroad.securityserver.restapi.service.GlobalConfService;
import org.niis.xroad.securityserver.restapi.service.ManagementRequestSenderFactory;
import org.niis.xroad.securityserver.restapi.service.ManagementRequestSenderService;
import org.niis.xroad.securityserver.restapi.service.ServerConfService;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.model.ParticipantState;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.client.SignerSignClient;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Runs the publication stage with real method security and no Authentication in the security context,
 * as on the scheduler thread.
 */
class DataspacePublicationStageSecurityTest {

    private static final Did SYSTEM_DID = Did.parse("did:web:ss.example.test%3A7183:xrd-ss0");
    private static final ParticipantContext SYSTEM_CONTEXT = new ParticipantContext("xrd-ss0", ParticipantKind.SYSTEM, null);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MethodSecurityConfig.class)
            .withBean(GlobalConfProvider.class, () -> mock(GlobalConfProvider.class))
            .withBean(CurrentSecurityServerId.class, () -> new CurrentSecurityServerId(TestUtils.OWNER_SERVER_ID))
            .withBean(SignerRpcClient.class, () -> mock(SignerRpcClient.class))
            .withBean(SignerSignClient.class, () -> mock(SignerSignClient.class))
            .withBean(VaultKeyProvider.class, () -> mock(VaultKeyProvider.class))
            .withBean(AdminServiceProperties.class, () -> mock(AdminServiceProperties.class))
            .withBean(ManagementRequestSenderFactory.class)
            .withBean(DidRegistrationSender.class)
            .withBean(ManagementRequestSenderService.class)
            .withBean(ServerConfService.class, () -> mock(ServerConfService.class))
            .withBean(GlobalConfService.class)
            .withBean(DataspaceReadinessPredicates.class, () -> mock(DataspaceReadinessPredicates.class))
            .withBean(DataspaceDidAuthority.class, () -> mock(DataspaceDidAuthority.class))
            .withBean(DsParticipantRepository.class, () -> mock(DsParticipantRepository.class))
            .withBean(DataspacePublicationStage.class);

    @BeforeEach
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void stageWithoutAuthenticationReachesSenderConstructionForSystemDidRegister() {
        contextRunner.run(context -> {
            var systemRow = new DsParticipantEntity();
            systemRow.setId(17L);
            systemRow.setDid(SYSTEM_DID.toString());
            systemRow.setState(ParticipantState.ACTIVE);
            when(context.getBean(DataspaceReadinessPredicates.class).isDataspaceReady()).thenReturn(true);
            when(context.getBean(DataspaceReadinessPredicates.class).registeredAuthCertBytes())
                    .thenReturn(Optional.of(new byte[] {1}));
            when(context.getBean(DataspaceDidAuthority.class).dspBaseUrlIfValid()).thenReturn(Optional.of("https://test/dsp"));
            when(context.getBean(DsParticipantRepository.class).findSystemParticipant()).thenReturn(Optional.of(systemRow));

            context.getBean(DataspacePublicationStage.class).publish(Set.of(SYSTEM_CONTEXT));

            var globalConfProvider = context.getBean(GlobalConfProvider.class);
            verify(globalConfProvider).verifyValidity();
            verify(globalConfProvider).getManagementRequestService();
        });
    }

    @Test
    void guardedSenderServiceStillRequiresAuthentication() {
        contextRunner.run(context -> {
            var senderService = context.getBean(ManagementRequestSenderService.class);

            assertThat(catchThrowable(() -> senderService.sendAuthCertRegisterRequest("ss.example.test", new byte[] {1}, true)))
                    .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        });
    }

    @Test
    void guardedGlobalConfServiceRequiresAuthentication() {
        contextRunner.run(context -> {
            var globalConfService = context.getBean(GlobalConfService.class);

            assertThat(catchThrowable(globalConfService::verifyGlobalConfValidity))
                    .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
            verify(context.getBean(GlobalConfProvider.class), never()).verifyValidity();
        });
    }

    @Configuration
    @EnableMethodSecurity(proxyTargetClass = true)
    static class MethodSecurityConfig {
    }
}
