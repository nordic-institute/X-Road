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
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.conf.serverconf.IsAuthentication;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.identifier.ClientId;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.exceptions.NotFoundException;
import org.niis.xroad.restapi.repository.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * client service
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("denyAll")
public class ClientService {

    @Autowired
    private ClientRepository clientRepository;

    /**
     * return all clients
     * @return
     */
    @PreAuthorize("hasAuthority('VIEW_CLIENTS')")
    public List<ClientType> getAllClients() {
        return clientRepository.getAllClients();
    }

    /**
     * return one client
     * @param id
     */
    @PreAuthorize("hasAuthority('VIEW_CLIENT_DETAILS')")
    public ClientType getClient(ClientId id) {
        return clientRepository.getClient(id);
    }

    /**
     * Update connection type of an existing client
     * @param id
     * @param connectionType
     * @return
     * @throws org.niis.xroad.restapi.exceptions.NotFoundException if
     * client was not found
     * @throws IllegalArgumentException if connectionType was not supported value
     */
    @PreAuthorize("hasAuthority('EDIT_CLIENT_INTERNAL_CONNECTION_TYPE')")
    public ClientType updateConnectionType(ClientId id, String connectionType) {
        ClientType clientType = clientRepository.getClient(id);
        if (clientType == null) {
            throw new NotFoundException(("client with id " + id + " not found"));
        }
        // validate connectionType param by creating enum out of it
        IsAuthentication enumValue = IsAuthentication.valueOf(connectionType);
        clientType.setIsAuthentication(connectionType);
        clientRepository.saveOrUpdate(clientType);
        return clientType;
    }

}
