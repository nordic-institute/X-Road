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
import org.niis.xroad.signer.jpa.entity.SignerCertificateEntity;

import java.time.Instant;

@ApplicationScoped
public class SignerKeyCertDaoImpl extends AbstractDAOImpl<SignerCertificateEntity> {

    public boolean setActive(Session session, Long id, boolean active) {
        var query = session.createMutationQuery("UPDATE SignerCertificateEntity SET active = :active WHERE id = :id");
        query.setParameter("active", active);
        query.setParameter("id", id);
        return query.executeUpdate() > 0;
    }

    public boolean updateStatus(Session session, Long id, String status) {
        var query = session.createMutationQuery("UPDATE SignerCertificateEntity SET status = :status WHERE id = :id");
        query.setParameter("status", status);
        query.setParameter("id", id);
        return query.executeUpdate() > 0;
    }

    public boolean updateRenewedCertHash(Session session, Long id, String renewedCertHash) {
        var query = session.createMutationQuery("UPDATE SignerCertificateEntity SET renewedCertHash = :renewedCertHash WHERE id = :id");
        query.setParameter("renewedCertHash", renewedCertHash);
        query.setParameter("id", id);
        return query.executeUpdate() > 0;
    }

    public boolean updateRenewalError(Session session, Long id, String renewalError) {
        var query = session.createMutationQuery("UPDATE SignerCertificateEntity SET renewalError = :renewalError WHERE id = :id");
        query.setParameter("renewalError", renewalError);
        query.setParameter("id", id);
        return query.executeUpdate() > 0;
    }

    public boolean updateNextAutomaticRenewalTime(Session session, Long id, Instant nextRenewalTime) {
        var query = session.createMutationQuery("UPDATE SignerCertificateEntity SET nextRenewalTime = :nextRenewalTime WHERE id = :id");
        query.setParameter("nextRenewalTime", nextRenewalTime);
        query.setParameter("id", id);
        return query.executeUpdate() > 0;
    }

    public boolean updateOcspVerifyBeforeActivationError(Session session, Long id, String ocspVerifyError) {
        var query = session.createMutationQuery("UPDATE SignerCertificateEntity SET ocspVerifyError = :ocspVerifyError WHERE id = :id");
        query.setParameter("ocspVerifyError", ocspVerifyError);
        query.setParameter("id", id);
        return query.executeUpdate() > 0;
    }
}
