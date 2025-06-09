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
package org.niis.xroad.signer.jpa.service.impl;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.niis.xroad.serverconf.impl.ServerConfDatabaseCtx;
import org.niis.xroad.serverconf.impl.dao.SignerKeyCertDaoImpl;
import org.niis.xroad.serverconf.impl.dao.SignerKeyCertRequestDaoImpl;
import org.niis.xroad.serverconf.impl.dao.SignerKeyDaoImpl;
import org.niis.xroad.serverconf.impl.dao.SignerTokenDaoImpl;
import org.niis.xroad.serverconf.impl.entity.SignerCertRequestEntity;
import org.niis.xroad.serverconf.impl.entity.SignerCertificateEntity;
import org.niis.xroad.serverconf.impl.entity.SignerKeyEntity;
import org.niis.xroad.serverconf.impl.entity.SignerTokenEntity;
import org.niis.xroad.signer.core.model.BasicCertInfo;
import org.niis.xroad.signer.core.model.BasicKeyInfo;
import org.niis.xroad.signer.core.model.BasicTokenInfo;
import org.niis.xroad.signer.core.model.CertRequestData;
import org.niis.xroad.signer.core.service.TokenService;
import org.niis.xroad.signer.jpa.mapper.CertMapper;
import org.niis.xroad.signer.jpa.mapper.CertRequestMapper;
import org.niis.xroad.signer.jpa.mapper.KeyMapper;
import org.niis.xroad.signer.jpa.mapper.TokenMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Holds the current keys & certificates in XML.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {
    private final ServerConfDatabaseCtx serverConfDatabaseCtx;
    private final SignerTokenDaoImpl tokenDao;
    private final SignerKeyDaoImpl keyDao;
    private final SignerKeyCertDaoImpl certDao;
    private final SignerKeyCertRequestDaoImpl certRequestDao;
    private final TokenMapper tokenMapper;
    private final KeyMapper keyMapper;
    private final CertMapper certMapper;
    private final CertRequestMapper certRequestMapper;

    @Override
    public LoadedTokens loadAllTokens() throws Exception {
        return serverConfDatabaseCtx.doInTransaction(this::loadedTokens);
    }

    @Override
    public void delete(Long tokenId) {
        tokenDao.deleteById(serverConfDatabaseCtx.getSession(), SignerTokenEntity.class, tokenId);
    }

    @Override
    public Long save(String externalId, String type, String friendlyName, String label, String serialNo) throws Exception {
        var entity = new SignerTokenEntity();
        entity.setExternalId(externalId);
        entity.setType(type);
        entity.setFriendlyName(friendlyName);
        entity.setLabel(label);
        entity.setSerialNo(serialNo);
        entity.setPin(null);

        return serverConfDatabaseCtx.doInTransaction(session ->
                tokenDao.save(session, entity).getId());
    }

    @Override
    public boolean setInitialTokenPin(Long id, byte[] pinHash) throws Exception {
        return serverConfDatabaseCtx.doInTransaction(session -> tokenDao.updatePin(session, id, pinHash));
    }


    @Override
    public boolean updateTokenPin(Long id, Map<Long, byte[]> updatedKeys, byte[] pinHash) throws Exception {
        return serverConfDatabaseCtx.doInTransaction(session -> {
            if (tokenDao.updatePin(session, id, pinHash)) {
                updatedKeys.forEach((keyId, keyStore) -> {
                    if (!keyDao.updateKeystore(session, keyId, keyStore)) {
                        log.warn("Failed to update keystore for key with id: {}", keyId);
                    }
                });
                return true;
            }
            return false;
        });
    }

    @Override
    public boolean updateFriendlyName(Long id, String friendlyName) throws Exception {
        return serverConfDatabaseCtx.doInTransaction(session -> tokenDao.updateFriendlyName(session, id, friendlyName));

    }

    private LoadedTokens loadedTokens(Session session) {
        Set<BasicTokenInfo> tokens = tokenDao.findAll(session, SignerTokenEntity.class).stream()
                .map(tokenMapper::toTarget)
                .collect(Collectors.toSet());

        Map<Long, List<BasicKeyInfo>> keys = keyDao.findAll(session, SignerKeyEntity.class).stream()
                .map(keyMapper::toTarget)
                .collect(Collectors.groupingBy(BasicKeyInfo::tokenId,
                        Collectors.mapping(key -> key, Collectors.toList())));

        Map<Long, List<BasicCertInfo>> certs = certDao.findAll(session, SignerCertificateEntity.class).stream()
                .map(certMapper::toTarget)
                .collect(Collectors.groupingBy(BasicCertInfo::keyId,
                        Collectors.mapping(cert -> cert, Collectors.toList())));

        Map<Long, List<CertRequestData>> certRequests = certRequestDao.findAll(session, SignerCertRequestEntity.class).stream()
                .map(certRequestMapper::toTarget)
                .collect(Collectors.groupingBy(CertRequestData::keyId,
                        Collectors.mapping(certRequest -> certRequest, Collectors.toList())));

        return new LoadedTokens(tokens, keys, certs, certRequests);
    }
}
