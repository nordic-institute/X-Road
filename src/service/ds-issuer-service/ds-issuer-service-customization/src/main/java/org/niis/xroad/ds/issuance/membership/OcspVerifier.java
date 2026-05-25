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
package org.niis.xroad.ds.issuance.membership;

import org.bouncycastle.cert.ocsp.OCSPResp;
import org.eclipse.edc.spi.result.Result;
import org.niis.xroad.globalconf.GlobalConfProvider;

import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.Base64;
import java.util.Date;

/**
 * Verifies a pinned OCSP response from a JWS {@code ocsp} header against the X-Road
 * global conf-anchored trust path. Delegates the actual cryptographic checks (signature
 * by the OCSP responder cert, subject/issuer linkage, freshness, status) to the existing
 * X-Road {@link org.niis.xroad.globalconf.impl.ocsp.OcspVerifier} used by SOAP signature
 * verification, so DCP gets the same checks as the rest of X-Road.
 */
final class OcspVerifier {

    private final GlobalConfProvider globalConf;
    private final org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory xroadVerifierFactory;
    private final Clock clock;

    OcspVerifier(GlobalConfProvider globalConf,
                 org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory xroadVerifierFactory,
                 Clock clock) {
        this.globalConf = globalConf;
        this.xroadVerifierFactory = xroadVerifierFactory;
        this.clock = clock;
    }

    /**
     * Verifies the pinned OCSP response against the leaf cert.
     *
     * @param pinnedOcspBase64 base64-encoded OCSP response DER bytes from the JWS
     *                         {@code ocsp} header
     * @param leafCert         the JWS signing certificate the OCSP response should cover
     * @return success, or a failure carrying {@link MembershipVerificationReason#OCSP_INVALID}
     */
    Result<Void> verify(String pinnedOcspBase64, X509Certificate leafCert) {
        if (pinnedOcspBase64 == null || pinnedOcspBase64.isBlank()) {
            return Result.failure(MembershipVerificationReason.OCSP_INVALID.name() + ": missing ocsp header");
        }
        byte[] ocspDer;
        try {
            ocspDer = Base64.getDecoder().decode(pinnedOcspBase64);
        } catch (IllegalArgumentException e) {
            return Result.failure(MembershipVerificationReason.OCSP_INVALID.name() + ": ocsp header is not base64");
        }
        OCSPResp ocspResp;
        try {
            ocspResp = new OCSPResp(ocspDer);
        } catch (Exception e) {
            return Result.failure(MembershipVerificationReason.OCSP_INVALID.name() + ": cannot parse OCSP response: "
                    + e.getMessage());
        }
        X509Certificate issuerCert;
        try {
            issuerCert = globalConf.getCaCert(globalConf.getInstanceIdentifier(), leafCert);
            if (issuerCert == null) {
                return Result.failure(MembershipVerificationReason.OCSP_INVALID.name() + ": no CA cert in globalconf for leaf");
            }
        } catch (Exception e) {
            return Result.failure(MembershipVerificationReason.OCSP_INVALID.name()
                    + ": cannot resolve CA cert for leaf: " + e.getMessage());
        }
        try {
            xroadVerifierFactory.create(globalConf)
                    .verifyValidityAndStatus(ocspResp, leafCert, issuerCert, Date.from(clock.instant()));
            return Result.success();
        } catch (Exception e) {
            return Result.failure(MembershipVerificationReason.OCSP_INVALID.name() + ": " + e.getMessage());
        }
    }
}
