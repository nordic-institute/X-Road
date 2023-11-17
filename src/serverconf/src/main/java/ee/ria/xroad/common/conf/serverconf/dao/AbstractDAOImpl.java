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
package ee.ria.xroad.common.conf.serverconf.dao;

import jakarta.persistence.criteria.CriteriaQuery;
import org.hibernate.Session;
import org.hibernate.query.Query;

import java.util.List;

class AbstractDAOImpl<T> {

    public List<T> findMany(Query<T> query) {
        return query.list();
    }

    public T findOne(Query<T> query) {
        return query.uniqueResult();
    }

    public T findById(Session session, Class<T> clazz, Long id) {
        return session.get(clazz, id);
    }

    public List<T> findAll(Session session, Class<T> clazz) {
        final CriteriaQuery<T> q = session.getCriteriaBuilder().createQuery(clazz);
        q.select(q.from(clazz));
        return session.createQuery(q).getResultList();
    }

    static String nullOrName(Object obj, String name) {
        if (obj == null) {
            return "is null";
        } else {
            return " = :" + name;
        }
    }

    static <T> void setString(Query<T> q, String name, String value) {
        if (value != null) {
            q.setParameter(name, value);
        }
    }
}
