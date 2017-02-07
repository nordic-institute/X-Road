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
package ee.ria.xroad.common;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.ocsp.BasicOCSPRespBuilder;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.OCSPRespBuilder;
import org.bouncycastle.cert.ocsp.RespID;
import org.bouncycastle.operator.ContentSigner;

import ee.ria.xroad.common.util.CryptoUtils;

/**
 * Contains utility methods for creating test OCSP responses.
 */
public final class OcspTestUtils {

    private OcspTestUtils() {
    }

    /**
     * Creates an OCSP response for the subject's certificate with the given status.
     * @param subject the subject certificate
     * @param issuer certificate of the subject certificate issuer
     * @param signer certificate of the OCSP response signer
     * @param signerKey key of the OCSP response signer
     * @param certStatus OCSP response status
     * @return OCSPResp
     * @throws Exception in case of any errors
     */
    public static OCSPResp createOCSPResponse(X509Certificate subject,
            X509Certificate issuer,
            X509Certificate signer, PrivateKey signerKey,
            CertificateStatus certStatus) throws Exception {
        return createOCSPResponse(subject, issuer, signer, signerKey,
                certStatus, null, null);
    }

    /**
     * Creates an OCSP response for the subject's certificate with the given status.
     * @param subject the subject certificate
     * @param issuer certificate of the subject certificate issuer
     * @param signer certificate of the OCSP response signer
     * @param signerKey key of the OCSP response signer
     * @param certStatus OCSP response status
     * @param thisUpdate date this response was valid on
     * @param nextUpdate date when next update should be requested
     * @return OCSPResp
     * @throws Exception in case of any errors
     */
    public static OCSPResp createOCSPResponse(X509Certificate subject,
            X509Certificate issuer, X509Certificate signer, PrivateKey signerKey,
            CertificateStatus certStatus, Date thisUpdate, Date nextUpdate)
                    throws Exception {
        BasicOCSPRespBuilder builder = new BasicOCSPRespBuilder(
                new RespID(new X500Name(
                        signer.getSubjectX500Principal().getName())));
        CertificateID cid = CryptoUtils.createCertId(subject, issuer);

        if (thisUpdate != null) {
            builder.addResponse(cid, certStatus, thisUpdate, nextUpdate, null);
        } else {
            builder.addResponse(cid, certStatus);
        }

        ContentSigner contentSigner = CryptoUtils.createContentSigner(
                subject.getSigAlgName(), signerKey);

        Object responseObject = builder.build(contentSigner, null, new Date());

        OCSPResp resp = new OCSPRespBuilder().build(
                OCSPRespBuilder.SUCCESSFUL, responseObject);
        return resp;
    }

    /**
     * Creates a "signature required" OCSP response.
     * @return OCSPResp
     * @throws Exception in case of any errors
     */
    public static OCSPResp createSigRequiredOCSPResponse() throws Exception {
        OCSPResp resp = new OCSPRespBuilder().build(
                OCSPRespBuilder.SIG_REQUIRED, null);
        return resp;
    }

}
