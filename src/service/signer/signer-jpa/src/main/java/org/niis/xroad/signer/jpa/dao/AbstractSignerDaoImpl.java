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
package org.niis.xroad.signer.jpa.dao;

import jakarta.annotation.Nonnull;
import jakarta.persistence.criteria.CriteriaQuery;
import org.hibernate.Session;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.jpa.dao.AbstractDAOImpl;

import java.util.List;
import java.util.Optional;

/**
 * Base for signer DAOs whose entities carry a {@code haNodeName} attribute.
 */
public abstract class AbstractSignerDaoImpl<T> extends AbstractDAOImpl<T> {

    private static final String NODE_NAME_ATTRIBUTE = "haNodeName";

    public List<T> findAllByNode(@Nonnull Session session, @Nonnull Class<T> clazz, @Nonnull String nodeName) {
        var criteriaBuilder = session.getCriteriaBuilder();
        CriteriaQuery<T> query = criteriaBuilder.createQuery(clazz);
        var root = query.from(clazz);
        query.select(root).where(criteriaBuilder.equal(root.get(NODE_NAME_ATTRIBUTE), nodeName));
        return session.createQuery(query).getResultList();
    }

    @Override
    public Optional<T> findById(Session session, Class<T> clazz, Long id) {
        throw unscopedFinder();
    }

    @Override
    public List<T> findAll(Session session, Class<T> clazz) {
        throw unscopedFinder();
    }

    private static XrdRuntimeException unscopedFinder() {
        return XrdRuntimeException.systemInternalError(
                "Signer rows are scoped by node: finders that ignore the node are not allowed, use findAllByNode");
    }
}
