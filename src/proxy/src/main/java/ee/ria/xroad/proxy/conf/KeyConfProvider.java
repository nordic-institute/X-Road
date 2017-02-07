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

import java.security.cert.X509Certificate;
import java.util.List;

import org.bouncycastle.cert.ocsp.OCSPResp;

import ee.ria.xroad.common.conf.globalconf.AuthKey;
import ee.ria.xroad.common.identifier.ClientId;

/**
 * Declares methods for accessing key configuration.
 */
public interface KeyConfProvider {

    /**
     * @return security (signing) context for given member.
     * @param memberId client ID of the member
     */
    SigningCtx getSigningCtx(ClientId memberId);

    /**
     * @return the current key and certificate for SSL authentication.
     */
    AuthKey getAuthKey();

    /**
     * @return the OCSP server response for the given certificate hash,
     * or null, if no response is available for that certificate.
     * @param cert the certificate
     * @throws Exception in case of any errors
     */
    OCSPResp getOcspResponse(X509Certificate cert) throws Exception;

    /**
     * @return the OCSP server response for the given certificate hash,
     * or null, if no response is available for that certificate.
     * @param certHash hash of the certificate
     * @throws Exception in case of any errors
     */
    OCSPResp getOcspResponse(String certHash) throws Exception;

    /**
     * @return OCSP responses for given certificates.
     * @param certs list of certificates
     * @throws Exception in case of any errors
     */
    List<OCSPResp> getOcspResponses(List<X509Certificate> certs)
            throws Exception;

    /**
     * Updates the existing OCSP response or stores the OCSP response,
     * if it does not exist for the given certificate.
     * @param certs list of certificates
     * @param responses list of OCSP responses
     * @throws Exception in case of any errors
     */
    void setOcspResponses(List<X509Certificate> certs,
            List<OCSPResp> responses) throws Exception;

}
