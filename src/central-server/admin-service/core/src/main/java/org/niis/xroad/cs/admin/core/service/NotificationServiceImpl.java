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

import ee.ria.xroad.common.util.TimeUtils;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSigningKey;
import org.niis.xroad.cs.admin.api.dto.AlertInfo;
import org.niis.xroad.cs.admin.api.dto.GlobalConfGenerationStatus;
import org.niis.xroad.cs.admin.api.facade.SignerProxyFacade;
import org.niis.xroad.cs.admin.api.service.ConfigurationSigningKeysService;
import org.niis.xroad.cs.admin.api.service.GlobalConfGenerationStatusService;
import org.niis.xroad.cs.admin.api.service.NotificationService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static ee.ria.xroad.common.SystemProperties.getCenterTrustedAnchorsAllowed;
import static ee.ria.xroad.signer.protocol.dto.TokenStatusInfo.NOT_INITIALIZED;
import static java.lang.String.format;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.niis.xroad.cs.admin.api.dto.GlobalConfGenerationStatus.GlobalConfGenerationStatusEnum.FAILURE;
import static org.niis.xroad.cs.admin.api.dto.GlobalConfGenerationStatus.GlobalConfGenerationStatusEnum.UNKNOWN;
import static org.niis.xroad.cs.admin.api.service.ConfigurationSigningKeysService.SOURCE_TYPE_EXTERNAL;
import static org.niis.xroad.cs.admin.api.service.ConfigurationSigningKeysService.SOURCE_TYPE_INTERNAL;
import static org.niis.xroad.cs.admin.core.service.TokensServiceImpl.SOFTWARE_TOKEN_ID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final ConfigurationSigningKeysService configurationSigningKeysService;
    private final SystemParameterService systemParameterService;
    private final SignerProxyFacade signerProxyFacade;
    private final GlobalConfGenerationStatusService globalConfGenerationStatus;

    @Override
    public Set<AlertInfo> getAlerts() {

        final List<TokenInfo> tokens;
        try {
            tokens = signerProxyFacade.getTokens();
        } catch (Exception e) {
            log.error("Failed to get tokens", e);
            return Set.of(new AlertInfo("status.signer_error"));
        }

        final Set<AlertInfo> alerts = new HashSet<>();
        if (isInitialized(tokens)) {
            alerts.addAll(checkGlobalConfGenerationStatus());
            alerts.addAll(checkConfigurationSigningKey(SOURCE_TYPE_INTERNAL, tokens));
            if (getCenterTrustedAnchorsAllowed()) {
                alerts.addAll(checkConfigurationSigningKey(SOURCE_TYPE_EXTERNAL, tokens));
            }
        }
        return alerts;
    }

    private boolean isInitialized(List<TokenInfo> tokens) {
        return isNotBlank(systemParameterService.getInstanceIdentifier())
                && isNotBlank(systemParameterService.getCentralServerAddress())
                && softwareTokenInitialized(tokens);
    }

    private boolean softwareTokenInitialized(List<TokenInfo> tokens) {
        return tokens.stream()
                .anyMatch(token -> token.getId().equals(SOFTWARE_TOKEN_ID) && token.getStatus() != NOT_INITIALIZED);
    }

    private Set<AlertInfo> checkGlobalConfGenerationStatus() {
        final GlobalConfGenerationStatus status = globalConfGenerationStatus.get();

        if (status.getStatus() == UNKNOWN) {
            return Set.of(new AlertInfo("status.global_conf_generation.status_not_found"));
        } else if (status.getStatus() == FAILURE) {
            return Set.of(new AlertInfo("status.global_conf_generation.failing", status.getTime()));
        } else if (isGlobalConfExpired(status)) {
            return Set.of(new AlertInfo("status.global_conf_generation.global_conf_expired"));
        }

        return Set.of();
    }

    private boolean isGlobalConfExpired(GlobalConfGenerationStatus status) {
        return SECONDS.between(status.getTime(), TimeUtils.now()) > systemParameterService.getConfExpireIntervalSeconds();
    }

    private Set<AlertInfo> checkConfigurationSigningKey(String sourceType, List<TokenInfo> tokens) {
        final Optional<ConfigurationSigningKey> signingKeyOptional = configurationSigningKeysService.findActiveForSource(sourceType);
        if (signingKeyOptional.isEmpty()) {
            return Set.of(new AlertInfo(format("status.signing_key.%s.missing", sourceType)));
        }
        final ConfigurationSigningKey signingKey = signingKeyOptional.get();
        for (final TokenInfo token : tokens) {
            for (final KeyInfo keyInfo : token.getKeyInfo()) {
                if (keyInfo.getId().equals(signingKey.getKeyIdentifier())) {
                    if (!token.isActive()) {
                        return Set.of(new AlertInfo(format("status.signing_key.%s.token_not_active", sourceType)));
                    } else if (!keyInfo.isAvailable()) {
                        return Set.of(new AlertInfo(format("status.signing_key.%s.key_not_available", sourceType)));
                    }
                    return Set.of();
                }
            }
        }
        return Set.of(new AlertInfo(format("status.signing_key.%s.token_not_found", sourceType)));
    }

}
