package ee.ria.xroad.common.conf.serverconf.dao;

import java.util.List;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Example;

import ee.ria.xroad.common.conf.serverconf.model.CertificateType;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.identifier.ClientId;

import static java.util.Collections.emptyList;

/**
 * Client data access object implementation.
 */
public class ClientDAOImpl extends AbstractDAOImpl<ClientType> {

    /**
     * Returns true, if client with specified identifier exists.
     * @param session the session
     * @param id the identifier
     * @param includeSubsystems if true and identifier is not subsystem,
     * also looks for clients whose identifier is a subsystem
     * @return true, if client with specified identifier exists
     */
    public boolean clientExists(Session session, ClientId id,
            boolean includeSubsystems) {
        Example ex = Example.create(id);

        if (includeSubsystems) {
            ex.excludeProperty("type").excludeZeroes();
        }

        Criteria criteria = session.createCriteria(ClientType.class);
        criteria.createCriteria("identifier").add(ex);
        return criteria.list().size() > 0;
    }

    /**
     * Returns the client for the given client identifier.
     * @param session the session
     * @param id the client identifier
     * @return the client
     */
    public ClientType getClient(Session session, ClientId id) {
        Criteria criteria = session.createCriteria(ClientType.class);
        criteria.createCriteria("identifier").add(Example.create(id));
        return findOne(criteria);
    }

    /**
     * Returns the information system certificates of the specified client.
     * @param session the session
     * @param id the client identifier
     * @return the information system certificates of the specified client
     */
    public List<CertificateType> getIsCerts(Session session, ClientId id) {
        Criteria criteria = session.createCriteria(ClientType.class);
        criteria.createCriteria("identifier").add(Example.create(id));

        ClientType client = findOne(criteria);
        if (client != null) {
            return client.getIsCert();
        }

        return emptyList();
    }
}
