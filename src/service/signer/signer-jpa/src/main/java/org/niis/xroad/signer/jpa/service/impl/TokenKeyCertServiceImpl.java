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

package org.niis.xroad.signer.jpa.service.impl;

import ee.ria.xroad.common.identifier.ClientId;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.serverconf.impl.ServerConfDatabaseCtx;
import org.niis.xroad.serverconf.impl.dao.IdentifierDAOImpl;
import org.niis.xroad.serverconf.impl.dao.SignerKeyCertDaoImpl;
import org.niis.xroad.serverconf.impl.entity.SignerCertificateEntity;
import org.niis.xroad.signer.core.service.TokenKeyCertService;

import java.time.Instant;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class TokenKeyCertServiceImpl implements TokenKeyCertService {
    private final ServerConfDatabaseCtx serverConfDatabaseCtx;
    private final SignerKeyCertDaoImpl keyCertDao;
    private final IdentifierDAOImpl identifierDAO;

    @Override
    public Long save(Long keyId, String externalId, ClientId memberId, String status, byte[] certBytes) throws Exception {
        return serverConfDatabaseCtx.doInTransaction(session -> {
            var cert = new SignerCertificateEntity();
            cert.setKeyId(keyId);
            cert.setExternalId(externalId);
            cert.setData(certBytes);
            cert.setStatus(status);

            if (memberId != null) {
                cert.setMember(identifierDAO.findOrCreateClientId(session, memberId));
            }

            keyCertDao.save(session, cert);
            return cert.getId();
        });
    }

    @Override
    public boolean delete(Long id) throws Exception {
        return serverConfDatabaseCtx.doInTransaction(session -> keyCertDao.deleteById(session, SignerCertificateEntity.class, id));
    }

    @Override
    public boolean setActive(Long id, boolean active) throws Exception {
        return serverConfDatabaseCtx.doInTransaction(session -> keyCertDao.setActive(session, id, active));
    }

    @Override
    public boolean updateStatus(Long id, String status) throws Exception {
        return serverConfDatabaseCtx.doInTransaction(session -> keyCertDao.updateStatus(session, id, status));
    }

    @Override
    public boolean updateRenewedCertHash(Long id, String renewedCertHash) throws Exception {
        return serverConfDatabaseCtx.doInTransaction(session -> keyCertDao.updateRenewedCertHash(session, id, renewedCertHash));
    }

    @Override
    public boolean updateRenewalError(Long id, String renewalError) throws Exception {
        return serverConfDatabaseCtx.doInTransaction(session -> keyCertDao.updateRenewalError(session, id, renewalError));
    }

    @Override
    public boolean updateNextAutomaticRenewalTime(Long id, Instant nextRenewalTime) throws Exception {
        return serverConfDatabaseCtx.doInTransaction(session -> keyCertDao.updateNextAutomaticRenewalTime(session, id, nextRenewalTime));
    }

    @Override
    public boolean updateOcspVerifyBeforeActivationError(Long certId, String ocspVerifyBeforeActivationError) throws Exception {
        return serverConfDatabaseCtx.doInTransaction(session ->
                keyCertDao.updateOcspVerifyBeforeActivationError(session, certId, ocspVerifyBeforeActivationError));
    }
}
