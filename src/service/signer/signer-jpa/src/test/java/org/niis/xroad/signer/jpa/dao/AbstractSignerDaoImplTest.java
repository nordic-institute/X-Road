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
package org.niis.xroad.signer.jpa.dao;

import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.signer.jpa.dao.impl.SignerKeyCertDaoImpl;
import org.niis.xroad.signer.jpa.dao.impl.SignerKeyCertRequestDaoImpl;
import org.niis.xroad.signer.jpa.dao.impl.SignerKeyDaoImpl;
import org.niis.xroad.signer.jpa.dao.impl.SignerTokenDaoImpl;
import org.niis.xroad.signer.jpa.entity.SignerCertRequestEntity;
import org.niis.xroad.signer.jpa.entity.SignerCertificateEntity;
import org.niis.xroad.signer.jpa.entity.SignerKeyEntity;
import org.niis.xroad.signer.jpa.entity.SignerTokenEntity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AbstractSignerDaoImplTest {

    private final Session session = mock(Session.class);

    @Test
    void shouldRefuseToFindAllRowsWithoutANode() {
        var tokenDao = new SignerTokenDaoImpl();
        var keyDao = new SignerKeyDaoImpl();
        var certDao = new SignerKeyCertDaoImpl();
        var certRequestDao = new SignerKeyCertRequestDaoImpl();

        assertThatThrownBy(() -> tokenDao.findAll(session, SignerTokenEntity.class))
                .isInstanceOf(XrdRuntimeException.class);
        assertThatThrownBy(() -> keyDao.findAll(session, SignerKeyEntity.class))
                .isInstanceOf(XrdRuntimeException.class);
        assertThatThrownBy(() -> certDao.findAll(session, SignerCertificateEntity.class))
                .isInstanceOf(XrdRuntimeException.class);
        assertThatThrownBy(() -> certRequestDao.findAll(session, SignerCertRequestEntity.class))
                .isInstanceOf(XrdRuntimeException.class);

        verifyNoInteractions(session);
    }

    @Test
    void shouldRefuseToFindARowByIdWithoutANode() {
        var tokenDao = new SignerTokenDaoImpl();
        var keyDao = new SignerKeyDaoImpl();
        var certDao = new SignerKeyCertDaoImpl();
        var certRequestDao = new SignerKeyCertRequestDaoImpl();

        assertThatThrownBy(() -> tokenDao.findById(session, SignerTokenEntity.class, 1L))
                .isInstanceOf(XrdRuntimeException.class);
        assertThatThrownBy(() -> keyDao.findById(session, SignerKeyEntity.class, 1L))
                .isInstanceOf(XrdRuntimeException.class);
        assertThatThrownBy(() -> certDao.findById(session, SignerCertificateEntity.class, 1L))
                .isInstanceOf(XrdRuntimeException.class);
        assertThatThrownBy(() -> certRequestDao.findById(session, SignerCertRequestEntity.class, 1L))
                .isInstanceOf(XrdRuntimeException.class);

        verifyNoInteractions(session);
    }
}
