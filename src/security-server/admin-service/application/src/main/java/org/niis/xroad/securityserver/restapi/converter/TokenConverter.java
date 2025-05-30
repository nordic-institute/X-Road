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
package org.niis.xroad.securityserver.restapi.converter;

import com.google.common.collect.Streams;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyValuePairDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TokenDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TokenStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TokenTypeDto;
import org.niis.xroad.securityserver.restapi.service.PossibleActionsRuleEngine;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Convert TokenDto related data between openapi and service domain classes
 */
@Component
@RequiredArgsConstructor
public class TokenConverter {

    private final KeyConverter keyConverter;
    private final PossibleActionsRuleEngine possibleActionsRuleEngine;
    private final PossibleActionConverter possibleActionConverter;

    /**
     * Convert {@link TokenInfo} to openapi {@link TokenDto} object
     * @param tokenInfo
     * @return
     */
    public TokenDto convert(TokenInfo tokenInfo) {
        TokenDto token = new TokenDto();
        token.setId(tokenInfo.getId());
        token.setName(tokenInfo.getFriendlyName());

        // software module has a magic type string, hardware modules have device UI as type
        if (TokenInfo.SOFTWARE_MODULE_TYPE.equals(tokenInfo.getType())) {
            token.setType(TokenTypeDto.SOFTWARE);
        } else {
            token.setType(TokenTypeDto.HARDWARE);
        }

        token.setKeys(keyConverter.convert(tokenInfo.getKeyInfo(), tokenInfo));

        Optional<TokenStatusDto> status = TokenStatusMapping.map(tokenInfo.getStatus());
        token.setStatus(status.orElse(null));

        token.setLoggedIn(tokenInfo.isActive());
        token.setAvailable(tokenInfo.isAvailable());
        token.setSavedToConfiguration(tokenInfo.isSavedToConfiguration());
        token.setReadOnly(tokenInfo.isReadOnly());
        token.setSerialNumber(tokenInfo.getSerialNumber());
        token.setTokenInfos(new ArrayList<>());

        for (String key : tokenInfo.getTokenInfo().keySet()) {
            KeyValuePairDto keyValuePair = new KeyValuePairDto();
            keyValuePair.setKey(key);
            keyValuePair.setValue(tokenInfo.getTokenInfo().get(key));
            token.getTokenInfos().add(keyValuePair);
        }

        token.setPossibleActions(possibleActionConverter.convert(
                possibleActionsRuleEngine.getPossibleTokenActions(
                        tokenInfo)));

        return token;
    }

    /**
     * Convert a group of {@link TokenInfo tokenInfos} to a list of {@link TokenDto tokens}
     * @param tokenInfos
     * @return List of {@link TokenInfo tokenInfos}
     */
    public Set<TokenDto> convert(Iterable<TokenInfo> tokenInfos) {
        return Streams.stream(tokenInfos)
                .map(this::convert)
                .collect(Collectors.toSet());
    }
}
