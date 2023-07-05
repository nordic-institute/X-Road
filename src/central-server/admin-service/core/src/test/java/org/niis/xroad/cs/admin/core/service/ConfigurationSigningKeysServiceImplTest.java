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
import ee.ria.xroad.signer.protocol.dto.TokenStatusInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSigningKeyWithDetails;
import org.niis.xroad.cs.admin.api.dto.HAConfigStatus;
import org.niis.xroad.cs.admin.api.dto.KeyLabel;
import org.niis.xroad.cs.admin.api.facade.SignerProxyFacade;
import org.niis.xroad.cs.admin.api.service.ConfigurationAnchorService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
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

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static ee.ria.xroad.signer.protocol.dto.TokenStatusInfo.OK;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.api.domain.ConfigurationSourceType.EXTERNAL;
import static org.niis.xroad.cs.admin.api.domain.ConfigurationSourceType.INTERNAL;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DELETE_EXTERNAL_CONFIGURATION_SIGNING_KEY;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DELETE_INTERNAL_CONFIGURATION_SIGNING_KEY;

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
    private ConfigurationAnchorService configurationAnchorService;
    @Spy
    private final ConfigurationSigningKeyMapper configurationSigningKeyMapper = new ConfigurationSigningKeyMapperImpl();
    @Spy
    private final ConfigurationSigningKeyWithDetailsMapper withDetailsMapper = new ConfigurationSigningKeyWithDetailsMapperImpl();

    private ConfigurationSigningKeysServiceImpl configurationSigningKeysServiceImpl;
    private final HAConfigStatus haConfigStatus = new HAConfigStatus("haNodeName", false);

    @BeforeEach
    void beforeEach() {
        configurationSigningKeysServiceImpl = new ConfigurationSigningKeysServiceImpl(systemParameterService,
                configurationAnchorService,
                configurationSigningKeyRepository,
                configurationSourceRepository,
                configurationSigningKeyMapper,
                withDetailsMapper,
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
                .hasMessage("Signing key not found");
    }


    @Test
    void deleteActiveKeyShouldThrowException() throws Exception {
        ConfigurationSigningKeyEntity signingKeyEntity = createConfigurationSigningEntity("INTERNAL", true);
        when(signerProxyFacade.getToken(TOKEN_ID)).thenReturn(createToken(List.of()));
        when(configurationSigningKeyRepository.findByKeyIdentifier(signingKeyEntity.getKeyIdentifier()))
                .thenReturn(Optional.of(signingKeyEntity));

        assertThatThrownBy(() -> configurationSigningKeysServiceImpl.deleteKey(signingKeyEntity.getKeyIdentifier()))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessage("Signing key action not possible");
    }

    @Test
    void deleteKeyErrorGettingTokenFromSignerProxyShouldThrowException() throws Exception {
        ConfigurationSigningKeyEntity signingKeyEntity = createConfigurationSigningEntity("INTERNAL", false);
        when(configurationSigningKeyRepository.findByKeyIdentifier(signingKeyEntity.getKeyIdentifier()))
                .thenReturn(Optional.of(signingKeyEntity));
        when(signerProxyFacade.getToken(signingKeyEntity.getTokenIdentifier())).thenThrow(new Exception());

        assertThatThrownBy(() -> configurationSigningKeysServiceImpl.deleteKey(signingKeyEntity.getKeyIdentifier()))
                .isInstanceOf(SigningKeyException.class)
                .hasMessage("Error deleting signing key");
    }

    @Test
    void deleteKeyErrorDeletingKeyThroughSignerShouldThrowException() throws Exception {
        ConfigurationSigningKeyEntity signingKeyEntity = createConfigurationSigningEntity("INTERNAL", false);
        when(configurationSigningKeyRepository.findByKeyIdentifier(signingKeyEntity.getKeyIdentifier()))
                .thenReturn(Optional.of(signingKeyEntity));
        when(signerProxyFacade.getToken(signingKeyEntity.getTokenIdentifier()))
                .thenReturn(createTokenInfo(true, true, List.of()));
        doThrow(new Exception()).when(signerProxyFacade).deleteKey(signingKeyEntity.getKeyIdentifier(), true);

        assertThatThrownBy(() -> configurationSigningKeysServiceImpl.deleteKey(signingKeyEntity.getKeyIdentifier()))
                .isInstanceOf(SigningKeyException.class)
                .hasMessage("Error deleting signing key");
        verify(configurationSigningKeyRepository).deleteByKeyIdentifier(signingKeyEntity.getKeyIdentifier());
    }

    @Test
    void deleteInternalConfigurationSigningKey() throws Exception {
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
        verify(configurationAnchorService).recreateAnchor(INTERNAL, false);
    }

    @Test
    void deleteExternalConfigurationSigningKey() throws Exception {
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
        verify(configurationAnchorService).recreateAnchor(EXTERNAL, false);
    }

    @Test
    void shouldAddSigningKey() throws Exception {
        when(configurationSourceRepository.findBySourceTypeOrCreate(INTERNAL_CONFIGURATION, haConfigStatus))
                .thenReturn(configurationSourceEntity);
        when(signerProxyFacade.getToken(TOKEN_ID)).thenReturn(createToken(List.of()));
        when(signerProxyFacade.generateKey(TOKEN_ID, KEY_LABEL)).thenReturn(createKeyInfo("keyId"));
        when(signerProxyFacade.generateSelfSignedCert(eq(KEY_ID), isA(ClientId.Conf.class),
                eq(KeyUsageInfo.SIGNING),
                eq("N/A"),
                eq(SIGNING_KEY_CERT_NOT_BEFORE),
                eq(SIGNING_KEY_CERT_NOT_AFTER))
        ).thenReturn(new byte[0]);
        when(systemParameterService.getInstanceIdentifier()).thenReturn(INSTANCE);

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
    void shouldNotAddMoreThanTwoSigningKeys() throws Exception {
        ConfigurationSigningKeyEntity key1 = createConfigurationSigningEntity(INTERNAL_CONFIGURATION, true);
        ConfigurationSigningKeyEntity key2 = createConfigurationSigningEntity(INTERNAL_CONFIGURATION, false);
        when(configurationSourceRepository.findBySourceTypeOrCreate(INTERNAL_CONFIGURATION, haConfigStatus))
                .thenReturn(configurationSourceEntity);
        when(configurationSigningKeyRepository.findByKeyIdentifierIn(Set.of("keyId"))).thenReturn(List.of(key1, key2));
        when(signerProxyFacade.getToken(TOKEN_ID)).thenReturn(createToken(List.of(createKeyInfo("keyId"))));

        assertThatThrownBy(() -> configurationSigningKeysServiceImpl.addKey(INTERNAL_CONFIGURATION, TOKEN_ID, KEY_LABEL))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessage("Token action not possible");

        verify(signerProxyFacade).getToken(TOKEN_ID);
        verifyNoMoreInteractions(signerProxyFacade);
    }

    private TokenInfo createToken(List<KeyInfo> keys) {
        return new TokenInfo(null, "tokenName", TOKEN_ID,
                true, true, true, "serialNumber", "tokenLabel",
                1, TokenStatusInfo.OK, keys, new HashMap<>()
        );
    }

    @Test
    void activateKeyShouldFailWhenKeyNotFound() {
        assertThatThrownBy(() -> configurationSigningKeysServiceImpl.activateKey("some_random_id"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Signing key not found");
    }

    @Test
    void activateKeyShouldFailWhenTokenNotLoggedIn() throws Exception {
        final var tokenInfo = createTokenInfo(FALSE, TRUE, List.of());
        final var signingKeyEntity = createConfigurationSigningEntity("EXTERNAL", TRUE);
        when(configurationSigningKeyRepository.findByKeyIdentifier(signingKeyEntity.getKeyIdentifier()))
                .thenReturn(Optional.of(signingKeyEntity));
        when(signerProxyFacade.getToken(signingKeyEntity.getTokenIdentifier())).thenReturn(tokenInfo);


        assertThatThrownBy(() -> configurationSigningKeysServiceImpl.activateKey(signingKeyEntity.getKeyIdentifier()))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessage("Signing key action not possible");
    }

    @Test
    void activateKeyShouldSucceed() throws Exception {
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
    void activateKeyErrorGettingTokenFromSignerProxyShouldThrowException() throws Exception {
        ConfigurationSigningKeyEntity signingKeyEntity = createConfigurationSigningEntity("INTERNAL", false);
        when(configurationSigningKeyRepository.findByKeyIdentifier(signingKeyEntity.getKeyIdentifier()))
                .thenReturn(Optional.of(signingKeyEntity));
        when(signerProxyFacade.getToken(signingKeyEntity.getTokenIdentifier())).thenThrow(new Exception());

        assertThatThrownBy(() -> configurationSigningKeysServiceImpl.activateKey(signingKeyEntity.getKeyIdentifier()))
                .isInstanceOf(SigningKeyException.class)
                .hasMessage("Error activating signing key");
    }

    @Test
    void findDetailedByToken() {
        TokenInfo token = createToken(List.of(createKeyInfo("keyId-1"), createKeyInfo("keyId-3")));

        when(configurationSigningKeyRepository.findByKeyIdentifierIn(Set.of("keyId-1", "keyId-3")))
                .thenReturn(List.of(
                        new ConfigurationSigningKeyEntity("keyId-1", new byte[0], Instant.now(), TOKEN_ID),
                        new ConfigurationSigningKeyEntity("keyId-3", new byte[0], Instant.now(), TOKEN_ID)
                ));

        final List<ConfigurationSigningKeyWithDetails> keysWithDetails = configurationSigningKeysServiceImpl.findDetailedByToken(token);

        assertThat(keysWithDetails).hasSize(2);
        assertThat(keysWithDetails).extracting("keyIdentifier").containsExactly("keyId-1", "keyId-3");
    }

    private KeyInfo createKeyInfo(String keyIdentifier) {
        return new ee.ria.xroad.signer.protocol.dto.KeyInfo(true, KeyUsageInfo.SIGNING, "keyFriendlyName",
                keyIdentifier, "keyLabel", "keyPublicKey", List.of(), List.of(), "keySignMechanismName");
    }

    private TokenInfo createTokenInfo(boolean active, boolean available, List<KeyInfo> keyInfos) {
        return new TokenInfo(
                "type", "TOKEN_FRIENDLY_NAME", "TOKEN_ID", false, available,
                active, "TOKEN_SERIAL_NUMBER", "label", 13, OK, keyInfos, Map.of()
        );
    }

    private ConfigurationSigningKeyEntity createConfigurationSigningEntity(
            String sourceType, boolean activeSigningKey) {
        ConfigurationSigningKeyEntity configurationSigningKey = new ConfigurationSigningKeyEntity();
        configurationSigningKey.setKeyIdentifier("keyIdentifier");
        configurationSigningKey.setCert("keyCert".getBytes());
        configurationSigningKey.setKeyGeneratedAt(Instant.now());
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
