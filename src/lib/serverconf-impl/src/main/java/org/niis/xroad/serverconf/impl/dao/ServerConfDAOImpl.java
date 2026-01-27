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
package org.niis.xroad.serverconf.impl.dao;

import jakarta.persistence.criteria.CriteriaQuery;
import org.hibernate.Session;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.serverconf.impl.entity.ServerConfEntity;

import static org.niis.xroad.common.core.exception.ErrorCode.MALFORMED_SERVERCONF;

/**
 * Server conf data access object implementation.
 */
public class ServerConfDAOImpl {

    /**
     * @return the server conf
     */
    public ServerConfEntity getConf(Session session) {
        ServerConfEntity confType = getFirst(session, ServerConfEntity.class);
        if (confType == null) {
            throw XrdRuntimeException.systemException(MALFORMED_SERVERCONF,
                    "Server conf is not initialized!");
        }

        return confType;
    }

    private <T> T getFirst(Session session, final Class<T> clazz) {
        final CriteriaQuery<T> q = session.getCriteriaBuilder().createQuery(clazz);
        q.select(q.from(clazz));

        return session.createQuery(q)
                .setFirstResult(0)
                .setMaxResults(1)
                .uniqueResult();
    }

}
