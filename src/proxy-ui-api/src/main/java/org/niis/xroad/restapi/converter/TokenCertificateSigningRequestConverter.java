/**
 * The MIT License
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
package org.niis.xroad.restapi.converter;

import ee.ria.xroad.signer.protocol.dto.CertRequestInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import com.google.common.collect.Streams;
import org.niis.xroad.restapi.openapi.model.PossibleActions;
import org.niis.xroad.restapi.openapi.model.TokenCertificateSigningRequest;
import org.niis.xroad.restapi.service.StateChangeActionHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Convert token certificate signing request related data between openapi and service domain classes
 */
@Component
public class TokenCertificateSigningRequestConverter {

    private final ClientConverter clientConverter;
    private final StateChangeActionHelper stateChangeActionHelper;
    private final StateChangeActionConverter stateChangeActionConverter;

    @Autowired
    public TokenCertificateSigningRequestConverter(ClientConverter clientConverter,
            StateChangeActionHelper stateChangeActionHelper,
            StateChangeActionConverter stateChangeActionConverter) {
        this.clientConverter = clientConverter;
        this.stateChangeActionHelper = stateChangeActionHelper;
        this.stateChangeActionConverter = stateChangeActionConverter;
    }

    /**
     * Convert {@link CertRequestInfo} to {@link TokenCertificateSigningRequest}
     * and populate possibleActions
     * @param csrInfo
     * @return {@link TokenCertificateSigningRequest}
     */
    public TokenCertificateSigningRequest convert(CertRequestInfo csrInfo,
            KeyInfo keyInfo,
            TokenInfo tokenInfo) {
        TokenCertificateSigningRequest request = convert(csrInfo);
        PossibleActions possibleActions = new PossibleActions();
        possibleActions.setItems(stateChangeActionConverter.convert(
                stateChangeActionHelper.getPossibleCsrActions(
                        tokenInfo, keyInfo, csrInfo)));
        request.setPossibleActions(possibleActions);
        return request;
    }
    /**
     * Convert {@link CertRequestInfo} to {@link TokenCertificateSigningRequest}
     * @param csrInfo
     * @return {@link TokenCertificateSigningRequest}
     */
    public TokenCertificateSigningRequest convert(CertRequestInfo csrInfo) {
        TokenCertificateSigningRequest request = new TokenCertificateSigningRequest();
        request.setId(csrInfo.getId());
        if (csrInfo.getMemberId() != null) {
            request.setOwnerId(clientConverter.convertId(csrInfo.getMemberId()));
        }
        return request;
    }

    /**
     * Convert a group of {@link CertRequestInfo certRequestInfos} to a list of
     * {@link TokenCertificateSigningRequest token CSRs}
     * @param csrInfos
     * @return List of {@link TokenCertificateSigningRequest token CSRs}
     */
    public List<TokenCertificateSigningRequest> convert(Iterable<CertRequestInfo> csrInfos) {
        return Streams.stream(csrInfos)
                .map(this::convert)
                .collect(Collectors.toList());
    }

    /**
     * Convert a group of {@link CertRequestInfo certRequestInfos} to a list of
     * {@link TokenCertificateSigningRequest token CSRs}, while populating possibleActions
     * @param csrInfos
     * @return List of {@link TokenCertificateSigningRequest token CSRs}
     */
    public List<TokenCertificateSigningRequest> convert(Iterable<CertRequestInfo> csrInfos,
            KeyInfo keyInfo,
            TokenInfo tokenInfo) {
        return Streams.stream(csrInfos)
                .map(c -> convert(c, keyInfo, tokenInfo))
                .collect(Collectors.toList());
    }

}
