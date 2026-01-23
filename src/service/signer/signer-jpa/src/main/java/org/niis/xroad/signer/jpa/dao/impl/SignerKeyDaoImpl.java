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
package org.niis.xroad.signer.jpa.dao.impl;

import jakarta.enterprise.context.ApplicationScoped;
import org.hibernate.Session;
import org.niis.xroad.common.jpa.dao.AbstractDAOImpl;
import org.niis.xroad.signer.jpa.entity.SignerKeyEntity;
import org.niis.xroad.signer.jpa.entity.type.KeyUsage;

@ApplicationScoped
public class SignerKeyDaoImpl extends AbstractDAOImpl<SignerKeyEntity> {

    public boolean updateKeystore(Session session, Long id, byte[] keystore) {
        var query = session.createMutationQuery("UPDATE SignerKeyEntity SET keyStore = :keystore WHERE id = :id");
        query.setParameter("keystore", keystore);
        query.setParameter("id", id);
        return query.executeUpdate() > 0;
    }

    public boolean updateFriendlyName(Session session, Long id, String friendlyName) {
        var query = session.createMutationQuery("UPDATE SignerKeyEntity SET friendlyName = :friendlyName WHERE id = :id");
        query.setParameter("friendlyName", friendlyName);
        query.setParameter("id", id);
        return query.executeUpdate() > 0;
    }

    public boolean updateLabel(Session session, Long id, String label) {
        var query = session.createMutationQuery("UPDATE SignerKeyEntity SET label = :label WHERE id = :id");
        query.setParameter("label", label);
        query.setParameter("id", id);
        return query.executeUpdate() > 0;
    }

    public boolean updateKeyUsage(Session session, Long id, KeyUsage keyUsage) {
        var query = session.createMutationQuery("UPDATE SignerKeyEntity SET usage = :usage WHERE id = :id");
        query.setParameter("usage", keyUsage);
        query.setParameter("id", id);
        return query.executeUpdate() > 0;
    }

    public boolean updatePublicKey(Session session, Long id, String publicKey) {
        var query = session.createMutationQuery("UPDATE SignerKeyEntity SET publicKey = :publicKey WHERE id = :id");
        query.setParameter("publicKey", publicKey);
        query.setParameter("id", id);
        return query.executeUpdate() > 0;
    }
}
