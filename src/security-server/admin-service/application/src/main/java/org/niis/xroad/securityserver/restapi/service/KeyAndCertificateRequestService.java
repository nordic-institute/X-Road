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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.signer.SignerProxy.GeneratedCertRequestInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.signer.proto.CertificateRequestFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * key + csr service
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class KeyAndCertificateRequestService {

    private final KeyService keyService;
    private final TokenCertificateService tokenCertificateService;

    @Autowired
    public KeyAndCertificateRequestService(KeyService keyService,
                                           TokenCertificateService tokenCertificateService) {
        this.keyService = keyService;
        this.tokenCertificateService = tokenCertificateService;
    }

    /**
     * DTO for passing key & csr data in a single object
     */
    @Value
    public static class KeyAndCertRequestInfo {
        private final KeyInfo keyInfo;
        private final String certReqId;
        private final byte[] certRequest;
        private final CertificateRequestFormat format;
        private final ClientId memberId;
        private final KeyUsageInfo keyUsage;
    }

    /**
     * Add a new key and create a csr for it
     * @param tokenId
     * @param keyLabel
     * @param memberId
     * @param keyUsageInfo
     * @param caName
     * @param subjectFieldValues
     * @param csrFormat
     * @return
     * @throws ActionNotPossibleException if add key or generate csr was not possible
     * @throws ClientNotFoundException if client with {@code memberId} id was not found
     * @throws CertificateAuthorityNotFoundException if ca authority with name {@code caName} does not exist
     * @throws TokenNotFoundException if token with {@code tokenId} was not found
     * @throws DnFieldHelper.InvalidDnParameterException if required dn parameters were missing, or if there
     * were some extra parameters
     */
    public KeyAndCertRequestInfo addKeyAndCertRequest(String tokenId, String keyLabel,
                                                      ClientId.Conf memberId, KeyUsageInfo keyUsageInfo, String caName,
                                                      Map<String, String> subjectFieldValues, CertificateRequestFormat csrFormat)
            throws ActionNotPossibleException,
            ClientNotFoundException, CertificateAuthorityNotFoundException, TokenNotFoundException,
            DnFieldHelper.InvalidDnParameterException {

        KeyInfo keyInfo = keyService.addKey(tokenId, keyLabel);
        GeneratedCertRequestInfo csrInfo;
        boolean csrGenerateSuccess = false;
        Exception csrGenerateException = null;
        try {
            csrInfo = tokenCertificateService.generateCertRequest(keyInfo.getId(),
                    memberId, keyUsageInfo, caName,
                    subjectFieldValues, csrFormat);
            csrGenerateSuccess = true;
        } catch (KeyNotFoundException | WrongKeyUsageException e) {
            csrGenerateException = e;
            // since we just generated the key, neither of these should happen
            // these should only happen if someone else updated / deleted the key between
            // create key & generateCertRequest
            throw new DeviationAwareRuntimeException(e, e.getErrorDeviation());
        } catch (Exception e) {
            csrGenerateException = e;
            throw e;
        } finally {
            // only rollback if we caught the exception.
            // In case of Errors, we do not want to attempt rollback
            if (csrGenerateException != null) {
                tryRollbackCreateKey(csrGenerateException, keyInfo.getId());
            } else if (!csrGenerateSuccess) {
                log.error("csr generate failed -create key rollback was not attempted since failure "
                        + "was not due to an Exception (we do not catch Errors)");
            }
        }
        // get a new keyInfo that contains the csr
        KeyInfo refreshedKeyInfo;
        try {
            refreshedKeyInfo = keyService.getKey(keyInfo.getId());
        } catch (KeyNotFoundException e) {
            throw new DeviationAwareRuntimeException(e, e.getErrorDeviation());
        }
        KeyAndCertRequestInfo info = new KeyAndCertRequestInfo(refreshedKeyInfo,
                csrInfo.getCertReqId(),
                csrInfo.getCertRequest(),
                csrInfo.getFormat(),
                csrInfo.getMemberId(),
                csrInfo.getKeyUsage());

        return info;
    }

    /**
     * Rollback key creation by deleting that key
     * @param rootCause root cause why we rollback, to log in case new exceptions would mask it
     * @param keyId key id
     */
    private void tryRollbackCreateKey(Exception rootCause, String keyId) {
        // log error in case deleteKey throws an error, to not mask the original exception
        boolean rollbackSuccess = false;
        try {
            keyService.deleteKeyAndIgnoreWarnings(keyId);
            rollbackSuccess = true;
        } catch (GlobalConfOutdatedException e) {
            // should not happen, since only thrown from unregister cert (which wont be done)
            throw new DeviationAwareRuntimeException(e, e.getErrorDeviation());
        } catch (KeyNotFoundException | ActionNotPossibleException e) {
            // this should be rare situations since we just created the key -> not checked exceptions
            throw new DeviationAwareRuntimeException(e, e.getErrorDeviation());
        } finally {
            if (!rollbackSuccess) {
                log.error("csr generate failed, key create rollback also failed."
                        + " Logging csr generate exception here, throwing key create rollback exception", rootCause);
            }
        }

    }
}
