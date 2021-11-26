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
package org.niis.xroad.securityserver.restapi.openapi;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.message.CertificateRequestFormat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * helper for creating csr filenames
 */
@Component
@Slf4j
public class CsrFilenameCreator {
    public static final String INTERNAL_CSR_FILE_PREFIX = "internal_tls_cert_request_";
    public static final String INTERNAL_CSR_FILE_EXTENSION = ".p10";

    /**
     * Create a filename for CSR
     * @param keyUsageInfo
     * @param csrFormat
     * @param memberId
     * @param securityServerId
     * @return
     */
    public String createCsrFilename(KeyUsageInfo keyUsageInfo, CertificateRequestFormat csrFormat,
            ClientId memberId, SecurityServerId securityServerId) {
        StringBuilder builder = new StringBuilder();
        if (KeyUsageInfo.AUTHENTICATION == keyUsageInfo) {
            builder.append("auth");
        } else {
            builder.append("sign");
        }
        builder.append("_csr_");
        builder.append(createDateString());
        builder.append("_");
        builder.append(createIdentifier(keyUsageInfo, memberId, securityServerId));
        builder.append(".");
        builder.append(csrFormat.name().toLowerCase());
        return builder.toString();
    }

    String createDateString() {
        return LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private String createIdentifier(KeyUsageInfo keyUsageInfo,
            ClientId memberId, SecurityServerId securityServerId) {
        StringBuilder builder = new StringBuilder();
        if (KeyUsageInfo.AUTHENTICATION == keyUsageInfo) {
            builder.append("securityserver_");
            builder.append(securityServerId.getXRoadInstance());
            builder.append("_");
            builder.append(securityServerId.getMemberClass());
            builder.append("_");
            builder.append(securityServerId.getMemberCode());
            builder.append("_");
            builder.append(securityServerId.getServerCode());
        } else {
            builder.append("member_");
            builder.append(memberId.getXRoadInstance());
            builder.append("_");
            builder.append(memberId.getMemberClass());
            builder.append("_");
            builder.append(memberId.getMemberCode());
        }
        return builder.toString();
    }

    /**
     * Create a simple filename with the current date for internal cert CSR
     * @return
     */
    public String createInternalCsrFilename() {
        return INTERNAL_CSR_FILE_PREFIX + createDateString() + INTERNAL_CSR_FILE_EXTENSION;
    }
}
