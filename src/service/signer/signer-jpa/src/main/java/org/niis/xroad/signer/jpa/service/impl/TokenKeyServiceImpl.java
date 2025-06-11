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

import ee.ria.xroad.common.crypto.identifier.SignMechanism;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.serverconf.impl.ServerConfDatabaseCtx;
import org.niis.xroad.serverconf.impl.dao.SignerKeyDaoImpl;
import org.niis.xroad.serverconf.impl.entity.SignerKeyEntity;
import org.niis.xroad.serverconf.impl.entity.type.KeyType;
import org.niis.xroad.serverconf.impl.entity.type.KeyUsage;
import org.niis.xroad.signer.core.service.TokenKeyService;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class TokenKeyServiceImpl implements TokenKeyService {
    private final ServerConfDatabaseCtx serverConfDatabaseCtx;
    private final SignerKeyDaoImpl keyDao;

    @Override
    public Long save(Long tokenId, String keyId, String publicKeyBase64, byte[] keyStore,
                     SignMechanism signMechanism,
                     String friendlyName, String label, boolean softwareKey) throws Exception {
        var entity = new SignerKeyEntity();
        entity.setExternalId(keyId);
        entity.setTokenId(tokenId);
        entity.setType(softwareKey ? KeyType.SOFTWARE : KeyType.HARDWARE);
        entity.setFriendlyName(friendlyName);
        entity.setLabel(label);
        entity.setSignMechanismName(signMechanism);
        entity.setPublicKey(publicKeyBase64);
        entity.setKeyStore(keyStore);

        return serverConfDatabaseCtx.doInTransaction(session -> keyDao.save(session, entity).getId());
    }

    @Override
    public boolean delete(Long id) throws Exception {
        return serverConfDatabaseCtx.doInTransaction(session -> keyDao.deleteById(session, SignerKeyEntity.class, id));
    }

    @Override
    public boolean updateFriendlyName(Long id, String friendlyName) throws Exception {
        return serverConfDatabaseCtx.doInTransaction(session -> keyDao.updateFriendlyName(session, id, friendlyName));
    }

    @Override
    public boolean updateLabel(Long id, String label) throws Exception {
        return serverConfDatabaseCtx.doInTransaction(session -> keyDao.updateLabel(session, id, label));
    }

    @Override
    public boolean updateKeyUsage(Long id, KeyUsageInfo keyUsageInfo) throws Exception {
        KeyUsage keyUsage = switch (keyUsageInfo) {
            case SIGNING -> KeyUsage.SIGNING;
            case AUTHENTICATION -> KeyUsage.AUTHENTICATION;
            default -> null;
        };

        return serverConfDatabaseCtx.doInTransaction(session -> keyDao.updateKeyUsage(session, id, keyUsage));
    }

    @Override
    public boolean updatePublicKey(Long id, String publicKey) throws Exception {
        return serverConfDatabaseCtx.doInTransaction(session -> keyDao.updatePublicKey(session, id, publicKey));
    }
}
