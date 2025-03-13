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

import ee.ria.xroad.common.identifier.ClientId;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.niis.xroad.restapi.util.PersistenceUtils;
import org.niis.xroad.securityserver.restapi.service.ClientNotFoundException;
import org.niis.xroad.securityserver.restapi.service.EndpointNotFoundException;
import org.niis.xroad.serverconf.entity.ClientTypeEntity;
import org.niis.xroad.serverconf.entity.EndpointTypeEntity;
import org.niis.xroad.serverconf.entity.LocalGroupTypeEntity;
import org.niis.xroad.serverconf.entity.ServerConfTypeEntity;
import org.niis.xroad.serverconf.impl.dao.ClientDAOImpl;
import org.niis.xroad.serverconf.impl.dao.ServerConfDAOImpl;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * client repository
 */
@Slf4j
@Repository
@Transactional
@RequiredArgsConstructor
public class ClientRepository extends AbstractRepository<ClientTypeEntity> {

    @Getter(AccessLevel.PROTECTED)
    private final PersistenceUtils persistenceUtils;

    /**
     * return one local client
     * @param id client id
     * @return the client, or null if matching client was not found
     */
    public ClientTypeEntity getClient(ClientId id) {
        ClientDAOImpl clientDAO = new ClientDAOImpl();
        return clientDAO.getClient(persistenceUtils.getCurrentSession(), id);
    }

    /**
     * return all local clients
     * @return List<ClientTypeEntity>
     */
    public List<ClientTypeEntity> getAllLocalClients() {
        ServerConfDAOImpl serverConfDao = new ServerConfDAOImpl();
        ServerConfTypeEntity serverConfType = serverConfDao.getConf(persistenceUtils.getCurrentSession());
        List<ClientTypeEntity> clientTypes = serverConfType.getClient();
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

    /**
     * Return ClientTypeEntity containing the id matching endpoint
     *
     * @param id                                         id for endpoint
     * @return ClientType                                client containing id matching endpoint
     * @throws EndpointNotFoundException if endpoint is not found with given id
     * @throws ClientNotFoundException if client is not found with given endpoint id
     */
    public ClientTypeEntity getClientByEndpointId(Long id)
            throws EndpointNotFoundException, ClientNotFoundException {
        Session session = this.persistenceUtils.getCurrentSession();
        EndpointTypeEntity endpointType = session.get(EndpointTypeEntity.class, id);

        if (endpointType == null) {
            throw new EndpointNotFoundException(id.toString());
        }

        ClientDAOImpl clientDAO = new ClientDAOImpl();
        ClientTypeEntity clientType = clientDAO.getClientByEndpoint(session, endpointType);

        session.refresh(clientType);

        if (clientType == null) {
            throw new ClientNotFoundException("Client not found for endpoint with id: " + id.toString());
        }

        return clientType;
    }

    /**
     * Return ClientTypeEntity containing the id matching local group
     *
     * @throws ClientNotFoundException if client is not found with given endpoint id
     */
    public ClientTypeEntity getClientByLocalGroup(LocalGroupTypeEntity localGroupType)
            throws ClientNotFoundException {
        ClientDAOImpl clientDAO = new ClientDAOImpl();
        ClientTypeEntity clientType = clientDAO.getClientByLocalGroup(persistenceUtils.getCurrentSession(), localGroupType);
        if (clientType == null) {
            throw new ClientNotFoundException("Client not found for localGroup with id: " + localGroupType.getId());
        }
        return clientType;
    }

}

