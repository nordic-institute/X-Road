package ee.cyber.sdsb.common.conf.serverconf.dao;

import java.util.List;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Example;

import ee.cyber.sdsb.common.conf.serverconf.model.CertificateType;
import ee.cyber.sdsb.common.conf.serverconf.model.ClientType;
import ee.cyber.sdsb.common.identifier.ClientId;

import static java.util.Collections.emptyList;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ClientDAOImpl extends AbstractDAOImpl<ClientType> {

    private static final ClientDAOImpl instance = new ClientDAOImpl();

    public static ClientDAOImpl getInstance() {
        return instance;
    }

    /**
     * Returns true, if client with specified identifier exists.
     * @param session the session
     * @param id the identifier
     * @param includeSubsystems if true and identifier is not subsystem,
     * also looks for clients whose identifier is a subsystem
     */
    public boolean clientExists(Session session, ClientId id,
            boolean includeSubsystems) throws Exception {
        Example ex = Example.create(id);

        if (includeSubsystems) {
            ex.excludeProperty("type");
            ex.excludeZeroes();
        }

        Criteria criteria = session.createCriteria(ClientType.class);
        criteria.createCriteria("identifier").add(ex);
        return criteria.list().size() > 0;
    }

    public ClientType getClient(Session session, ClientId id) throws Exception {
        Criteria criteria = session.createCriteria(ClientType.class);
        criteria.createCriteria("identifier").add(Example.create(id));
        return findOne(criteria);
    }

    public List<CertificateType> getIsCerts(Session session, ClientId id)
            throws Exception {
        Criteria criteria = session.createCriteria(ClientType.class);
        criteria.createCriteria("identifier").add(Example.create(id));

        ClientType client = findOne(criteria);
        if (client != null) {
            return client.getIsCert();
        }

        // TODO: or throw exception?
        return emptyList();
    }
}
