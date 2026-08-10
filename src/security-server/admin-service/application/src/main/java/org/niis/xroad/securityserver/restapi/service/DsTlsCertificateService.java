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
package org.niis.xroad.securityserver.restapi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.validator.DsTlsMaterialValidator;
import org.springframework.stereotype.Service;

import java.security.cert.X509Certificate;

import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_CERTIFICATE_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_SECRET;

@Slf4j
@Service
@RequiredArgsConstructor
public class DsTlsCertificateService {

    private final VaultClient vaultClient;
    private final DsTlsMaterialValidator dsTlsMaterialValidator;
    private final AuditDataHelper auditDataHelper;

    public X509Certificate getDataspaceTlsCertificate() {
        try {
            return vaultClient.getDsHttpsTlsCredentials().getCertChain()[0];
        } catch (XrdRuntimeException e) {
            if (e.isCausedBy(MISSING_SECRET)) {
                throw new NotFoundException(DS_TLS_CERTIFICATE_NOT_FOUND.build());
            }
            throw e;
        } catch (Exception e) {
            log.error("Failed to read dataspace TLS certificate", e);
            throw XrdRuntimeException.systemException(INTERNAL_ERROR, e);
        }
    }

    public X509Certificate uploadDataspaceTlsCertificate(byte[] keyBytes, byte[] certificateChainBytes) {
        var validated = dsTlsMaterialValidator.validate(keyBytes, certificateChainBytes);
        X509Certificate leafCertificate = validated.getCertChain()[0];

        try {
            vaultClient.createDsHttpsTlsCredentials(validated);
        } catch (Exception e) {
            log.error("Failed to store dataspace TLS certificate", e);
            throw XrdRuntimeException.systemException(INTERNAL_ERROR, e);
        }

        auditDataHelper.putCertificateHash(leafCertificate);
        return leafCertificate;
    }
}
