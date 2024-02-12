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

import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.signer.SignerProxy;

import eu.europa.esig.dss.enumerations.JWSSerializationType;
import lombok.Getter;
import org.bouncycastle.util.encoders.Base64;
import org.niis.xroad.edc.sig.jades.XrdJAdESSignatureCreator;
import org.niis.xroad.edc.sig.jades.XrdJAdESVerifier;
import org.niis.xroad.edc.sig.jws.XrdJWSSignatureCreator;
import org.niis.xroad.edc.sig.jws.XrdJwsVerifier;

import java.util.HashMap;
import java.util.Map;

import static eu.europa.esig.dss.enumerations.SignatureLevel.JAdES_BASELINE_B;
import static eu.europa.esig.dss.enumerations.SignatureLevel.JAdES_BASELINE_LT;
import static org.niis.xroad.edc.sig.PocConstants.HEADER_XRD_SIG;
import static org.niis.xroad.edc.sig.PocConstants.HEADER_XRD_SIG_OCSP;

public class XrdSignService {
    private static final XrdSignatureMode MODE = XrdSignatureMode.JADES_B;

    public Map<String, String> sign(String assetId, String messageBody, Map<String, String> messageHeaders)
            throws XrdSignatureCreationException {

        var signingInfo = getMemberSigningInfo(assetId);
        var signer = switch (MODE) {
            case JWS -> new XrdJWSSignatureCreator();
            case JADES_B -> new XrdJAdESSignatureCreator(JAdES_BASELINE_B, JWSSerializationType.COMPACT_SERIALIZATION);
            case JADES_B_LT -> new XrdJAdESSignatureCreator(JAdES_BASELINE_LT, JWSSerializationType.FLATTENED_JSON_SERIALIZATION);
        };

        Map<String, String> headers = new HashMap<>();
        headers.put(HEADER_XRD_SIG_OCSP, Base64.toBase64String(signingInfo.getCert().getOcspBytes()));
        headers.put(HEADER_XRD_SIG, signer.sign(signingInfo, messageBody, messageHeaders));

        return headers;
    }

    public void verify(Map<String, String> headers, byte[] detachedPayload, RestRequest restRequest)
            throws XrdSignatureVerificationException {

        var verifier = switch (MODE) {
            case JWS -> new XrdJwsVerifier();
            case JADES_B, JADES_B_LT -> new XrdJAdESVerifier();
        };

        var signature = headers.get(HEADER_XRD_SIG); // currently only header is supported for signature.

        verifier.verifySignature(signature, detachedPayload, headers, restRequest);
    }

    private SignerProxy.MemberSigningInfoDto getMemberSigningInfo(String assetId) throws XrdSignatureCreationException {
        var serviceId = ServiceId.Conf.fromEncodedId(assetId);

        try {
            return SignerProxy.getMemberSigningInfo(serviceId.getClientId());
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
