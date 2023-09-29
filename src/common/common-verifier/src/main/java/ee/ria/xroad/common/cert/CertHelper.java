/*
 * The MIT License
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
package ee.ria.xroad.common.cert;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.SingleResp;

import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.X_SSL_AUTH_FAILED;

/**
 * Certificate-related helper functions.
 */
@Slf4j
public final class CertHelper {

    private CertHelper() {
    }

    /**
     * @param cert the certificate
     * @return short name of the certificate subject.
     * Short name is used in messages and access checking.
     */
    public static String getSubjectCommonName(X509Certificate cert) {
        return CertUtils.getSubjectCommonName(cert);
    }

    /**
     * @param cert the certificate
     * @return the SerialNumber component from the Subject field.
     */
    public static String getSubjectSerialNumber(X509Certificate cert) {
        return CertUtils.getSubjectSerialNumber(cert);
    }

    /**
     * @param cert the certificate
     * @return a fully constructed Client identifier from DN of the certificate.
     */
    public static ClientId getSubjectClientId(X509Certificate cert) {
        return CertUtils.getSubjectClientId(cert);
    }

    /**
     * Verifies that the certificate <code>cert</code> can be used for
     * authenticating as member <code>member</code>.
     * The <code>ocspResponsec</code> is used to verify validity of the
     * certificate.
     * @param chain the certificate chain
     * @param ocspResponses OCSP responses used in the cert chain
     * @param member the member
     * @throws Exception if verification fails.
     */
    public static void verifyAuthCert(CertChain chain, List<OCSPResp> ocspResponses, ClientId member) throws Exception {
        X509Certificate cert = chain.getEndEntityCert();
        if (!CertUtils.isAuthCert(cert)) {
            throw new CodedException(X_SSL_AUTH_FAILED,
                    "Peer certificate is not an authentication certificate");
        }

        log.debug("verifyAuthCert({}: {}, {})",
                new Object[] {cert.getSerialNumber(),
                        cert.getSubjectX500Principal().getName(), member });

        // Verify certificate against CAs.
        try {
            new CertChainVerifier(chain).verify(ocspResponses, new Date());
        } catch (CodedException e) {
            // meaningful errors get SSL auth verification prefix
            throw e.withPrefix(X_SSL_AUTH_FAILED);
        }

        // Verify (using GlobalConf) that given certificate can be used
        // to authenticate given member.
        if (!GlobalConf.authCertMatchesMember(cert, member)) {
            SecurityServerId serverId = GlobalConf.getServerId(cert);
            if (serverId != null) {
                throw new CodedException(X_SSL_AUTH_FAILED,
                        "Client '%s' is not registered at security server %s",
                        member, serverId);

            }

            throw new CodedException(X_SSL_AUTH_FAILED,
                    "Authentication certificate %s is not associated "
                            + "with any security server",
                            cert.getSubjectX500Principal());
        }
    }

    /**
     * Finds the OCSP response from a list of OCSP responses
     * for a given certificate.
     * @param cert the certificate
     * @param issuer the issuer of the certificate
     * @param ocspResponses list of OCSP responses
     * @return the OCSP response or null if not found
     * @throws Exception if an error occurs
     */
    public static OCSPResp getOcspResponseForCert(X509Certificate cert,
            X509Certificate issuer, List<OCSPResp> ocspResponses)
                    throws Exception {
        CertificateID certId = CryptoUtils.createCertId(cert, issuer);
        for (OCSPResp resp : ocspResponses) {
            BasicOCSPResp basicResp = (BasicOCSPResp) resp.getResponseObject();
            SingleResp singleResp = basicResp.getResponses()[0];
            if (certId.equals(singleResp.getCertID())) {
                return resp;
            }
        }

        return null;
    }
}
