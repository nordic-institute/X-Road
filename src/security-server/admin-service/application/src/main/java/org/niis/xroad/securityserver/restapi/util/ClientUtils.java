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
package org.niis.xroad.securityserver.restapi.util;

import ee.ria.xroad.common.identifier.ClientId;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.api.dto.CertificateInfo;

import java.util.List;

@Slf4j
public final class ClientUtils {

    public static final String ERROR_OCSP_EXTRACT_MSG = "Failed to extract OCSP status for local sign certificate";

    public static final int GLOBAL_CONF_VERSION_WITH_SUBSYSTEM_NAMES = 5;

    private ClientUtils() {
        // noop
    }

    /**
     * @param clientId
     * @param certificateInfos
     * @return
     */
    public static boolean hasValidLocalSignCert(ClientId clientId, List<CertificateInfo> certificateInfos) {
        for (CertificateInfo certificateInfo : certificateInfos) {
            if (!clientId.memberEquals(certificateInfo.getMemberId())) {
                continue;
            }
            String ocspResponseStatus = null;
            try {
                ocspResponseStatus = OcspUtils.getOcspResponseStatus(certificateInfo.getOcspBytes());
            } catch (OcspUtils.OcspStatusExtractionException e) {
                log.error(ERROR_OCSP_EXTRACT_MSG + " for client: " + clientId, e);
                break;
            }
            if (certificateInfo.getStatus().equals(CertificateInfo.STATUS_REGISTERED)
                    && ocspResponseStatus.equals(CertificateInfo.OCSP_RESPONSE_GOOD)) {
                return true;
            }
        }
        return false;
    }

    public static boolean doesSupportSubsystemNames(int globalConfVersion) {
        return globalConfVersion >= GLOBAL_CONF_VERSION_WITH_SUBSYSTEM_NAMES;
    }
}
