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
package org.niis.xroad.common.managementrequest.verify.decode.util;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.util.CertUtils;

import lombok.RequiredArgsConstructor;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifier;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierOptions;

import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;

import static ee.ria.xroad.common.ErrorCodes.X_CERT_VALIDATION;

@RequiredArgsConstructor
public class ManagementRequestCertVerifier {
    private final GlobalConfProvider globalConfProvider;

    @ArchUnitSuppressed("NoVanillaExceptions") //TODO XRDDEV-2962 review and refactor if needed
    public void verifyCertificate(X509Certificate memberCert, OCSPResp memberCertOcsp) throws Exception {

        X509Certificate issuer = globalConfProvider.getCaCert(globalConfProvider.getInstanceIdentifier(), memberCert);

        try {
            memberCert.verify(issuer.getPublicKey());
            memberCert.checkValidity();
        } catch (GeneralSecurityException e) {
            throw new CodedException(X_CERT_VALIDATION,
                    "Member (owner/client) sign certificate is invalid: %s", e.getMessage());
        }

        if (!CertUtils.isSigningCert(memberCert)) {
            throw new CodedException(X_CERT_VALIDATION, "Member (owner/client) sign certificate is invalid");
        }

        new OcspVerifier(globalConfProvider,
                new OcspVerifierOptions(globalConfProvider.getGlobalConfExtensions().shouldVerifyOcspNextUpdate()))
                .verifyValidityAndStatus(memberCertOcsp, memberCert, issuer);
    }

}
