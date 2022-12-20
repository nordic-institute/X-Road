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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSigningKey;
import org.niis.xroad.cs.admin.core.entity.ConfigurationSigningKeyEntity;
import org.niis.xroad.cs.admin.core.entity.ConfigurationSourceEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.ConfigurationSigningKeyMapper;
import org.niis.xroad.cs.admin.core.entity.mapper.ConfigurationSigningKeyMapperImpl;
import org.niis.xroad.cs.admin.core.repository.ConfigurationSigningKeyRepository;

import java.util.List;
import java.util.Map;

import static ee.ria.xroad.signer.protocol.dto.TokenStatusInfo.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigurationSigningKeysServiceImplTest {

    @Mock
    private ConfigurationSigningKeyRepository configurationSigningKeyRepository;
    private final ConfigurationSigningKeyMapper configurationSigningKeyMapper = new ConfigurationSigningKeyMapperImpl();

    private ConfigurationSigningKeysServiceImpl configurationSigningKeysServiceImpl;

    @BeforeEach
    public void setup() {
        configurationSigningKeysServiceImpl =
                new ConfigurationSigningKeysServiceImpl(configurationSigningKeyRepository, configurationSigningKeyMapper);
    }

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
