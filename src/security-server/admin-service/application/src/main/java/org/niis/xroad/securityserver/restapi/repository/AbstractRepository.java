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
package org.niis.xroad.securityserver.restapi.repository;

import org.niis.xroad.restapi.util.PersistenceUtils;

import java.util.function.Function;

public abstract class AbstractRepository<E> {
    abstract PersistenceUtils getPersistenceUtils();

    public E merge(E entity) {
        return merge(entity, true);
    }

    public E merge(E entity, boolean flush) {
        return handleSave(entity, this::handleMerge, flush);
    }

    /**
     * Executes a Hibernate merge(client)
     * @param entity
     */
    public E persist(E entity) {
        return persist(entity, true);
    }

    public E persist(E entity, boolean flush) {
        return handleSave(entity, this::handlePersist, flush);
    }

    private E handlePersist(E entity) {
        getPersistenceUtils().getCurrentSession().persist(entity);
        return entity;
    }

    private E handleMerge(E entity) {
        return getPersistenceUtils().getCurrentSession().merge(entity);
    }

    private E handleSave(E entity, Function<E, E> handler, boolean flush) {
        E saved = handler.apply(entity);
        if (flush) {
            getPersistenceUtils().flush();
        }
        return saved;
    }
}
