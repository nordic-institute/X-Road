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
package org.niis.xroad.centralserver.restapi.service;

import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.centralserver.restapi.service.exception.NotFoundException;
import org.niis.xroad.centralserver.restapi.service.exception.SigningKeyException;
import org.niis.xroad.cs.admin.api.facade.SignerProxyFacade;
import org.niis.xroad.cs.admin.core.entity.ConfigurationSigningKeyEntity;
import org.niis.xroad.cs.admin.core.entity.ConfigurationSourceEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.ConfigurationSigningKeyMapper;
import org.niis.xroad.cs.admin.core.entity.mapper.ConfigurationSigningKeyMapperImpl;
import org.niis.xroad.cs.admin.core.repository.ConfigurationSigningKeyRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.AuditEventHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ee.ria.xroad.signer.protocol.dto.TokenStatusInfo.OK;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DELETE_EXTERNAL_CONFIGURATION_SIGNING_KEY;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DELETE_INTERNAL_CONFIGURATION_SIGNING_KEY;

@ExtendWith(MockitoExtension.class)
class ConfigurationSigningKeysServiceImplTest {

    @Mock
    private AuditEventHelper auditEventHelper;
    @Mock
    private ConfigurationSigningKeyRepository configurationSigningKeyRepository;
    @Mock
    private AuditDataHelper auditDataHelper;
    @Mock
    private SignerProxyFacade signerProxyFacade;
    @Spy
    private final ConfigurationSigningKeyMapper configurationSigningKeyMapper = new ConfigurationSigningKeyMapperImpl();

    @InjectMocks
    private ConfigurationSigningKeysServiceImpl configurationSigningKeysServiceImpl;

    @Test
    void deleteKeyNotFoundShouldThrowException() {
        assertThatThrownBy(() -> configurationSigningKeysServiceImpl.deleteKey("some_random_id"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Signing key not found");
    }

    @Test
    void deleteActiveKeyShouldThrowException() {
        ConfigurationSigningKeyEntity signingKeyEntity = createConfigurationSigningEntity("INTERNAL", true);
        when(configurationSigningKeyRepository.findByKeyIdentifier(signingKeyEntity.getKeyIdentifier()))
                .thenReturn(Optional.of(signingKeyEntity));

        assertThatThrownBy(() -> configurationSigningKeysServiceImpl.deleteKey(signingKeyEntity.getKeyIdentifier()))
                .isInstanceOf(SigningKeyException.class)
                .hasMessage("Active configuration signing key cannot be deleted");
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
        when(signerProxyFacade.getToken(signingKeyEntity.getTokenIdentifier())).thenReturn(createTokenInfo(true, true, List.of()));
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
    }


    private KeyInfo createKeyInfo() {
        return new ee.ria.xroad.signer.protocol.dto.KeyInfo(true, KeyUsageInfo.SIGNING, "keyFriendlyName",
                "keyId", "keyLabel", "keyPublicKey", List.of(), List.of(), "keySignMechanismName");
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
        configurationSigningKey.setTokenIdentifier("tokenIdentifier");

        ConfigurationSourceEntity configurationSource = new ConfigurationSourceEntity();
        configurationSource.setSourceType(sourceType);
        if (activeSigningKey) {
            configurationSource.setConfigurationSigningKey(configurationSigningKey);
        }

        configurationSigningKey.setConfigurationSource(configurationSource);
        return configurationSigningKey;
    }

}
