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
package org.niis.xroad.restapi.repository;

import ee.ria.xroad.common.conf.serverconf.dao.ClientDAOImpl;
import ee.ria.xroad.common.conf.serverconf.dao.ServerConfDAOImpl;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.identifier.ClientId;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.niis.xroad.restapi.util.PersistenceUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * client repository
 */
@Slf4j
@Repository
@Transactional
public class ClientRepository {

    private final PersistenceUtils persistenceUtils;

    @Autowired
    public ClientRepository(PersistenceUtils persistenceUtils) {
        this.persistenceUtils = persistenceUtils;
    }

    /**
     * Executes a Hibernate saveOrUpdate(client)
     * @param clientType
     */
    public void saveOrUpdate(ClientType clientType) {
        saveOrUpdate(clientType, false);
    }

    /**
     * Executes a Hibernate saveOrUpdate(client) and flushes whole entityManager
     * @param clientType
     */
    public void saveOrUpdateAndFlush(ClientType clientType) {
        saveOrUpdate(clientType, true);
    }

    /**
     * Executes a Hibernate saveOrUpdate(client) and flushes whole entityManager
     * @param clientType
     */
    public void saveOrUpdate(ClientType clientType, boolean flush) {
        persistenceUtils.getCurrentSession().saveOrUpdate(clientType);
        if (flush) {
            persistenceUtils.flush();
        }
    }

    /**
     * return one client
     * @param id
     * @return the client, or null if matching client was not found
     */
    public ClientType getClient(ClientId id) {
        ClientDAOImpl clientDAO = new ClientDAOImpl();
        return clientDAO.getClient(persistenceUtils.getCurrentSession(), id);
    }

    /**
     * return all clients
     * @return
     */
    public List<ClientType> getAllLocalClients() {
        ServerConfDAOImpl serverConf = new ServerConfDAOImpl();
        List<ClientType> clientTypes = serverConf.getConf(persistenceUtils.getCurrentSession()).getClient();
        Hibernate.initialize(clientTypes);
        return clientTypes;
    }

    /**
     * Returns true, if client with specified identifier exists.
     * @param id the identifier
     * @param includeSubsystems if true and identifier is not subsystem,
     * also looks for clients whose identifier is a subsystem
     * @return true, if client with specified identifier exists
     */
    public boolean clientExists(ClientId id, boolean includeSubsystems) {
        ClientDAOImpl clientDAO = new ClientDAOImpl();
        return clientDAO.clientExists(persistenceUtils.getCurrentSession(), id, includeSubsystems);
    }
}

