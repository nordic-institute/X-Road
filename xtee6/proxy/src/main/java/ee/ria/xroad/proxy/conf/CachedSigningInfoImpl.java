/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.proxy.conf;

import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.globalconfextension.GlobalConfExtensions;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.ocsp.OcspVerifier;
import ee.ria.xroad.common.ocsp.OcspVerifierOptions;
import ee.ria.xroad.proxy.signedmessage.SignerSigningKey;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.OCSPResp;

import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Date;

@Slf4j
@Getter
@RequiredArgsConstructor
final class CachedSigningInfoImpl extends AbstractCachedInfo {

    private final String keyId;
    private final ClientId clientId;
    private final X509Certificate cert;
    private final OCSPResp ocsp;

    // ------------------------------------------------------------------------

    @Override
    boolean verifyValidity(Date atDate) {
        try {
            log.trace("CachedSigningInfoImpl.verifyValidity date: {}", atDate);
            verifyCert(atDate);
            verifyOcsp(atDate, clientId.getXRoadInstance());
            return true;
        } catch (Exception e) {
            log.warn("Cached signing info for member '{}' "
                    + "failed verification: {}", clientId, e);
            return false;
        }
    }

    SigningCtx getSigningCtx() {
        return new SigningCtxImpl(clientId, new SignerSigningKey(keyId), cert);
    }

    // ------------------------------------------------------------------------

    private void verifyCert(Date atDate) throws CertificateExpiredException,
            CertificateNotYetValidException {
        cert.checkValidity(atDate);
    }

    private void verifyOcsp(Date atDate, String instanceIdentifier)
            throws Exception {
        X509Certificate issuer =
                GlobalConf.getCaCert(instanceIdentifier, cert);
        OcspVerifier verifier =
                new OcspVerifier(GlobalConf.getOcspFreshnessSeconds(false),
                        new OcspVerifierOptions(GlobalConfExtensions.getInstance().shouldVerifyOcspNextUpdate()));
        verifier.verifyValidityAndStatus(ocsp, cert, issuer, atDate);
    }
}
