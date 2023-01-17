/**
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
package org.niis.xroad.securityserver.restapi.util;

import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;

import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.SingleResp;

import java.io.IOException;

/**
 * Util class for working with OCSP responses
 */
public final class OcspUtils {
    private OcspUtils() {
        // noop
    }

    /**
     * {@link OcspUtils#getOcspResponseStatus(byte[])}
     * @param base64EncodedOcspResponse base 64 encoded ocsp response. If empty, returns null
     */
    public static String getOcspResponseStatus(String base64EncodedOcspResponse) throws OcspStatusExtractionException {
        if (StringUtils.isEmpty(base64EncodedOcspResponse)) {
            return null;
        }
        return getOcspResponseStatus(CryptoUtils.decodeBase64(base64EncodedOcspResponse));
    }

    /**
     * Returns OCSP response status as String. Values are from CertificateInfo constants,
     * e.g. CertificateInfo.OCSP_RESPONSE_GOOD.
     * Logic follows what sysparams_controller.rb and token_renderer.rb had.
     * @param ocspResponse
     * @throws OcspStatusExtractionException if OCSP status extraction failed for some reason
     * @return String representing the status
     */
    public static String getOcspResponseStatus(byte[] ocspResponse) throws OcspStatusExtractionException {
        if (ocspResponse == null || ocspResponse.length == 0) {
            return CertificateInfo.OCSP_RESPONSE_UNKNOWN;
        }
        CertificateStatus certificateStatus = getCertificateStatus(ocspResponse);
        if (certificateStatus == null) {
            return CertificateInfo.OCSP_RESPONSE_GOOD;
        }
        if (certificateStatus instanceof RevokedStatus) {
            RevokedStatus revokedStatus = (RevokedStatus) certificateStatus;
            if (revokedStatus.hasRevocationReason()
                    && revokedStatus.getRevocationReason() == CRLReason.certificateHold) {
                return CertificateInfo.OCSP_RESPONSE_SUSPENDED;
            }
            return CertificateInfo.OCSP_RESPONSE_REVOKED;
        }
        return CertificateInfo.OCSP_RESPONSE_UNKNOWN;
    }

    /**
     * From ee.ria.xroad.signer.console.Utils#getOcspStatus
     * @param ocspBytes
     * @return
     */
    private static CertificateStatus getCertificateStatus(byte[] ocspBytes) throws OcspStatusExtractionException {
        try {
            OCSPResp response = new OCSPResp(ocspBytes);
            BasicOCSPResp basicResponse = (BasicOCSPResp) response.getResponseObject();
            SingleResp resp = basicResponse.getResponses()[0];
            CertificateStatus status = resp.getCertStatus();
            return status;
        } catch (IOException | OCSPException e) {
            throw new OcspStatusExtractionException(e);
        }
    }

    /**
     * Thrown when attempt to extract ocsp status failed
     */
    public static class OcspStatusExtractionException extends Exception {
        public OcspStatusExtractionException(Throwable throwable) {
            super(throwable);
        }
    }


}
