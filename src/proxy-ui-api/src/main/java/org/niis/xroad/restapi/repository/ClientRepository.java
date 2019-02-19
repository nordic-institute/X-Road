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

import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.globalconf.MemberInfo;
import ee.ria.xroad.common.conf.serverconf.dao.ClientDAOImpl;
import ee.ria.xroad.common.conf.serverconf.dao.ServerConfDAOImpl;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.identifier.ClientId;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.niis.xroad.restapi.DatabaseContextHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * client repository
 */
@Slf4j
@Component
public class ClientRepository {

    /**
     *
      * @return
     */
    public List<MemberInfo> getAllMembers() {
        return GlobalConf.getMembers();
    }

    /**
     * dummy
     * @param s
     */
    public void throwRuntimeException(String s) {
        log.error("throwing exception {}", s);
        throw new RuntimeException(s);
    }

    /**
     * dummy
     * @param s
     */
    public void throwApplicationException(String s) throws MyApplicationException {
        log.error("throwing exception {}", s);
        throw new MyApplicationException(s);
    }

    /**
     * dummy
     * @param s
     */
    public void throwSpringException(String s) {
        log.error("throwing exception {}", s);
        throw new RestClientException(s);
    }

    /**
     * dummy
     */
    public static class MyApplicationException extends Exception {
        public MyApplicationException(String s) {
            super(s);
        }
    }

    /**
     * transactions
     * test rollback
     * - correct id encoding (see rest proxy)
     * @param id
     */
    public ClientType getClient(ClientId id) {
        ClientDAOImpl clientDAO = new ClientDAOImpl();
        return DatabaseContextHelper.serverConfTransaction(
                session -> {
                    return clientDAO.getClient(session, id);
                });
    }

    /**
     * return all clients
     * @return
     */
    public List<ClientType> getAllClients() {
        ServerConfDAOImpl serverConf = new ServerConfDAOImpl();
        return DatabaseContextHelper.serverConfTransaction(
                session -> {
                    List<ClientType> clientTypes = serverConf.getConf().getClient();
                    Hibernate.initialize(clientTypes);
                    return clientTypes;
                });
    }
}

