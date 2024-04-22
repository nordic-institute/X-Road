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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.cert.CertChainVerifier;
import ee.ria.xroad.common.certificateprofile.impl.SignCertificateProfileInfoParameters;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CertUtils;

import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.util.encoders.Base64;

import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
public abstract class XrdSignatureVerifierBase {

    protected SignedDocumentValidator getValidator(DSSDocument signedDocument) {
        SignedDocumentValidator validator = SignedDocumentValidator.fromDocument(signedDocument);
        validator.setCertificateVerifier(new CommonCertificateVerifier());
        return validator;
    }

    protected void validateXroad(X509Certificate cert,
                               ClientId clientId, Map<String, String> detachedHeaders) throws Exception {
        var ocsResponse = new OCSPResp(Base64.decode(detachedHeaders.get(PocConstants.HEADER_XRD_SIG_OCSP)));
        verifyCertificateChain(new Date(), clientId, cert, List.of(ocsResponse));
        verifySignerName(clientId, cert);

        CertUtils.isSigningCert(cert);
        CertUtils.isValid(cert); //TODO probably overlaps with native dss checks?
        log.info("XRD checks: Signature is valid.");
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

    protected void assertTrue(boolean val) {
        if (!val) {
            throw new CodedException(ErrorCodes.X_INCONSISTENT_RESPONSE, "Signature verification failed.");

        }
    }
}
