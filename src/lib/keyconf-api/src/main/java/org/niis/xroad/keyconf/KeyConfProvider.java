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
package org.niis.xroad.keyconf;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CryptoUtils;

import org.bouncycastle.cert.ocsp.OCSPResp;
import org.niis.xroad.keyconf.dto.AuthKey;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * Declares methods for accessing key configuration.
 */
public interface KeyConfProvider {

    /**
     * @param clientId client ID of the member
     * @return security (signing) context for given member.
     */
    SigningInfo getSigningInfo(ClientId clientId);

    /**
     * @return the current key and certificate for SSL authentication.
     */
    AuthKey getAuthKey();

    /**
     * @param cert the certificate
     * @return the OCSP server response for the given certificate hash,
     * or null, if no response is available for that certificate.
     * @throws Exception in case of any errors
     */
    OCSPResp getOcspResponse(X509Certificate cert) throws Exception;

    /**
     * @param certHash hash of the certificate
     * @return the OCSP server response for the given certificate hash,
     * or null, if no response is available for that certificate.
     * @throws Exception in case of any errors
     */
    OCSPResp getOcspResponse(String certHash) throws Exception;

    /**
     * @param certs list of certificates
     * @return OCSP responses for given certificates.
     * @throws Exception in case of any errors
     */
    List<OCSPResp> getOcspResponses(List<X509Certificate> certs)
            throws Exception;

    default List<OCSPResp> getAllOcspResponses(List<X509Certificate> certs) throws Exception {
        List<String> missingResponses = new ArrayList<>();
        List<OCSPResp> responses = getOcspResponses(certs);
        for (int i = 0; i < certs.size(); i++) {
            if (responses.get(i) == null) {
                missingResponses.add(CryptoUtils.calculateCertHexHash(certs.get(i)));
            }
        }

        if (!missingResponses.isEmpty()) {
            throw new CodedException(ErrorCodes.X_CANNOT_CREATE_SIGNATURE,
                    "Could not get OCSP responses for certificates (%s)",
                    missingResponses);
        }

        return responses;
    }

    /**
     * Updates the existing OCSP response or stores the OCSP response,
     * if it does not exist for the given certificate.
     *
     * @param certs     list of certificates
     * @param responses list of OCSP responses
     * @throws Exception in case of any errors
     */
    void setOcspResponses(List<X509Certificate> certs,
                          List<OCSPResp> responses) throws Exception;

    /**
     * Cleans up any resources hold by KeyConf Provider
     */
    default void destroy() {
        //NOP
    }

}
