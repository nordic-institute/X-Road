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
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.identifier.ClientId;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * orphan cert and csr removal service
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class OrphanRemovalService {

    private final TokenService tokenService;
    private final TokenCertificateService tokenCertificateService;

    @Autowired
    public OrphanRemovalService(TokenService tokenService,
            @Lazy TokenCertificateService tokenCertificateService) {
        this.tokenService = tokenService;
        this.tokenCertificateService = tokenCertificateService;
    }

    public boolean orphanCertsOrCsrsExist(ClientId clientId) {
        return false;
    }

    /**
     *
     * @param clientId
     * @throws NotOrphansException if certs and csrs were not orphans; there was some client associated with them
     * @throws ActionNotPossibleException if delete-cert or delete-csr was not possible action
     */
    public void deleteOrphanCertsAndCsrs(ClientId clientId) throws NotOrphansException,
            ActionNotPossibleException {
        try {
            tokenCertificateService.deleteCertificate(null);
            tokenCertificateService.deleteCsr(null);
        } catch (CsrNotFoundException | CertificateNotFoundException | KeyNotFoundException e) {
            // we searched for csrs, certs and keys ourselves, so they should not be lost
            throw new RuntimeException(e);
        }
    }

    /**
     * Thrown when someone tries to remove orphan certs and csrs,
     * but clients having same member data exist
     */
    public static class NotOrphansException extends ServiceException {
        public static final String ERROR_NOT_ORPHANS = "other_clients_may_share_same_certs";
        public NotOrphansException(String s) {
            super(s, new ErrorDeviation(ERROR_NOT_ORPHANS));
        }
    }


}
