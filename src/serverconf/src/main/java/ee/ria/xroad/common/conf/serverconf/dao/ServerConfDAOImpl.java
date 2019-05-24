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
package ee.ria.xroad.common.conf.serverconf.dao;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.serverconf.model.ServerConfType;

import org.hibernate.Session;

import javax.persistence.criteria.CriteriaQuery;

import static ee.ria.xroad.common.ErrorCodes.X_MALFORMED_SERVERCONF;
import static ee.ria.xroad.common.conf.serverconf.ServerConfDatabaseCtx.doInTransaction;
import static ee.ria.xroad.common.conf.serverconf.ServerConfDatabaseCtx.get;

/**
 * Server conf data access object implementation.
 */
public class ServerConfDAOImpl {

    /**
     * Saves the server conf to the database.
     * @param conf the server conf
     * @throws Exception if an error occurs
     */
    public void save(final ServerConfType conf) throws Exception {
        doInTransaction(session -> {
            session.saveOrUpdate(conf);
            return null;
        });
    }

    /**
     * @return true, if configuration exists in the database
     * @throws Exception if an error occurs
     */
    public boolean confExists() {
        return getFirst(ServerConfType.class) != null;
    }

    /**
     * @return the server conf
     */
    public ServerConfType getConf() {
        ServerConfType confType = getFirst(ServerConfType.class);
        if (confType == null) {
            throw new CodedException(X_MALFORMED_SERVERCONF, "Server conf is not initialized!");
        }

        return confType;
    }

    private <T> T getFirst(final Class<T> clazz) {
        final Session session = get().getSession();
        final CriteriaQuery<T> q = session.getCriteriaBuilder().createQuery(clazz);
        q.select(q.from(clazz));

        return session.createQuery(q)
                .setFirstResult(0)
                .setMaxResults(1)
                .uniqueResult();
    }
}
