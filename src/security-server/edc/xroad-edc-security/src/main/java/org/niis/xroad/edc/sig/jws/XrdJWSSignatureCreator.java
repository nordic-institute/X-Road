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
package org.niis.xroad.edc.sig.jws;

import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.SignerProxy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.impl.RSASSAProvider;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jose.util.Base64URL;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.edc.sig.XrdSignatureCreationException;
import org.niis.xroad.edc.sig.XrdSignatureCreator;

import java.util.List;
import java.util.Map;

import static ee.ria.xroad.common.util.CryptoUtils.calculateDigest;
import static ee.ria.xroad.common.util.CryptoUtils.getDigestAlgorithmId;

@RequiredArgsConstructor
public class XrdJWSSignatureCreator implements XrdSignatureCreator {
    public static final String JWS_HEADER_OCSP_RESPONSE = "ocsp-response";

    private final XrdJWSSigner signer = new XrdJWSSigner();

    @Override
    public String sign(final SignerProxy.MemberSigningInfoDto signingInfo, final byte[] messageBody,
                       final Map<String, String> messageHeaders) throws XrdSignatureCreationException {
        final var payload = XrdJwsUtils.createSignablePayload(messageBody, messageHeaders);

        JWSObject jwsObject = new JWSObject(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(signingInfo.getKeyId())
                        .x509CertChain(List.of(Base64.encode(signingInfo.getCert().getCertificateBytes())))
                        .customParam(JWS_HEADER_OCSP_RESPONSE, Base64.encode(signingInfo.getCert().getOcspBytes()))
                        .build(),
                payload);

        try {
            jwsObject.sign(signer);
        } catch (JOSEException e) {
            throw new XrdSignatureCreationException("Failed to sign", e);
        }
        return jwsObject.serialize(true);
    }

    private static final class XrdJWSSigner extends RSASSAProvider implements JWSSigner {

        @Override
        public Base64URL sign(final JWSHeader header, final byte[] signingInput) throws JOSEException {
            try {
                String signAlgoId = switch (header.getAlgorithm().getName()) {
                    case "RS256" -> CryptoUtils.SHA256WITHRSA_ID;
                    case "RS384" -> CryptoUtils.SHA384WITHRSA_ID;
                    case "RS512" -> CryptoUtils.SHA512WITHRSA_ID;
                    default -> throw new JOSEException("Unsupported signing algorithm");
                };

                String digAlgoId = getDigestAlgorithmId(signAlgoId);
                byte[] digest = calculateDigest(digAlgoId, signingInput);

                byte[] sig = SignerProxy.sign(header.getKeyID(), signAlgoId, digest);

                return Base64URL.encode(sig);
            } catch (Exception e) {
                throw new JOSEException("Failed to sign", e);
            }
        }


    }
}
