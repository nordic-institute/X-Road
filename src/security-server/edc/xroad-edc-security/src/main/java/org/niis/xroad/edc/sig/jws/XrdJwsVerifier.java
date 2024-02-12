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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.cert.CertChainVerifier;
import ee.ria.xroad.common.certificateprofile.impl.SignCertificateProfileInfoParameters;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.util.CertUtils;

import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.X509CertUtils;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.util.encoders.Base64;
import org.niis.xroad.edc.sig.XrdSignatureVerificationException;
import org.niis.xroad.edc.sig.XrdSignatureVerifier;

import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.niis.xroad.edc.sig.jws.XrdJWSSignatureCreator.JWS_HEADER_OCSP_RESPONSE;

/**
 * POC for JWS verification. Loosely based on SignatureVerifier.
 */
public class XrdJwsVerifier implements XrdSignatureVerifier {

    @Override
    public void verifySignature(String signature, byte[] detachedPayload, Map<String, String> detachedHeaders, RestRequest restRequest)
            throws XrdSignatureVerificationException {
        try {
            JWSObject parsedJWSObject = JWSObject.parse(signature, new Payload(detachedPayload));
            // Parse X.509 certificate
            X509Certificate cert = X509CertUtils.parse(parsedJWSObject.getHeader().getX509CertChain().get(0).decode());
            // Retrieve public key as RSA JWK
            RSAKey rsaJWK = RSAKey.parse(cert);
            JWSVerifier verifier = new RSASSAVerifier(rsaJWK);

            verifySignerName(restRequest.getServiceId().getClientId(), cert);

            CertUtils.isSigningCert(cert);
            CertUtils.isValid(cert);

            var ocspResponse = parsedJWSObject.getHeader().getCustomParam(JWS_HEADER_OCSP_RESPONSE);
            if (ocspResponse instanceof Map ocspResponseStr) {
                var ocsResponse = new OCSPResp(Base64.decode((String) ocspResponseStr.get("value")));
                verifyCertificateChain(new Date(), restRequest.getServiceId().getClientId(), cert, List.of(ocsResponse));
            } else {
                throw new CodedException(ErrorCodes.X_INCONSISTENT_RESPONSE, "missing ocsp responses");
            }
            if (!parsedJWSObject.verify(verifier)) {
                throw new CodedException(ErrorCodes.X_INCONSISTENT_RESPONSE, "Response message body sig is invalid.");
            }
        } catch (Exception e) {
            throw new XrdSignatureVerificationException("Verification has failed", e);
        }
    }

    private static void verifySignerName(ClientId signer, X509Certificate signingCert) throws Exception {
        ClientId cn = GlobalConf.getSubjectName(
                new SignCertificateProfileInfoParameters(
                        signer, signer.getMemberCode()
                ),
                signingCert);
        if (!signer.memberEquals(cn)) {
            throw new CodedException(ErrorCodes.X_INCORRECT_CERTIFICATE,
                    "Name in certificate (%s) does not match "
                            + "name in message (%s)", cn, signer);
        }
    }

    private void verifyCertificateChain(Date atDate, ClientId signer, X509Certificate signingCert,
                                        List<OCSPResp> ocspResps) {
        CertChain certChain = CertChain.create(signer.getXRoadInstance(), signingCert, null);

        new CertChainVerifier(certChain).verify(ocspResps, atDate);
    }
}
