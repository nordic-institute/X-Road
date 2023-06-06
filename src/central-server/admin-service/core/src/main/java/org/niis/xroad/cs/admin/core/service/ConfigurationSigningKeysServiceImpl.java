/*
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.admin.core.service;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSigningKey;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSigningKeyWithDetails;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSourceType;
import org.niis.xroad.cs.admin.api.dto.HAConfigStatus;
import org.niis.xroad.cs.admin.api.dto.PossibleTokenAction;
import org.niis.xroad.cs.admin.api.facade.SignerProxyFacade;
import org.niis.xroad.cs.admin.api.service.ConfigurationAnchorService;
import org.niis.xroad.cs.admin.api.service.ConfigurationSigningKeysService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.api.service.TokenActionsResolver;
import org.niis.xroad.cs.admin.core.entity.ConfigurationSigningKeyEntity;
import org.niis.xroad.cs.admin.core.entity.ConfigurationSourceEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.ConfigurationSigningKeyMapper;
import org.niis.xroad.cs.admin.core.entity.mapper.ConfigurationSigningKeyWithDetailsMapper;
import org.niis.xroad.cs.admin.core.exception.SignerProxyException;
import org.niis.xroad.cs.admin.core.exception.SigningKeyException;
import org.niis.xroad.cs.admin.core.repository.ConfigurationSigningKeyRepository;
import org.niis.xroad.cs.admin.core.repository.ConfigurationSourceRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.AuditEventHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.util.CryptoUtils.DEFAULT_CERT_HASH_ALGORITHM_ID;
import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHashDelimited;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.niis.xroad.cs.admin.api.domain.ConfigurationSourceType.EXTERNAL;
import static org.niis.xroad.cs.admin.api.domain.ConfigurationSourceType.INTERNAL;
import static org.niis.xroad.cs.admin.api.dto.PossibleKeyAction.ACTIVATE;
import static org.niis.xroad.cs.admin.api.dto.PossibleKeyAction.DELETE;
import static org.niis.xroad.cs.admin.api.dto.PossibleTokenAction.GENERATE_EXTERNAL_KEY;
import static org.niis.xroad.cs.admin.api.dto.PossibleTokenAction.GENERATE_INTERNAL_KEY;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.ERROR_ACTIVATING_SIGNING_KEY;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.ERROR_DELETING_SIGNING_KEY;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.KEY_GENERATION_FAILED;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.SIGNING_KEY_NOT_FOUND;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.ACTIVATE_EXTERNAL_CONFIGURATION_SIGNING_KEY;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.ACTIVATE_INTERNAL_CONFIGURATION_SIGNING_KEY;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DELETE_EXTERNAL_CONFIGURATION_SIGNING_KEY;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DELETE_INTERNAL_CONFIGURATION_SIGNING_KEY;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.GENERATE_EXTERNAL_CONFIGURATION_SIGNING_KEY;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.GENERATE_INTERNAL_CONFIGURATION_SIGNING_KEY;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CERT_HASH;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CERT_HASH_ALGORITHM;

@Service
@Transactional
@RequiredArgsConstructor
public class ConfigurationSigningKeysServiceImpl extends AbstractTokenConsumer implements ConfigurationSigningKeysService {
    private static final Date SIGNING_KEY_CERT_NOT_BEFORE = Date.from(Instant.EPOCH);
    private static final Date SIGNING_KEY_CERT_NOT_AFTER = Date.from(Instant.parse("2038-01-01T00:00:00Z"));

    private final SystemParameterService systemParameterService;
    private final ConfigurationAnchorService configurationAnchorService;
    private final ConfigurationSigningKeyRepository configurationSigningKeyRepository;
    private final ConfigurationSourceRepository configurationSourceRepository;
    private final ConfigurationSigningKeyMapper configurationSigningKeyMapper;
    private final ConfigurationSigningKeyWithDetailsMapper configurationSigningKeyWithDetailsMapper;
    private final SignerProxyFacade signerProxyFacade;
    private final TokenActionsResolver tokenActionsResolver;
    private final SigningKeyActionsResolver signingKeyActionsResolver;
    private final AuditEventHelper auditEventHelper;
    private final AuditDataHelper auditDataHelper;
    private final HAConfigStatus haConfigStatus;

    @Override
    public List<ConfigurationSigningKey> findByTokenIdentifier(final TokenInfo tokenInfo) {
        final Set<String> keyIds = tokenInfo.getKeyInfo().stream().map(KeyInfo::getId).collect(toSet());
        return configurationSigningKeyRepository.findByKeyIdentifierIn(keyIds).stream()
                .map(configurationSigningKeyMapper::toTarget)
                .collect(toList());
    }

    @Override
    public List<ConfigurationSigningKeyWithDetails> findDetailedByToken(final TokenInfo token) {
        var keyInfoMap = Optional.ofNullable(token.getKeyInfo())
                .orElseGet(List::of).stream()
                .collect(Collectors.toMap(KeyInfo::getId, key -> key));

        return configurationSigningKeyRepository.findByKeyIdentifierIn(keyInfoMap.keySet())
                .stream()
                .map(signingKey -> {
                    var keyInfo = keyInfoMap.get(signingKey.getKeyIdentifier());
                    var key = configurationSigningKeyMapper.toTarget(signingKey);
                    return mapWithDetails(token, key, keyInfo);
                })
                .collect(toList());
    }

    private ConfigurationSigningKeyWithDetails mapWithDetails(final ee.ria.xroad.signer.protocol.dto.TokenInfo token,
                                                              ConfigurationSigningKey signingKey,
                                                              KeyInfo keyInfo) {
        var possibleActions = List.copyOf(signingKeyActionsResolver.resolveActions(token, signingKey));

        return configurationSigningKeyWithDetailsMapper.toTarget(
                signingKey,
                possibleActions,
                keyInfo.getLabel(),
                keyInfo.isAvailable());
    }

    @Override
    public void deleteKey(String identifier) {
        ConfigurationSigningKey signingKey = configurationSigningKeyRepository.findByKeyIdentifier(identifier)
                .map(configurationSigningKeyMapper::toTarget)
                .orElseThrow(ConfigurationSigningKeysServiceImpl::notFoundException);

        final ConfigurationSourceType configurationSourceType = signingKey.getSourceType();
        if (configurationSourceType == INTERNAL) {
            auditEventHelper.changeRequestScopedEvent(DELETE_INTERNAL_CONFIGURATION_SIGNING_KEY);
        } else if (configurationSourceType == EXTERNAL) {
            auditEventHelper.changeRequestScopedEvent(DELETE_EXTERNAL_CONFIGURATION_SIGNING_KEY);
        }
        auditDataHelper.put(RestApiAuditProperty.TOKEN_ID, signingKey.getTokenIdentifier());
        auditDataHelper.put(RestApiAuditProperty.KEY_ID, signingKey.getKeyIdentifier());
        try {
            TokenInfo tokenInfo = signerProxyFacade.getToken(signingKey.getTokenIdentifier());
            signingKeyActionsResolver.requireAction(DELETE, tokenInfo, signingKey);

            auditDataHelper.put(RestApiAuditProperty.TOKEN_SERIAL_NUMBER, tokenInfo.getSerialNumber());
            auditDataHelper.put(RestApiAuditProperty.TOKEN_FRIENDLY_NAME, tokenInfo.getFriendlyName());

            configurationSigningKeyRepository.deleteByKeyIdentifier(identifier);
            signerProxyFacade.deleteKey(signingKey.getKeyIdentifier(), true);
        } catch (ValidationFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new SigningKeyException(ERROR_DELETING_SIGNING_KEY, e);
        }

        configurationAnchorService.recreateAnchor(configurationSourceType, false);
    }

    @Override
    public void activateKey(final String keyIdentifier) {
        final var signingKeyEntity = configurationSigningKeyRepository.findByKeyIdentifier(keyIdentifier)
                .orElseThrow(ConfigurationSigningKeysServiceImpl::notFoundException);
        final var signingKey = configurationSigningKeyMapper.toTarget(signingKeyEntity);

        if (signingKey.getSourceType() == INTERNAL) {
            auditEventHelper.changeRequestScopedEvent(ACTIVATE_INTERNAL_CONFIGURATION_SIGNING_KEY);
        } else if (signingKey.getSourceType() == EXTERNAL) {
            auditEventHelper.changeRequestScopedEvent(ACTIVATE_EXTERNAL_CONFIGURATION_SIGNING_KEY);
        }

        auditDataHelper.put(RestApiAuditProperty.TOKEN_ID, signingKeyEntity.getTokenIdentifier());
        auditDataHelper.put(RestApiAuditProperty.KEY_ID, signingKeyEntity.getKeyIdentifier());

        try {
            TokenInfo tokenInfo = signerProxyFacade.getToken(signingKeyEntity.getTokenIdentifier());
            signingKeyActionsResolver.requireAction(ACTIVATE, tokenInfo, signingKey);

            auditDataHelper.put(RestApiAuditProperty.TOKEN_SERIAL_NUMBER, tokenInfo.getSerialNumber());
            auditDataHelper.put(RestApiAuditProperty.TOKEN_FRIENDLY_NAME, tokenInfo.getFriendlyName());

            activateKey(signingKeyEntity);
        } catch (ValidationFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new SigningKeyException(ERROR_ACTIVATING_SIGNING_KEY, e);
        }
    }

    public Optional<ConfigurationSigningKey> findActiveForSource(String sourceType) {
        return configurationSigningKeyRepository.findActiveForSource(sourceType, haConfigStatus.getCurrentHaNodeName())
                .map(configurationSigningKeyMapper::toTarget);
    }

    @Override
    public ConfigurationSigningKeyWithDetails addKey(String sourceType, String tokenId, String keyLabel) {
        final ConfigurationSourceType configurationSourceType = ConfigurationSourceType.valueOf(sourceType.toUpperCase());
        var response = new ConfigurationSigningKey();
        response.setActiveSourceSigningKey(Boolean.FALSE);

        ConfigurationSourceEntity configurationSourceEntity = configurationSourceRepository
                .findBySourceTypeOrCreate(sourceType.toLowerCase(), haConfigStatus);

        final TokenInfo tokenInfo = getToken(tokenId);
        final PossibleTokenAction action = INTERNAL.equals(configurationSourceType)
                ? GENERATE_INTERNAL_KEY
                : GENERATE_EXTERNAL_KEY;

        if (configurationSourceType == INTERNAL) {
            auditEventHelper.changeRequestScopedEvent(GENERATE_INTERNAL_CONFIGURATION_SIGNING_KEY);
        } else if (configurationSourceType == EXTERNAL) {
            auditEventHelper.changeRequestScopedEvent(GENERATE_EXTERNAL_CONFIGURATION_SIGNING_KEY);
        }
        auditDataHelper.put(RestApiAuditProperty.TOKEN_ID, tokenInfo.getId());
        auditDataHelper.put(RestApiAuditProperty.TOKEN_SERIAL_NUMBER, tokenInfo.getSerialNumber());
        auditDataHelper.put(RestApiAuditProperty.TOKEN_FRIENDLY_NAME, tokenInfo.getFriendlyName());

        tokenActionsResolver.requireAction(action, tokenInfo, findByTokenIdentifier(tokenInfo));

        KeyInfo keyInfo;
        try {
            keyInfo = signerProxyFacade.generateKey(tokenId, keyLabel);
            auditDataHelper.put(RestApiAuditProperty.KEY_ID, keyInfo.getId());
            auditDataHelper.put(RestApiAuditProperty.KEY_FRIENDLY_NAME, keyInfo.getFriendlyName());
        } catch (Exception e) {
            throw new SignerProxyException(KEY_GENERATION_FAILED, e);
        }

        final Instant generatedAt = Instant.now();

        final ClientId.Conf clientId = ClientId.Conf.create(systemParameterService.getInstanceIdentifier(),
                "selfsigned", UUID.randomUUID().toString());

        try {
            final byte[] selfSignedCert = signerProxyFacade.generateSelfSignedCert(keyInfo.getId(), clientId,
                    KeyUsageInfo.SIGNING,
                    "N/A",
                    SIGNING_KEY_CERT_NOT_BEFORE,
                    SIGNING_KEY_CERT_NOT_AFTER);
            auditDataHelper.put(CERT_HASH, calculateCertHexHashDelimited(selfSignedCert));
            auditDataHelper.put(CERT_HASH_ALGORITHM, DEFAULT_CERT_HASH_ALGORITHM_ID);

            ConfigurationSigningKeyEntity signingKey = new ConfigurationSigningKeyEntity(keyInfo.getId(),
                    selfSignedCert, generatedAt, tokenId);

            if (configurationSourceEntity.getConfigurationSigningKey() == null) {
                configurationSourceEntity.setConfigurationSigningKey(signingKey);
                response.setActiveSourceSigningKey(Boolean.TRUE);
            }
            configurationSourceEntity.getConfigurationSigningKeys().add(signingKey);
            signingKey.setConfigurationSource(configurationSourceEntity);

            configurationSigningKeyRepository.saveAndFlush(signingKey);
            configurationSourceRepository.saveAndFlush(configurationSourceEntity);

            response.setKeyIdentifier(keyInfo.getId())
                    .setKeyGeneratedAt(generatedAt)
                    .setTokenIdentifier(tokenId);

            configurationAnchorService.recreateAnchor(configurationSourceType, false);
            return mapWithDetails(tokenInfo, response, keyInfo);
        } catch (Exception e) {
            deleteKey(keyInfo.getId());
            throw new SignerProxyException(KEY_GENERATION_FAILED);
        }
    }

    @Override
    protected SignerProxyFacade getSignerProxyFacade() {
        return signerProxyFacade;
    }

    private void activateKey(ConfigurationSigningKeyEntity signingKey) {
        try {
            signingKey.getConfigurationSource().setConfigurationSigningKey(signingKey);
            configurationSigningKeyRepository.save(signingKey);
        } catch (Exception e) {
            throw new SigningKeyException(ERROR_ACTIVATING_SIGNING_KEY, e);
        }
    }

    private static NotFoundException notFoundException() {
        return new NotFoundException(SIGNING_KEY_NOT_FOUND);
    }
}
