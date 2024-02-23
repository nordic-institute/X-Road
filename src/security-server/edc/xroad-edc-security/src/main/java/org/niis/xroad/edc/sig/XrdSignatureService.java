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
package org.niis.xroad.edc.sig;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.signer.SignerProxy;

import eu.europa.esig.dss.enumerations.JWSSerializationType;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Base64;
import org.niis.xroad.edc.sig.jades.XrdJAdESSignatureCreator;
import org.niis.xroad.edc.sig.jades.XrdJAdESVerifier;
import org.niis.xroad.edc.sig.jws.XrdJWSSignatureCreator;
import org.niis.xroad.edc.sig.jws.XrdJwsVerifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static eu.europa.esig.dss.enumerations.SignatureLevel.JAdES_BASELINE_B;
import static eu.europa.esig.dss.enumerations.SignatureLevel.JAdES_BASELINE_LT;
import static org.niis.xroad.edc.sig.PocConstants.HEADER_XRD_SIG;
import static org.niis.xroad.edc.sig.PocConstants.HEADER_XRD_SIG_OCSP;

@Slf4j
public class XrdSignatureService {
    private static final XrdSignatureMode MODE = XrdSignatureMode.JADES_B;

    private static final Set<String> IGNORED_HEADERS = Set.of(HEADER_XRD_SIG,
            "Accept-Encoding",
            "Date",
            "Content-Length"); //TODO length should not be ignored

    public Map<String, String> sign(String assetId, byte[] messageBody, Map<String, String> messageHeaders)
            throws XrdSignatureCreationException {
        var serviceId = ServiceId.Conf.fromEncodedId(assetId);

        return sign(serviceId.getClientId(), messageBody, messageHeaders);
    }

    public Map<String, String> sign(ClientId signingClientId, byte[] messageBody, Map<String, String> messageHeaders)
            throws XrdSignatureCreationException {

        var signingInfo = getMemberSigningInfo(signingClientId);
        var signer = switch (MODE) {
            case JWS -> new XrdJWSSignatureCreator();
            case JADES_B -> new XrdJAdESSignatureCreator(JAdES_BASELINE_B, JWSSerializationType.COMPACT_SERIALIZATION);
            case JADES_B_LT -> new XrdJAdESSignatureCreator(JAdES_BASELINE_LT, JWSSerializationType.FLATTENED_JSON_SERIALIZATION);
        };

        Map<String, String> headers = new HashMap<>();
        headers.put(HEADER_XRD_SIG_OCSP, Base64.toBase64String(signingInfo.getCert().getOcspBytes()));
        for (Map.Entry<String, String> entry : messageHeaders.entrySet()) {
            if (!IGNORED_HEADERS.contains(entry.getKey()))
                headers.put(entry.getKey(), entry.getValue());
        }

        headers.forEach((key, value) -> log.info("Will sign header: {}={}", key, value));
        var signature = signer.sign(signingInfo, messageBody, headers);
        headers.put(HEADER_XRD_SIG, signature);

        return headers;
    }

    public void verify(Map<String, String> headers, byte[] detachedPayload, ClientId signerClientId)
            throws XrdSignatureVerificationException {

        var verifier = switch (MODE) {
            case JWS -> new XrdJwsVerifier();
            case JADES_B, JADES_B_LT -> new XrdJAdESVerifier();
        };

        var signature = headers.get(HEADER_XRD_SIG); // currently only header is supported for signature.
        var filteredHeaders = headers.entrySet().stream()
                .filter(entry -> !IGNORED_HEADERS.contains(entry.getKey()))
                .peek(entry -> log.info("Will verify header: {}={}", entry.getKey(), entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        verifier.verifySignature(signature, detachedPayload, filteredHeaders, signerClientId);
    }

    private SignerProxy.MemberSigningInfoDto getMemberSigningInfo(ClientId clientId) throws XrdSignatureCreationException {
        try {
            return SignerProxy.getMemberSigningInfo(clientId);
        } catch (Exception e) {
            throw new XrdSignatureCreationException("Failed to get member sign cert info", e);
        }
    }

    @Getter
    public enum XrdSignatureMode {
        JWS,
        JADES_B,
        JADES_B_LT
    }
}
