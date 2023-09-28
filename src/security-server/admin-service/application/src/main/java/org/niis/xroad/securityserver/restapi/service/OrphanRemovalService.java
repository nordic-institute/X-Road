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

import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.signer.protocol.dto.CertRequestInfo;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.service.NotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_ORPHANS_NOT_FOUND;

/**
 * orphan cert and csr removal service
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class OrphanRemovalService {

    private final TokenService tokenService;
    private final TokenCertificateService tokenCertificateService;
    private final ClientService clientService;
    private final KeyService keyService;
    private final AuditDataHelper auditDataHelper;

    /**
     * Returns true, if orphaned keys, certs or csrs exist for given clientId.
     * Orphaned items exist if
     * - there are no local clients with same instance_id, member class, member name as clientId
     * - orphan csrs: sign csrs linked to this clientId's member
     * - orphan cert: sign certs linked to this clientId's member
     * - orphan keys: sign keys with at least one orphan csr or cert, and no csrs or certs for other members
     * @param clientId
     * @return
     */
    public boolean orphansExist(ClientId clientId) {
        if (isAlive(clientId) || hasAliveSiblings(clientId)) {
            return false;
        }

        Orphans orphans = findOrphans(clientId);
        return !orphans.isEmpty();
    }

    private boolean hasAliveSiblings(ClientId clientId) {
        // find out if siblings
        Optional<ClientType> sibling = clientService.getAllLocalClients().stream()
                .filter(c -> c.getIdentifier().memberEquals(clientId))
                .findFirst();
        if (sibling.isPresent()) {
            return true;
        }
        return false;
    }

    private boolean isAlive(ClientId clientId) {
        ClientType clientType = clientService.getLocalClient(clientId);
        if (clientType != null) {
            // cant have orphans if still alive
            return true;
        }
        return false;
    }

    /**
     * Finds Orphans for a deleted client. Assumes that client
     * is indeed deleted, and does not have alive siblings.
     */
    Orphans findOrphans(ClientId clientId) {
        // find out which orphan keys, certs and csrs exist
        List<TokenInfo> tokens = tokenService.getAllTokens();
        List<KeyInfo> orphanKeys = findOrphanKeys(clientId, tokens);

        List<KeyInfo> otherKeys = tokens.stream()
                .flatMap(tokenInfo -> tokenInfo.getKeyInfo().stream())
                .filter(keyInfo -> !orphanKeys.contains(keyInfo))
                .collect(Collectors.toList());

        List<CertificateInfo> orphanCerts = otherKeys.stream()
                .flatMap(keyInfo -> getCerts(keyInfo, clientId).stream())
                .collect(Collectors.toList());

        List<CertRequestInfo> orphanCsrs = otherKeys.stream()
                .flatMap(keyInfo -> getCsrs(keyInfo, clientId).stream())
                .collect(Collectors.toList());

        Orphans orphans = new Orphans();
        orphans.setKeys(orphanKeys);
        orphans.setCerts(orphanCerts);
        orphans.setCsrs(orphanCsrs);
        return orphans;
    }

    /**
     * Orphans by type
     */
    @Data
    class Orphans {
        private List<KeyInfo> keys;
        private List<CertificateInfo> certs;
        private List<CertRequestInfo> csrs;
        boolean isEmpty() {
            return keys.isEmpty() && certs.isEmpty() && csrs.isEmpty();
        }
    }

    private List<KeyInfo> findOrphanKeys(ClientId clientId, List<TokenInfo> tokens) {
        // find keys with some, and only, certs/csrs that belong to this client's member
        return tokens.stream()
                .flatMap(tokenInfo -> tokenInfo.getKeyInfo().stream())
                .filter(keyInfo -> isOrphanKey(keyInfo, clientId))
                .collect(Collectors.toList());
    }

    /**
     * Check if this key is this client's orphan (some certs / csrs exist,
     * and all of the belong to this client's member)
     */
    boolean isOrphanKey(KeyInfo keyInfo, ClientId clientId) {
        List<CertificateInfo> orphanCerts = getCerts(keyInfo, clientId);
        List<CertRequestInfo> orphanCsrs = getCsrs(keyInfo, clientId);
        if (!orphanCerts.isEmpty() || !orphanCsrs.isEmpty()) {
            // some orphan certs or csrs, make sure there are none that belong to others
            List<CertificateInfo> otherCerts = new ArrayList<>(keyInfo.getCerts());
            otherCerts.removeAll(orphanCerts);
            List<CertRequestInfo> otherCsrs = new ArrayList<>(keyInfo.getCertRequests());
            otherCsrs.removeAll(orphanCsrs);
            if (otherCerts.isEmpty() && otherCsrs.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get (sign) certs in this key that belong to this client's member
     */
    private List<CertificateInfo> getCerts(KeyInfo keyInfo, ClientId clientId) {
        return keyInfo.getCerts().stream()
                .filter(cert -> clientId.memberEquals(cert.getMemberId()))
                .collect(Collectors.toList());
    }

    /**
     * Get (sign) csrs in this key that belong to this client's member
     */
    private List<CertRequestInfo> getCsrs(KeyInfo keyInfo, ClientId clientId) {
        return keyInfo.getCertRequests().stream()
                .filter(csr -> clientId.memberEquals(csr.getMemberId()))
                .collect(Collectors.toList());
    }

    /**
     * Deletes orphan keys, certs and csrs for given clientId
     * @param clientId
     * @throws OrphansNotFoundException if orphans dont exist for this client. Possible reasons
     * include also that this client is still alive (not deleted).
     * @throws ActionNotPossibleException if delete-cert or delete-csr was not possible action
     * @throws GlobalConfOutdatedException
     * if global conf is outdated. This prevents key deletion.
     */
    public void deleteOrphans(ClientId clientId) throws OrphansNotFoundException,
            ActionNotPossibleException, GlobalConfOutdatedException {

        auditDataHelper.put(clientId);

        if (isAlive(clientId) || hasAliveSiblings(clientId)) {
            throw new OrphansNotFoundException();
        }

        Orphans orphans = findOrphans(clientId);
        if (orphans.isEmpty()) {
            throw new OrphansNotFoundException();
        }
        try {
            // delete the orphans
            for (KeyInfo keyInfo : orphans.getKeys()) {
                keyService.deleteKeyAndIgnoreWarnings(keyInfo.getId());
            }
            tokenCertificateService.deleteCertificates(orphans.getCerts());
            for (CertRequestInfo certRequestInfo : orphans.getCsrs()) {
                tokenCertificateService.deleteCsr(certRequestInfo.getId());
            }
        } catch (KeyNotFoundException | CsrNotFoundException | CertificateNotFoundException e) {
            // we just internally looked up these items, so them not being found is an internal error
            throw new RuntimeException(e);
        }
    }

    /**
     * Thrown when someone tries to remove orphans, but none exist
     */
    public static class OrphansNotFoundException extends NotFoundException {
        public OrphansNotFoundException() {
            super(new ErrorDeviation(ERROR_ORPHANS_NOT_FOUND));
        }
    }
}
