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

package org.niis.xroad.cs.admin.rest.api.mapper;

import ee.ria.xroad.common.util.TimeUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSigningKeyWithDetails;
import org.niis.xroad.cs.admin.api.dto.KeyLabel;
import org.niis.xroad.cs.admin.api.dto.PossibleKeyAction;
import org.niis.xroad.cs.admin.api.dto.TokenInfo;
import org.niis.xroad.cs.admin.api.dto.TokenStatus;
import org.niis.xroad.cs.openapi.model.PossibleTokenActionDto;
import org.niis.xroad.cs.openapi.model.TokenDto;
import org.niis.xroad.cs.openapi.model.TokenStatusDto;

import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.cs.admin.api.dto.PossibleTokenAction.LOGIN;
import static org.niis.xroad.cs.admin.api.dto.PossibleTokenAction.LOGOUT;

@ExtendWith(MockitoExtension.class)
class TokenDtoMapperTest {

    @Spy
    private ConfigurationSigningKeyDtoMapper configurationSigningKeyDtoMapper
            = new ConfigurationSigningKeyDtoMapperImpl();

    @InjectMocks
    private final TokenDtoMapper tokenDtoMapper = new TokenDtoMapperImpl();

    @Test
    void toTarget() {
        TokenInfo tokenInfo = tokenInfo();
        final TokenDto result = tokenDtoMapper.toTarget(tokenInfo);
        assertThat(result.getActive()).isTrue();
        assertThat(result.getAvailable()).isFalse();
        assertThat(result.getId()).isEqualTo("id");
        assertThat(result.getLoggedIn()).isTrue();
        assertThat(result.getName()).isEqualTo("friendlyName");
        assertThat(result.getPossibleActions())
                .containsExactlyInAnyOrder(PossibleTokenActionDto.LOGIN, PossibleTokenActionDto.LOGOUT);
        assertThat(result.getSerialNumber()).isEqualTo("serialNumber");
        assertThat(result.getStatus()).isEqualTo(TokenStatusDto.OK);

        assertThat(result.getConfigurationSigningKeys()).hasSize(1);
        assertThat(result.getConfigurationSigningKeys().get(0).getId())
                .isEqualTo(tokenInfo.getConfigurationSigningKeys().get(0).getKeyIdentifier());
        assertThat(result.getConfigurationSigningKeys().get(0).getTokenId()).isEqualTo(tokenInfo.getId());
        assertThat(result.getConfigurationSigningKeys().get(0).getCreatedAt()).isNotNull();
        assertThat(result.getConfigurationSigningKeys().get(0).getActive())
                .isEqualTo(tokenInfo.getConfigurationSigningKeys().get(0).isActiveSourceSigningKey());
    }

    private TokenInfo tokenInfo() {
        final TokenInfo tokenInfo = new TokenInfo();
        tokenInfo.setType("type");
        tokenInfo.setFriendlyName("friendlyName");
        tokenInfo.setId("id");
        tokenInfo.setReadOnly(true);
        tokenInfo.setAvailable(false);
        tokenInfo.setActive(true);
        tokenInfo.setSerialNumber("serialNumber");
        tokenInfo.setLabel("label");
        tokenInfo.setSlotIndex(123);
        tokenInfo.setStatus(TokenStatus.OK);
        tokenInfo.setPossibleActions(EnumSet.of(LOGIN, LOGOUT));

        tokenInfo.setConfigurationSigningKeys(List.of(configurationSigningKey()));

        return tokenInfo;
    }

    private ConfigurationSigningKeyWithDetails configurationSigningKey() {
        final ConfigurationSigningKeyWithDetails signingKey = new ConfigurationSigningKeyWithDetails();
        signingKey.setKeyIdentifier("keyIdentifier");
        signingKey.setTokenIdentifier("id");
        signingKey.setKeyGeneratedAt(TimeUtils.now());
        signingKey.setActiveSourceSigningKey(true);
        signingKey.setPossibleActions(List.of(PossibleKeyAction.ACTIVATE));
        signingKey.setLabel(new KeyLabel("LABEL"));
        return signingKey;
    }

}
