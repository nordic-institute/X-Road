/*
 *  The MIT License
 *  Copyright (c) 2018 Estonian Information System Authority (RIA),
 *  Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 *  Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package org.niis.xroad.securityserver.restapi.repository;

import ee.ria.xroad.common.conf.serverconf.dao.IdentifierDAOImpl;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.XRoadId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.niis.xroad.restapi.util.PersistenceUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

/**
 * identifier repository
 */
@Slf4j
@Repository
@Transactional
@RequiredArgsConstructor
public class IdentifierRepository {

    private final PersistenceUtils persistenceUtils;

    /**
     * Executes a Hibernate saveOrUpdate(identifier)
     * @param identifier
     */
    public void saveOrUpdate(XRoadId.Conf identifier) {
        saveOrUpdate(identifier, false);
    }

    /**
     * Executes a Hibernate saveOrUpdate(identifier) and flushes whole entityManager
     * @param identifier
     */
    public void saveOrUpdateAndFlush(XRoadId.Conf identifier) {
        saveOrUpdate(identifier, true);
    }

    /**
     * Executes a Hibernate saveOrUpdate(identifier) and flushes whole entityManager
     * @param identifier
     * @param flush
     */
    public void saveOrUpdate(XRoadId.Conf identifier, boolean flush) {
        persistenceUtils.getCurrentSession().saveOrUpdate(identifier);
        if (flush) {
            persistenceUtils.flush();
        }
    }

    /**
     * Executes a Hibernate persist(XRoadId) for multiple group members
     * @param identifiers
     */
    public void saveOrUpdate(Collection<XRoadId.Conf> identifiers) {
        Session session = persistenceUtils.getCurrentSession();
        for (XRoadId.Conf identifier : identifiers) {
            session.saveOrUpdate(identifier);
        }
    }

    /**
     * return all identifiers
     */
    public Collection<XRoadId.Conf> getIdentifiers() {
        IdentifierDAOImpl identifierDao = new IdentifierDAOImpl();
        return identifierDao.findAll(persistenceUtils.getCurrentSession(), XRoadId.Conf.class);
    }

    /**
     * Finds a (local) client identifier corresponding the example or null if none exits
     */
    public ClientId.Conf getClientId(ClientId clientId) {
        Session session = persistenceUtils.getCurrentSession();
        IdentifierDAOImpl identifierDao = new IdentifierDAOImpl();
        ClientId.Conf localClientId = identifierDao.findClientId(session, clientId);
        return localClientId;
    }

}

