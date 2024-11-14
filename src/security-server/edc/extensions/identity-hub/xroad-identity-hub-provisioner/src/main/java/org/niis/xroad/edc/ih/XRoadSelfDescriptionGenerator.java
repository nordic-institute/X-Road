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

package org.niis.xroad.edc.ih;

import ee.ria.xroad.signer.SignerRpcClient;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;

import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.util.Base64;
import lombok.RequiredArgsConstructor;
import org.eclipse.edc.security.token.jwt.CryptoConverter;

import java.time.Instant;
import java.util.List;

import static java.time.temporal.ChronoUnit.DAYS;

@RequiredArgsConstructor
public class XRoadSelfDescriptionGenerator {
    private final SignerRpcClient signerRpcClient;

    public JWSObject generate(String xroadIdentifier, JWSSigner signer, String did, String keyId) throws Exception {
        var payload = getPayload(xroadIdentifier);
        var header = new JWSHeader.Builder(CryptoConverter.getRecommendedAlgorithm(signer))
                .base64URLEncodePayload(true)
                .customParam("iss", did)
                .keyID(did + "#" + keyId)
                .customParam("iat", Instant.now().toString())
                .customParam("exp", Instant.now().plus(90, DAYS).toString())
                .x509CertChain(List.of(Base64.encode(getActiveCertificate(keyId))))
                .build();
        var detachedPayload = new Payload(payload);
        var jwsObject = new JWSObject(header, detachedPayload);
        jwsObject.sign(signer);
        return jwsObject;
    }

    private byte[] getActiveCertificate(String keyId) throws Exception {
        var token = signerRpcClient.getTokenForKeyId(keyId);
        var certificates = token.getKeyInfo().stream()
                .filter(keyInfo -> keyInfo.getId().equals(keyId))
                .findFirst()
                .map(KeyInfo::getCerts)
                .orElseThrow();
        return certificates.stream()
                .filter(CertificateInfo::isActive)
                .map(CertificateInfo::getCertificateBytes)
                .findFirst()
                .orElseThrow();
    }

    private static String getPayload(String xroadIdentifier) {
        return "{\n" +
                "  \"xroadIdentifier\": \"" + xroadIdentifier + "\"\n" +
                "}";
    }

}
