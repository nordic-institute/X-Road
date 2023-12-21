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

package org.niis.xroad.cs.admin.core.service;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.TimeUtils;
import ee.ria.xroad.signer.protocol.dto.KeyInfoProto;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfoProto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSigningKey;
import org.niis.xroad.cs.admin.api.dto.AlertInfo;
import org.niis.xroad.cs.admin.api.dto.GlobalConfGenerationStatus;
import org.niis.xroad.cs.admin.api.facade.SignerProxyFacade;
import org.niis.xroad.cs.admin.api.service.ConfigurationSigningKeysService;
import org.niis.xroad.cs.admin.api.service.GlobalConfGenerationStatusService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static ee.ria.xroad.signer.protocol.dto.KeyUsageInfo.SIGNING;
import static ee.ria.xroad.signer.protocol.dto.TokenStatusInfo.OK;
import static java.time.temporal.ChronoUnit.HOURS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.api.dto.GlobalConfGenerationStatus.GlobalConfGenerationStatusEnum.FAILURE;
import static org.niis.xroad.cs.admin.api.dto.GlobalConfGenerationStatus.GlobalConfGenerationStatusEnum.SUCCESS;
import static org.niis.xroad.cs.admin.api.dto.GlobalConfGenerationStatus.GlobalConfGenerationStatusEnum.UNKNOWN;
import static org.niis.xroad.cs.admin.api.service.ConfigurationSigningKeysService.SOURCE_TYPE_EXTERNAL;
import static org.niis.xroad.cs.admin.api.service.ConfigurationSigningKeysService.SOURCE_TYPE_INTERNAL;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private SignerProxyFacade signerProxyFacade;
    @Mock
    private SystemParameterService systemParameterService;
    @Mock
    private ConfigurationSigningKeysService configurationSigningKeysService;
    @Mock
    private GlobalConfGenerationStatusService globalConfGenerationStatus;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Test
    void getAlertsSignerException() throws Exception {
        when(signerProxyFacade.getTokens()).thenThrow(new Exception());

        final Set<AlertInfo> alerts = notificationService.getAlerts();

        assertThat(alerts).hasSize(1)
                .anyMatch(p -> p.getErrorCode().equals("status.signer_error"));
    }

    @Test
    void getAlertsAllOk() throws Exception {
        ConfigurationSigningKey confSigningKey = new ConfigurationSigningKey();
        confSigningKey.setKeyIdentifier("id");
        mockInitialized(true, true);
        when(globalConfGenerationStatus.get()).thenReturn(new GlobalConfGenerationStatus(SUCCESS, TimeUtils.now()));
        when(configurationSigningKeysService.findActiveForSource(SOURCE_TYPE_INTERNAL))
                .thenReturn(Optional.of(confSigningKey));
        when(configurationSigningKeysService.findActiveForSource(SOURCE_TYPE_EXTERNAL))
                .thenReturn(Optional.of(confSigningKey));

        try (MockedStatic<SystemProperties> utilities = Mockito.mockStatic(SystemProperties.class)) {
            utilities.when(SystemProperties::getCenterTrustedAnchorsAllowed).thenReturn(true);

            final Set<AlertInfo> alerts = notificationService.getAlerts();

            assertThat(alerts).isEmpty();
        }
    }

    @Test
    void getAlertsAllFail() throws Exception {
        ConfigurationSigningKey confSigningKey = new ConfigurationSigningKey();
        confSigningKey.setKeyIdentifier("id-other");
        mockInitialized(true, true);

        Instant time = TimeUtils.now();
        when(globalConfGenerationStatus.get()).thenReturn(new GlobalConfGenerationStatus(FAILURE, time));
        when(configurationSigningKeysService.findActiveForSource(SOURCE_TYPE_INTERNAL))
                .thenReturn(Optional.of(confSigningKey));
        when(configurationSigningKeysService.findActiveForSource(SOURCE_TYPE_EXTERNAL))
                .thenReturn(Optional.of(confSigningKey));

        try (MockedStatic<SystemProperties> utilities = Mockito.mockStatic(SystemProperties.class)) {
            utilities.when(SystemProperties::getCenterTrustedAnchorsAllowed).thenReturn(true);

            final Set<AlertInfo> alerts = notificationService.getAlerts();

            assertThat(alerts).hasSize(3)
                    .contains(new AlertInfo("status.signing_key.internal.token_not_found"))
                    .contains(new AlertInfo("status.signing_key.external.token_not_found"))
                    .contains(new AlertInfo("status.global_conf_generation.failing", time));
        }
    }

    @Test
    void getAlertsTokenNotActive() throws Exception {
        ConfigurationSigningKey confSigningKey = new ConfigurationSigningKey();
        confSigningKey.setKeyIdentifier("id");
        mockInitialized(false, true);
        when(systemParameterService.getConfExpireIntervalSeconds()).thenReturn(600);
        when(globalConfGenerationStatus.get()).thenReturn(new GlobalConfGenerationStatus(SUCCESS, TimeUtils.now()));
        when(configurationSigningKeysService.findActiveForSource(SOURCE_TYPE_INTERNAL))
                .thenReturn(Optional.of(confSigningKey));
        when(configurationSigningKeysService.findActiveForSource(SOURCE_TYPE_EXTERNAL))
                .thenReturn(Optional.of(confSigningKey));

        try (MockedStatic<SystemProperties> utilities = Mockito.mockStatic(SystemProperties.class)) {
            utilities.when(SystemProperties::getCenterTrustedAnchorsAllowed).thenReturn(true);

            final Set<AlertInfo> alerts = notificationService.getAlerts();

            assertThat(alerts).hasSize(2)
                    .contains(new AlertInfo("status.signing_key.internal.token_not_active"))
                    .contains(new AlertInfo("status.signing_key.external.token_not_active"));
        }
    }

    @Test
    void getAlertsKeyNotActiveConfStatusUnknown() throws Exception {
        ConfigurationSigningKey confSigningKey = new ConfigurationSigningKey();
        confSigningKey.setKeyIdentifier("id");
        mockInitialized(true, false);

        when(globalConfGenerationStatus.get()).thenReturn(new GlobalConfGenerationStatus(UNKNOWN, null));
        when(configurationSigningKeysService.findActiveForSource(SOURCE_TYPE_INTERNAL))
                .thenReturn(Optional.of(confSigningKey));
        when(configurationSigningKeysService.findActiveForSource(SOURCE_TYPE_EXTERNAL))
                .thenReturn(Optional.of(confSigningKey));

        try (MockedStatic<SystemProperties> utilities = Mockito.mockStatic(SystemProperties.class)) {
            utilities.when(SystemProperties::getCenterTrustedAnchorsAllowed).thenReturn(true);

            final Set<AlertInfo> alerts = notificationService.getAlerts();

            assertThat(alerts).hasSize(3)
                    .contains(new AlertInfo("status.global_conf_generation.status_not_found"))
                    .contains(new AlertInfo("status.signing_key.internal.key_not_available"))
                    .contains(new AlertInfo("status.signing_key.external.key_not_available"));
        }
    }

    @Test
    void getAlertsGlobalConfExpired() throws Exception {
        ConfigurationSigningKey confSigningKey = new ConfigurationSigningKey();
        confSigningKey.setKeyIdentifier("id");
        mockInitialized(true, true);
        when(globalConfGenerationStatus.get())
                .thenReturn(new GlobalConfGenerationStatus(SUCCESS, TimeUtils.now().minus(1, HOURS)));
        when(configurationSigningKeysService.findActiveForSource(SOURCE_TYPE_INTERNAL))
                .thenReturn(Optional.of(confSigningKey));
        when(configurationSigningKeysService.findActiveForSource(SOURCE_TYPE_EXTERNAL))
                .thenReturn(Optional.of(confSigningKey));

        try (MockedStatic<SystemProperties> utilities = Mockito.mockStatic(SystemProperties.class)) {
            utilities.when(SystemProperties::getCenterTrustedAnchorsAllowed).thenReturn(true);

            final Set<AlertInfo> alerts = notificationService.getAlerts();

            assertThat(alerts).hasSize(1)
                    .contains(new AlertInfo("status.global_conf_generation.global_conf_expired"));
        }
    }

    private void mockInitialized(boolean tokenActive, boolean keyAvailable) throws Exception {
        KeyInfoProto keyinfo = KeyInfoProto.newBuilder()
                .setAvailable(keyAvailable)
                .setUsage(SIGNING)
                .setFriendlyName("")
                .setId("id")
                .setLabel("")
                .setPublicKey("")
                .setSignMechanismName("")
                .build();

        TokenInfo tokenInfo = new TokenInfo(TokenInfoProto.newBuilder()
                .setType("")
                .setFriendlyName("")
                .setId("0")
                .setReadOnly(false)
                .setAvailable(true)
                .setActive(tokenActive)
                .setSerialNumber("")
                .setLabel("")
                .setSlotIndex(0)
                .setStatus(OK)
                .addKeyInfo(keyinfo)
                .build());

        when(signerProxyFacade.getTokens()).thenReturn(List.of(tokenInfo));
        when(systemParameterService.getInstanceIdentifier()).thenReturn("CS");
        when(systemParameterService.getCentralServerAddress()).thenReturn("https://cs");
    }

}
