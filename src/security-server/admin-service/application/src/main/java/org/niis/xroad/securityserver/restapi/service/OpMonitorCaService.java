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
import org.niis.xroad.common.core.cert.CaCertificateFileValidator;
import org.niis.xroad.common.core.exception.ErrorDeviation;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.InternalServerErrorException;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.security.cert.X509Certificate;
import java.util.Optional;

import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;

@Slf4j
@Service
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class OpMonitorCaService {

    private final VaultClient vaultClient;
    private final AuditDataHelper auditDataHelper;

    public Optional<X509Certificate> getOpMonitorCa() {
        try {
            return vaultClient.getOpMonitorCa();
        } catch (RuntimeException e) {
            throw new InternalServerErrorException(e, INTERNAL_ERROR.build());
        }
    }

    /**
     * Validates and stores an uploaded op-monitor CA, replacing whatever was stored before.
     *
     * @param certificateFileBytes the uploaded file
     * @return the CA certificate the file holds
     */
    public X509Certificate uploadOpMonitorCa(byte[] certificateFileBytes) {
        X509Certificate certificate = validate(certificateFileBytes);
        auditDataHelper.putCertificateHash(certificate);
        store(certificate);
        return certificate;
    }

    /**
     * Clears the stored op-monitor CA. Succeeds when none is stored or the stored one cannot be read.
     */
    public void deleteOpMonitorCa() {
        auditStoredCaHash();
        try {
            vaultClient.clearOpMonitorCa();
        } catch (XrdRuntimeException e) {
            throw new InternalServerErrorException(e, new ErrorDeviation(e.getCode()));
        } catch (Exception e) {
            throw new InternalServerErrorException(e, INTERNAL_ERROR.build());
        }
        log.info("Cleared the op-monitor CA");
    }

    private void auditStoredCaHash() {
        try {
            vaultClient.getOpMonitorCa().ifPresent(auditDataHelper::putCertificateHash);
        } catch (RuntimeException e) {
            log.warn("Could not read the stored op-monitor CA for the audit record: {}", e.getMessage());
        }
    }

    private X509Certificate validate(byte[] certificateFileBytes) {
        try {
            return CaCertificateFileValidator.validate(certificateFileBytes);
        } catch (XrdRuntimeException e) {
            throw new BadRequestException(e.getDetails(), new ErrorDeviation(e.getCode()));
        }
    }

    private void store(X509Certificate certificate) {
        try {
            vaultClient.createOpMonitorCa(certificate);
        } catch (XrdRuntimeException e) {
            throw new InternalServerErrorException(e, new ErrorDeviation(e.getCode()));
        } catch (Exception e) {
            throw new InternalServerErrorException(e, INTERNAL_ERROR.build());
        }
        log.info("Stored the op-monitor CA");
    }
}
