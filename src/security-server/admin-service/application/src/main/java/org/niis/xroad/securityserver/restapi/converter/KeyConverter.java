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

import ee.ria.xroad.common.crypto.identifier.SignMechanism;

import com.google.common.collect.Streams;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyAlgorithmDto;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyDto;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageTypeDto;
import org.niis.xroad.securityserver.restapi.service.PossibleActionsRuleEngine;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Convert KeyDto related data between openapi and service domain classes
 */
@Component
@RequiredArgsConstructor
public class KeyConverter {

    private final TokenCertificateConverter tokenCertificateConverter;
    private final TokenCertificateSigningRequestConverter tokenCsrConverter;
    private final PossibleActionsRuleEngine possibleActionsRuleEngine;
    private final PossibleActionConverter possibleActionConverter;

    /**
     * Convert {@link KeyInfo} to openapi {@link KeyDto} object
     * @param keyInfo
     */
    public KeyDto convert(KeyInfo keyInfo) {
        return convertInternal(keyInfo, null);
    }

    /**
     * Convert {@link KeyInfo} to openapi {@link KeyDto} object
     * and populate possibleActions
     * @param keyInfo
     */
    public KeyDto convert(KeyInfo keyInfo, TokenInfo tokenInfo) {
        if (tokenInfo == null) {
            throw new IllegalArgumentException("tokenInfo is mandatory to populate possibleActions");
        }
        return convertInternal(keyInfo, tokenInfo);
    }

    /**
     * Convert {@link KeyInfo} to openapi {@link KeyDto} object
     * and populate possibleActions if TokenInfo param was given
     * @param keyInfo
     */
    private KeyDto convertInternal(KeyInfo keyInfo, TokenInfo tokenInfo) {
        KeyDto keyDto = new KeyDto();
        keyDto.setId(keyInfo.getId());
        keyDto.setName(keyInfo.getFriendlyName());
        keyDto.setLabel(keyInfo.getLabel());
        keyDto.setKeyAlgorithm(mapKeyAlgorithm(keyInfo.getSignMechanismName()));
        if (keyInfo.getUsage() != null) {
            if (keyInfo.isForSigning()) {
                keyDto.setUsage(KeyUsageTypeDto.SIGNING);
            } else {
                keyDto.setUsage(KeyUsageTypeDto.AUTHENTICATION);
            }
        }

        keyDto.setAvailable(keyInfo.isAvailable());
        keyDto.setSavedToConfiguration(keyInfo.isSavedToConfiguration());

        if (tokenInfo == null) {
            // without possibleactions
            keyDto.setCertificates(tokenCertificateConverter.convert(keyInfo.getCerts()));
            keyDto.setCertificateSigningRequests(tokenCsrConverter.convert(keyInfo.getCertRequests()));
        } else {
            // with possibleactions
            keyDto.setCertificates(tokenCertificateConverter.convert(keyInfo.getCerts(), keyInfo, tokenInfo));
            keyDto.setCertificateSigningRequests(tokenCsrConverter.convert(keyInfo.getCertRequests(), keyInfo, tokenInfo));

            keyDto.setPossibleActions(possibleActionConverter.convert(
                    possibleActionsRuleEngine.getPossibleKeyActions(
                            tokenInfo, keyInfo)));
        }

        return keyDto;
    }

    private KeyAlgorithmDto mapKeyAlgorithm(String signMechanismName) {
        return switch (SignMechanism.valueOf(signMechanismName).keyAlgorithm()) {
            case RSA -> KeyAlgorithmDto.RSA;
            case EC -> KeyAlgorithmDto.EC;
        };
    }

    /**
     * Convert a group of {@link KeyInfo keyInfos} to a list of {@link KeyDto keyInfos}
     * @param keyInfos
     * @return List of {@link KeyInfo keyInfos}
     */
    public List<KeyDto> convert(Iterable<KeyInfo> keyInfos) {
        return Streams.stream(keyInfos)
                .map(this::convert)
                .collect(Collectors.toList());
    }

    /**
     * Convert a group of {@link KeyInfo keyInfos} to a list of {@link KeyDto keyInfos},
     * populating possibleActions
     * @param keyInfos
     * @return List of {@link KeyInfo keyInfos}
     */
    public Set<KeyDto> convert(Iterable<KeyInfo> keyInfos, TokenInfo tokenInfo) {
        return Streams.stream(keyInfos)
                .map(k -> convert(k, tokenInfo))
                .collect(Collectors.toSet());
    }
}
