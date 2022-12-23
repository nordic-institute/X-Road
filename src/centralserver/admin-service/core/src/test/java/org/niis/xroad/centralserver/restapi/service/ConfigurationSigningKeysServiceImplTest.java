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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.dto.TokenStatusInfo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.centralserver.restapi.service.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSigningKey;
import org.niis.xroad.cs.admin.api.dto.KeyLabel;
import org.niis.xroad.cs.admin.api.facade.SignerProxyFacade;
import org.niis.xroad.cs.admin.api.service.TokensService;
import org.niis.xroad.cs.admin.core.entity.ConfigurationSigningKeyEntity;
import org.niis.xroad.cs.admin.core.entity.ConfigurationSourceEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.ConfigurationSigningKeyMapper;
import org.niis.xroad.cs.admin.core.entity.mapper.ConfigurationSigningKeyMapperImpl;
import org.niis.xroad.cs.admin.core.repository.ConfigurationSigningKeyRepository;
import org.niis.xroad.cs.admin.core.repository.ConfigurationSourceRepository;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ee.ria.xroad.signer.protocol.dto.TokenStatusInfo.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigurationSigningKeysServiceImplTest {
    private static final String INTERNAL_CONFIGURATION = "internal";
    private static final String TOKEN_ID = "token";
    private static final String KEY_LABEL = "keyLabel";
    private static final String KEY_ID = "keyId";
    private static final Date SIGNING_KEY_CERT_NOT_BEFORE = Date.from(Instant.EPOCH);
    private static final Date SIGNING_KEY_CERT_NOT_AFTER = Date.from(Instant.parse("2038-01-01T00:00:00Z"));
    @Mock
    private ConfigurationSigningKeyRepository configurationSigningKeyRepository;
    @Mock
    private ConfigurationSourceRepository configurationSourceRepository;
    @Mock
    private ObjectProvider<TokensService> tokensServiceProvider;
    @Mock
    private TokensService tokensService;
    @Mock
    private SignerProxyFacade signerProxyFacade;
    @Spy
    private TokenActionsResolver tokenActionsResolver;
    @Mock
    private ConfigurationSourceEntity configurationSource;
    @Spy
    private final ConfigurationSigningKeyMapper configurationSigningKeyMapper = new ConfigurationSigningKeyMapperImpl();
    @InjectMocks
    private ConfigurationSigningKeysServiceImpl configurationSigningKeysServiceImpl;

    @Test
    void findByTokenIdentifier() {
        TokenInfo tokenInfo = createTokenInfo(true, true, List.of(createKeyInfo()));
        ConfigurationSigningKeyEntity configurationSigningKeyEntity = mock(ConfigurationSigningKeyEntity.class);
        ConfigurationSourceEntity configurationSourceEntity = mock(ConfigurationSourceEntity.class);
        when(configurationSigningKeyRepository.findByTokenIdentifier(tokenInfo.getId()))
                .thenReturn(List.of(configurationSigningKeyEntity));
        when(configurationSigningKeyEntity.getConfigurationSource()).thenReturn(configurationSourceEntity);
        when(configurationSourceEntity.getConfigurationSigningKey()).thenReturn(configurationSigningKeyEntity);

        List<ConfigurationSigningKey> configurationSigningKeys =
                configurationSigningKeysServiceImpl.findByTokenIdentifier(tokenInfo.getId());
        assertThat(configurationSigningKeys).hasSize(1);
    }

    @Test
    void shouldAddSigningKey() throws Exception {
        when(tokensServiceProvider.getObject()).thenReturn(tokensService);
        when(configurationSourceRepository.findBySourceType(INTERNAL_CONFIGURATION))
                .thenReturn(Optional.of(configurationSource));
        when(tokensService.getToken(TOKEN_ID)).thenReturn(createToken(new ArrayList<>()));
        when(signerProxyFacade.generateKey(TOKEN_ID, KEY_LABEL)).thenReturn(createKeyInfo());
        when(signerProxyFacade.generateSelfSignedCert(eq(KEY_ID), isA(ClientId.Conf.class),
                eq(KeyUsageInfo.SIGNING),
                eq("N/A"),
                eq(SIGNING_KEY_CERT_NOT_BEFORE),
                eq(SIGNING_KEY_CERT_NOT_AFTER))
        ).thenReturn(new byte[0]);

        var result = configurationSigningKeysServiceImpl.addKey(INTERNAL_CONFIGURATION,
                TOKEN_ID, KEY_LABEL);

        verify(configurationSourceRepository, times(1)).save(configurationSource);

        assertThat(result.isActiveSourceSigningKey()).isEqualTo(Boolean.TRUE);
        assertThat(result.getAvailable()).isEqualTo(Boolean.TRUE);
        assertThat(result.getKeyIdentifier()).isEqualTo(KEY_ID);
        assertThat(result.getTokenIdentifier()).isEqualTo(TOKEN_ID);
        assertThat(result.getLabel()).isEqualTo(new KeyLabel(KEY_LABEL));
    }

    @Test
    void shouldNotAddMoreThanTwoSingingKeys() {
        when(tokensServiceProvider.getObject()).thenReturn(tokensService);
        when(configurationSourceRepository.findBySourceType(INTERNAL_CONFIGURATION))
                .thenReturn(Optional.of(configurationSource));
        when(tokensService.getToken(TOKEN_ID)).thenReturn(createToken(Arrays.asList(
                createKeyInfo(),
                createKeyInfo()
        )));

        assertThrows(ValidationFailureException.class, () ->
                configurationSigningKeysServiceImpl.addKey(INTERNAL_CONFIGURATION, TOKEN_ID, KEY_LABEL));
        verifyNoInteractions(signerProxyFacade);
    }

    private TokenInfo createToken(List<KeyInfo> keys) {
        return new TokenInfo(null, "tokenName", TOKEN_ID,
                true, true, true, "serialNumber", "tokenLabel",
                1, TokenStatusInfo.OK, keys, new HashMap<>()
        );
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
}
