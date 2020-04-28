/**
 * The MIT License
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
package org.niis.xroad.restapi.util;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;

import java.util.List;

public final class ClientUtils {

    private ClientUtils() {
        // noop
    }

    /**
     *
     * @param clientId
     * @param certificateInfos
     * @return
     * @throws OcspUtils.OcspStatusExtractionException
     */
    public static boolean hasValidLocalSignCert(ClientId clientId, List<CertificateInfo> certificateInfos)
            throws OcspUtils.OcspStatusExtractionException {
        for (CertificateInfo certificateInfo : certificateInfos) {
            String ocspResponseStatus = OcspUtils.getOcspResponseStatus(certificateInfo.getOcspBytes());
            if (clientId.equals(certificateInfo.getMemberId())
                    && certificateInfo.getStatus().equals(CertificateInfo.STATUS_REGISTERED)
                    && ocspResponseStatus.equals(CertificateInfo.OCSP_RESPONSE_GOOD)) {
                return true;
            }
        }
        return false;
    }

}
