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

import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.TimeUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSigningKeyWithDetails;
import org.niis.xroad.cs.admin.api.dto.HAConfigStatus;
import org.niis.xroad.cs.admin.api.dto.KeyLabel;
import org.niis.xroad.cs.admin.api.facade.SignerProxyFacade;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.core.config.KeyAlgorithmConfig;
import org.niis.xroad.cs.admin.core.entity.ConfigurationSigningKeyEntity;
import org.niis.xroad.cs.admin.core.entity.ConfigurationSourceEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.ConfigurationSigningKeyMapper;
import org.niis.xroad.cs.admin.core.entity.mapper.ConfigurationSigningKeyMapperImpl;
import org.niis.xroad.cs.admin.core.entity.mapper.ConfigurationSigningKeyWithDetailsMapper;
import org.niis.xroad.cs.admin.core.entity.mapper.ConfigurationSigningKeyWithDetailsMapperImpl;
import org.niis.xroad.cs.admin.core.exception.SigningKeyException;
import org.niis.xroad.cs.admin.core.repository.ConfigurationSigningKeyRepository;
import org.niis.xroad.cs.admin.core.repository.ConfigurationSourceRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.AuditEventHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.protocol.dto.KeyInfoProto;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.niis.xroad.signer.protocol.dto.TokenInfoProto;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DELETE_EXTERNAL_CONFIGURATION_SIGNING_KEY;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DELETE_INTERNAL_CONFIGURATION_SIGNING_KEY;
import static org.niis.xroad.signer.protocol.dto.TokenStatusInfo.OK;

@ExtendWith(MockitoExtension.class)
class ConfigurationSigningKeysServiceImplTest {
    private static final String INTERNAL_CONFIGURATION = "internal";
    private static final String TOKEN_ID = "token";
    private static final String KEY_LABEL = "keyLabel";
    private static final String KEY_ID = "keyId";
    private static final Date SIGNING_KEY_CERT_NOT_BEFORE = Date.from(Instant.EPOCH);
    private static final Date SIGNING_KEY_CERT_NOT_AFTER = Date.from(Instant.parse("2038-01-01T00:00:00Z"));
    public static final String INSTANCE = "XROAD-INSTANCE";
    @Mock
    private AuditEventHelper auditEventHelper;
    @Mock
    private ConfigurationSigningKeyRepository configurationSigningKeyRepository;
    @Mock
    private ConfigurationSourceRepository configurationSourceRepository;
    @Spy
    private TokenActionsResolverImpl tokenActionsResolver;
    @Spy
    private SigningKeyActionsResolver signingKeyActionsResolver;
    @Mock
    private ConfigurationSourceEntity configurationSourceEntity;
    @Mock
    private AuditDataHelper auditDataHelper;
    @Mock
    private SignerProxyFacade signerProxyFacade;
    @Mock
    private SystemParameterService systemParameterService;
    @Mock
    private KeyAlgorithmConfig keyAlgorithmConfig;
    @Spy
    private final ConfigurationSigningKeyMapper configurationSigningKeyMapper = new ConfigurationSigningKeyMapperImpl();
    @Spy
    private final ConfigurationSigningKeyWithDetailsMapper withDetailsMapper = new ConfigurationSigningKeyWithDetailsMapperImpl();

    private ConfigurationSigningKeysServiceImpl configurationSigningKeysServiceImpl;
    private final HAConfigStatus haConfigStatus = new HAConfigStatus("haNodeName", false);

    @BeforeEach
    void beforeEach() {
        configurationSigningKeysServiceImpl = new ConfigurationSigningKeysServiceImpl(systemParameterService,
                configurationSigningKeyRepository,
                configurationSourceRepository,
                configurationSigningKeyMapper,
                withDetailsMapper,
                keyAlgorithmConfig,
                signerProxyFacade,
                tokenActionsResolver,
                signingKeyActionsResolver,
                auditEventHelper,
                auditDataHelper,
                haConfigStatus);
    }

    @Test
    void deleteKeyNotFoundShouldThrowException() {
        assertThatThrownBy(() -> configurationSigningKeysServiceImpl.deleteKey("some_random_id"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Error[code=signing_key_not_found]");
    }


    @Test
    void deleteActiveKeyShouldThrowException() {
        ConfigurationSigningKeyEntity signingKeyEntity = createConfigurationSigningEntity("INTERNAL", true);
        when(signerProxyFacade.getToken(TOKEN_ID)).thenReturn(createToken(List.of()));
        when(configurationSigningKeyRepository.findByKeyIdentifier(signingKeyEntity.getKeyIdentifier()))
                .thenReturn(Optional.of(signingKeyEntity));

        assertThatThrownBy(() -> configurationSigningKeysServiceImpl.deleteKey(signingKeyEntity.getKeyIdentifier()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Error[code=signing_key_action_not_possible]");
    }

    @Test
    void deleteKeyErrorGettingTokenFromSignerProxyShouldThrowException() {
        ConfigurationSigningKeyEntity signingKeyEntity = createConfigurationSigningEntity("INTERNAL", false);
        when(configurationSigningKeyRepository.findByKeyIdentifier(signingKeyEntity.getKeyIdentifier()))
                .thenReturn(Optional.of(signingKeyEntity));
        when(signerProxyFacade.getToken(signingKeyEntity.getTokenIdentifier()))
                .thenThrow(XrdRuntimeException.systemException(INTERNAL_ERROR).build());

        assertThatThrownBy(() -> configurationSigningKeysServiceImpl.deleteKey(signingKeyEntity.getKeyIdentifier()))
                .isInstanceOf(SigningKeyException.class)
                .hasMessage("Error[code=error_deleting_signing_key]");
    }

    @Test
    void deleteKeyErrorDeletingKeyThroughSignerShouldThrowException() {
        ConfigurationSigningKeyEntity signingKeyEntity = createConfigurationSigningEntity("INTERNAL", false);
        when(configurationSigningKeyRepository.findByKeyIdentifier(signingKeyEntity.getKeyIdentifier()))
                .thenReturn(Optional.of(signingKeyEntity));
        when(signerProxyFacade.getToken(signingKeyEntity.getTokenIdentifier()))
                .thenReturn(createTokenInfo(true, true, List.of()));
        doThrow(XrdRuntimeException.systemException(INTERNAL_ERROR).build())
                .when(signerProxyFacade).deleteKey(signingKeyEntity.getKeyIdentifier(), true);

        assertThatThrownBy(() -> configurationSigningKeysServiceImpl.deleteKey(signingKeyEntity.getKeyIdentifier()))
                .isInstanceOf(SigningKeyException.class)
                .hasMessage("Error[code=error_deleting_signing_key]");
        verify(configurationSigningKeyRepository).deleteByKeyIdentifier(signingKeyEntity.getKeyIdentifier());
    }

    @Test
    void deleteInternalConfigurationSigningKey() {
        ConfigurationSigningKeyEntity signingKeyEntity = createConfigurationSigningEntity("INTERNAL", false);
        when(configurationSigningKeyRepository.findByKeyIdentifier(signingKeyEntity.getKeyIdentifier()))
                .thenReturn(Optional.of(signingKeyEntity));
        TokenInfo tokenInfo = createTokenInfo(true, true, List.of());
        when(signerProxyFacade.getToken(signingKeyEntity.getTokenIdentifier())).thenReturn(tokenInfo);

        configurationSigningKeysServiceImpl.deleteKey(signingKeyEntity.getKeyIdentifier());

        verify(auditEventHelper).changeRequestScopedEvent(DELETE_INTERNAL_CONFIGURATION_SIGNING_KEY);
        verify(auditDataHelper).put(RestApiAuditProperty.TOKEN_ID, signingKeyEntity.getTokenIdentifier());
        verify(auditDataHelper).put(RestApiAuditProperty.KEY_ID, signingKeyEntity.getKeyIdentifier());
        verify(auditDataHelper).put(RestApiAuditProperty.TOKEN_SERIAL_NUMBER, tokenInfo.getSerialNumber());
        verify(auditDataHelper).put(RestApiAuditProperty.TOKEN_FRIENDLY_NAME, tokenInfo.getFriendlyName());
        verify(configurationSigningKeyRepository).deleteByKeyIdentifier(signingKeyEntity.getKeyIdentifier());
        verify(signerProxyFacade).deleteKey(signingKeyEntity.getKeyIdentifier(), true);
    }

    @Test
    void deleteExternalConfigurationSigningKey() {
        ConfigurationSigningKeyEntity signingKeyEntity = createConfigurationSigningEntity("EXTERNAL", false);
        when(configurationSigningKeyRepository.findByKeyIdentifier(signingKeyEntity.getKeyIdentifier()))
                .thenReturn(Optional.of(signingKeyEntity));
        TokenInfo tokenInfo = createTokenInfo(true, true, List.of());
        when(signerProxyFacade.getToken(signingKeyEntity.getTokenIdentifier())).thenReturn(tokenInfo);

        configurationSigningKeysServiceImpl.deleteKey(signingKeyEntity.getKeyIdentifier());

        verify(auditEventHelper).changeRequestScopedEvent(DELETE_EXTERNAL_CONFIGURATION_SIGNING_KEY);
        verify(auditDataHelper).put(RestApiAuditProperty.TOKEN_ID, signingKeyEntity.getTokenIdentifier());
        verify(auditDataHelper).put(RestApiAuditProperty.KEY_ID, signingKeyEntity.getKeyIdentifier());
        verify(auditDataHelper).put(RestApiAuditProperty.TOKEN_SERIAL_NUMBER, tokenInfo.getSerialNumber());
        verify(auditDataHelper).put(RestApiAuditProperty.TOKEN_FRIENDLY_NAME, tokenInfo.getFriendlyName());
        verify(configurationSigningKeyRepository).deleteByKeyIdentifier(signingKeyEntity.getKeyIdentifier());
        verify(signerProxyFacade).deleteKey(signingKeyEntity.getKeyIdentifier(), true);
    }

    @Test
    void shouldAddSigningKey() {
        when(configurationSourceRepository.findBySourceTypeOrCreate(INTERNAL_CONFIGURATION, haConfigStatus))
                .thenReturn(configurationSourceEntity);
        when(signerProxyFacade.getToken(TOKEN_ID)).thenReturn(createToken(List.of()));
        when(signerProxyFacade.generateKey(TOKEN_ID, KEY_LABEL, KeyAlgorithm.RSA)).thenReturn(createKeyInfo("keyId"));
        when(signerProxyFacade.generateSelfSignedCert(eq(KEY_ID), isA(ClientId.Conf.class),
                eq(KeyUsageInfo.SIGNING),
                eq("internalSigningKey"),
                eq(SIGNING_KEY_CERT_NOT_BEFORE),
                eq(SIGNING_KEY_CERT_NOT_AFTER))
        ).thenReturn(new byte[0]);
        when(systemParameterService.getInstanceIdentifier()).thenReturn(INSTANCE);
        when(keyAlgorithmConfig.getInternalKeyAlgorithm()).thenReturn(KeyAlgorithm.RSA);

        var result = configurationSigningKeysServiceImpl.addKey(INTERNAL_CONFIGURATION,
                TOKEN_ID, KEY_LABEL);

        verify(configurationSourceRepository, times(1)).saveAndFlush(configurationSourceEntity);

        assertThat(result.isActiveSourceSigningKey()).isEqualTo(Boolean.TRUE);
        assertThat(result.getAvailable()).isEqualTo(Boolean.TRUE);
        assertThat(result.getKeyIdentifier()).isEqualTo(KEY_ID);
        assertThat(result.getTokenIdentifier()).isEqualTo(TOKEN_ID);
        assertThat(result.getLabel()).isEqualTo(new KeyLabel(KEY_LABEL));
    }

    @Test
    void shouldNotAddMoreThanTwoSigningKeys() {
        ConfigurationSigningKeyEntity key1 = createConfigurationSigningEntity(INTERNAL_CONFIGURATION, true);
        ConfigurationSigningKeyEntity key2 = createConfigurationSigningEntity(INTERNAL_CONFIGURATION, false);
        when(configurationSourceRepository.findBySourceTypeOrCreate(INTERNAL_CONFIGURATION, haConfigStatus))
                .thenReturn(configurationSourceEntity);
        when(configurationSigningKeyRepository.findByKeyIdentifierIn(Set.of("keyId"))).thenReturn(List.of(key1, key2));
        when(signerProxyFacade.getToken(TOKEN_ID)).thenReturn(createToken(List.of(createKeyInfo("keyId"))));

        assertThatThrownBy(() -> configurationSigningKeysServiceImpl.addKey(INTERNAL_CONFIGURATION, TOKEN_ID, KEY_LABEL))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Error[code=token_action_not_possible]");

        verify(signerProxyFacade).getToken(TOKEN_ID);
        verifyNoMoreInteractions(signerProxyFacade);
    }

    private TokenInfo createToken(List<KeyInfo> keys) {
        final TokenInfoProto.Builder builder = TokenInfoProto.newBuilder()
                .setFriendlyName("tokenName")
                .setId(TOKEN_ID)
                .setReadOnly(true)
                .setAvailable(true)
                .setActive(true)
                .setSerialNumber("serialNumber")
                .setLabel("tokenLabel")
                .setStatus(OK);
        if (!keys.isEmpty()) {
            builder.addAllKeyInfo(keys.stream().map(KeyInfo::getMessage).collect(toList()));
        }
        return new TokenInfo(builder.build());
    }

    @Test
    void activateKeyShouldFailWhenKeyNotFound() {
        assertThatThrownBy(() -> configurationSigningKeysServiceImpl.activateKey("some_random_id"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Error[code=signing_key_not_found]");
    }

    @Test
    void activateKeyShouldFailWhenTokenNotLoggedIn() {
        final var tokenInfo = createTokenInfo(FALSE, TRUE, List.of());
        final var signingKeyEntity = createConfigurationSigningEntity("EXTERNAL", TRUE);
        when(configurationSigningKeyRepository.findByKeyIdentifier(signingKeyEntity.getKeyIdentifier()))
                .thenReturn(Optional.of(signingKeyEntity));
        when(signerProxyFacade.getToken(signingKeyEntity.getTokenIdentifier())).thenReturn(tokenInfo);

        assertThatThrownBy(() -> configurationSigningKeysServiceImpl.activateKey(signingKeyEntity.getKeyIdentifier()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Error[code=signing_key_action_not_possible]");
    }

    @Test
    void activateKeyShouldSucceed() {
        final var tokenInfo = createTokenInfo(TRUE, TRUE, List.of());
        final var signingKeyEntity = createConfigurationSigningEntity("EXTERNAL", FALSE);
        when(configurationSigningKeyRepository.findByKeyIdentifier(signingKeyEntity.getKeyIdentifier()))
                .thenReturn(Optional.of(signingKeyEntity));
        when(signerProxyFacade.getToken(signingKeyEntity.getTokenIdentifier())).thenReturn(tokenInfo);

        configurationSigningKeysServiceImpl.activateKey(signingKeyEntity.getKeyIdentifier());

        assertThat(signingKeyEntity.getConfigurationSource().getConfigurationSigningKey()).isEqualTo(signingKeyEntity);

        verify(configurationSigningKeyRepository).save(signingKeyEntity);
    }

    @Test
    void activateKeyErrorGettingTokenFromSignerProxyShouldThrowException() {
        ConfigurationSigningKeyEntity signingKeyEntity = createConfigurationSigningEntity("INTERNAL", false);
        when(configurationSigningKeyRepository.findByKeyIdentifier(signingKeyEntity.getKeyIdentifier()))
                .thenReturn(Optional.of(signingKeyEntity));
        when(signerProxyFacade.getToken(signingKeyEntity.getTokenIdentifier()))
                .thenThrow(XrdRuntimeException.systemException(INTERNAL_ERROR).build());

        assertThatThrownBy(() -> configurationSigningKeysServiceImpl.activateKey(signingKeyEntity.getKeyIdentifier()))
                .isInstanceOf(SigningKeyException.class)
                .hasMessage("Error[code=error_activating_signing_key]");
    }

    @Test
    void findDetailedByToken() {
        TokenInfo token = createToken(List.of(createKeyInfo("keyId-1"), createKeyInfo("keyId-3")));

        when(configurationSigningKeyRepository.findByKeyIdentifierIn(Set.of("keyId-1", "keyId-3")))
                .thenReturn(List.of(
                        new ConfigurationSigningKeyEntity("keyId-1", new byte[0], TimeUtils.now(), TOKEN_ID),
                        new ConfigurationSigningKeyEntity("keyId-3", new byte[0], TimeUtils.now(), TOKEN_ID)
                ));

        final List<ConfigurationSigningKeyWithDetails> keysWithDetails = configurationSigningKeysServiceImpl.findDetailedByToken(token);

        assertThat(keysWithDetails).hasSize(2);
        assertThat(keysWithDetails).extracting("keyIdentifier").containsExactly("keyId-1", "keyId-3");
    }

    private KeyInfo createKeyInfo(String keyIdentifier) {
        return new KeyInfo(KeyInfoProto.newBuilder()
                .setAvailable(true)
                .setUsage(KeyUsageInfo.SIGNING)
                .setFriendlyName("keyFriendlyName")
                .setId(keyIdentifier)
                .setLabel("keyLabel")
                .setPublicKey("keyPublicKey")
                .setSignMechanismName(SignMechanism.CKM_RSA_PKCS.name())
                .build());
    }

    private TokenInfo createTokenInfo(boolean active, boolean available, List<KeyInfo> keyInfos) {
        final TokenInfoProto.Builder builder = TokenInfoProto.newBuilder()
                .setType("type")
                .setFriendlyName("TOKEN_FRIENDLY_NAME")
                .setId("TOKEN_ID")
                .setReadOnly(false)
                .setAvailable(available)
                .setActive(active)
                .setSerialNumber("TOKEN_SERIAL_NUMBER")
                .setLabel("label")
                .setStatus(OK);
        if (!keyInfos.isEmpty()) {
            builder.addAllKeyInfo(keyInfos.stream().map(KeyInfo::getMessage).collect(toList()));
        }
        return new TokenInfo(builder
                .build());
    }

    private ConfigurationSigningKeyEntity createConfigurationSigningEntity(
            String sourceType, boolean activeSigningKey) {
        ConfigurationSigningKeyEntity configurationSigningKey = new ConfigurationSigningKeyEntity();
        configurationSigningKey.setKeyIdentifier("keyIdentifier");
        configurationSigningKey.setCert("keyCert".getBytes());
        configurationSigningKey.setKeyGeneratedAt(TimeUtils.now());
        configurationSigningKey.setTokenIdentifier(TOKEN_ID);

        ConfigurationSourceEntity configurationSource = new ConfigurationSourceEntity();
        configurationSource.setSourceType(sourceType);
        if (activeSigningKey) {
            configurationSource.setConfigurationSigningKey(configurationSigningKey);
        }

        configurationSigningKey.setConfigurationSource(configurationSource);
        return configurationSigningKey;
    }
}
