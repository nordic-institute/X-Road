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

package org.niis.xroad.cs.admin.core.converter;

import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfoProto;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfoProto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSigningKeyWithDetails;
import org.niis.xroad.cs.admin.api.dto.PossibleTokenAction;
import org.niis.xroad.cs.admin.api.dto.TokenStatus;
import org.niis.xroad.cs.admin.api.service.ConfigurationSigningKeysService;
import org.niis.xroad.cs.admin.api.service.TokenActionsResolver;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static ee.ria.xroad.signer.protocol.dto.TokenStatusInfo.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenInfoMapperTest {
    @Mock
    private ConfigurationSigningKeysService configurationSigningKeysService;
    @Mock
    protected TokenActionsResolver tokenActionsResolver;
    @InjectMocks
    private TokenInfoMapper tokenInfoMapper;

    @Test
    void toTarget() {
        final TokenInfo tokenInfo = createTokenInfo();
        final EnumSet<PossibleTokenAction> possibleActions = mock(EnumSet.class);
        final List<ConfigurationSigningKeyWithDetails> configurationSigningKeys = mock(List.class);

        when(configurationSigningKeysService.findDetailedByToken(any())).thenReturn(configurationSigningKeys);
        when(tokenActionsResolver.resolveActions(tokenInfo, configurationSigningKeys)).thenReturn(possibleActions);

        final org.niis.xroad.cs.admin.api.dto.TokenInfo result = tokenInfoMapper.toTarget(tokenInfo);

        assertThat(result.getId()).isEqualTo("TOKEN_ID");
        assertThat(result.getType()).isEqualTo("type");
        assertThat(result.getFriendlyName()).isEqualTo("TOKEN_FRIENDLY_NAME");
        assertThat(result.getSerialNumber()).isEqualTo("TOKEN_SERIAL_NUMBER");
        assertThat(result.getLabel()).isEqualTo("label");
        assertThat(result.getSlotIndex()).isEqualTo(13);
        assertThat(result.getStatus()).isEqualTo(TokenStatus.OK);
        assertThat(result.isReadOnly()).isFalse();
        assertThat(result.isAvailable()).isTrue();
        assertThat(result.isActive()).isFalse();
        assertThat(result.getPossibleActions()).isEqualTo(possibleActions);
        assertThat(result.getConfigurationSigningKeys()).isEqualTo(configurationSigningKeys);
    }

    private TokenInfo createTokenInfo() {
        return new TokenInfo(TokenInfoProto.newBuilder()
                .setType("type")
                .setFriendlyName("TOKEN_FRIENDLY_NAME")
                .setId("TOKEN_ID")
                .setReadOnly(false)
                .setAvailable(true)
                .setActive(false)
                .setSerialNumber("TOKEN_SERIAL_NUMBER")
                .setLabel("label")
                .setSlotIndex(13)
                .setStatus(OK)
                .addAllKeyInfo(List.of(createKeyInfo().getMessage()))
                .putAllTokenInfo(Map.of("key", "value"))
                .build());
    }

    private KeyInfo createKeyInfo() {
        return new KeyInfo(KeyInfoProto.newBuilder()
                .setAvailable(true)
                .setUsage(KeyUsageInfo.SIGNING)
                .setFriendlyName("keyFriendlyName")
                .setId("keyId")
                .setLabel("keyLabel")
                .setPublicKey("keyPublicKey")
                .setSignMechanismName("keySignMechanismName")
                .build());
    }
}
