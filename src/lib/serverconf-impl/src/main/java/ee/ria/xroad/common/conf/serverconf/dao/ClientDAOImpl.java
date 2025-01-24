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

import ee.ria.xroad.common.conf.serverconf.model.CertificateType;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.EndpointType;
import ee.ria.xroad.common.conf.serverconf.model.LocalGroupType;
import ee.ria.xroad.common.identifier.ClientId;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.hibernate.Session;
import org.hibernate.query.Query;

import java.util.List;

import static java.util.Collections.emptyList;

/**
 * Client data access object implementation.
 */
public class ClientDAOImpl extends AbstractDAOImpl<ClientType> {

    /**
     * Returns true, if client with specified identifier exists.
     * @param session           the session
     * @param id                the identifier
     * @param includeSubsystems if true and identifier is not subsystem,
     *                          also looks for clients whose identifier is a subsystem
     * @return true, if client with specified identifier exists
     */
    public boolean clientExists(Session session, ClientId id, boolean includeSubsystems) {
        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<Boolean> query = cb.createQuery(Boolean.class);
        final Root<ClientType> client = query.from(ClientType.class);
        final Join<ClientType, ClientId> iden = client.join("identifier");
        Predicate pred = cb.conjunction();

        if (!includeSubsystems) {
            pred = cb.and(pred, cb.equal(iden.get("type"), id.getObjectType()));
        }

        pred = cb.and(pred,
                cb.equal(iden.get("xRoadInstance"), id.getXRoadInstance()),
                cb.equal(iden.get("memberClass"), id.getMemberClass()),
                cb.equal(iden.get("memberCode"), id.getMemberCode()));

        if (id.getSubsystemCode() != null) {
            pred = cb.and(pred, cb.equal(iden.get("subsystemCode"), id.getSubsystemCode()));
        }

        query.select(cb.literal(Boolean.TRUE)).where(pred);
        return !session.createQuery(query).list().isEmpty();

    }

    /**
     * Returns the client for the given client identifier.
     * @param session the session
     * @param id the client identifier
     * @return the client, or null if matching client was not found
     */
    public ClientType getClient(Session session, ClientId id) {
        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<ClientType> query = cb.createQuery(ClientType.class);
        final Root<ClientType> client = query.from(ClientType.class);
        final Join<ClientType, ClientId> iden = client.join("identifier");

        Predicate pred =
                cb.and(cb.equal(iden.get("type"), id.getObjectType()),
                        cb.equal(iden.get("xRoadInstance"), id.getXRoadInstance()),
                        cb.equal(iden.get("memberClass"), id.getMemberClass()),
                        cb.equal(iden.get("memberCode"), id.getMemberCode()));
        if (id.getSubsystemCode() != null) {
            pred = cb.and(pred, cb.equal(iden.get("subsystemCode"), id.getSubsystemCode()));
        }

        return session
                .createQuery(query.select(client).where(pred))
                .uniqueResult();
    }

    /**
     * Returns the information system certificates of the specified client.
     * @param session the session
     * @param id      the client identifier
     * @return the information system certificates of the specified client
     */
    public List<CertificateType> getIsCerts(Session session, ClientId id) {
        ClientType client = getClient(session, id);
        if (client != null) {
            return client.getIsCert();
        }
        return emptyList();
    }

    /**
     * Returns ClientType containing endpoint with id given as parameter
     *
     * @param session       the session
     * @param endpointType  endpointType entity
     * @return the client, or null if matching client was not found for the endpoint id
     */
    public ClientType getClientByEndpointId(Session session, EndpointType endpointType) {
        Query<ClientType> query = session.createQuery(
                "select c from ClientType as c where :endpoint member of c.endpoint",
                ClientType.class);
        query.setParameter("endpoint", endpointType);
        return findOne(query);
    }

    /**
     * Returns ClientType containing localGroupType given as parameter
     *
     * @param session       the session
     * @param localGroupType  localGroupType entity
     * @return the client, or null if matching client was not found for the localGroupType
     */
    public ClientType getClientByLocalGroup(Session session, LocalGroupType localGroupType) {
        Query<ClientType> query = session.createQuery(
                "select c from ClientType as c where :localGroup member of c.localGroup",
                ClientType.class);
        query.setParameter("localGroup", localGroupType);
        return findOne(query);
    }
}
