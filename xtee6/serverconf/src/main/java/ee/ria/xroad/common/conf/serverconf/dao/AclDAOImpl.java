package ee.ria.xroad.common.conf.serverconf.dao;

import java.util.List;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Example;

import ee.ria.xroad.common.conf.serverconf.model.AclType;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.identifier.ClientId;

import static java.util.Collections.emptyList;

/**
 * ACL data access object implementation.
 */
public class AclDAOImpl extends AbstractDAOImpl<AclType> {

    /**
     * Returns the ACL objects for the given client identifier.
     * @param session the session
     * @param id the client identifier.
     * @return ACL objects
     * @throws Exception if an error occurs
     */
    public List<AclType> getAcl(Session session, ClientId id)
            throws Exception {
        Criteria criteria = session.createCriteria(ClientType.class);
        criteria.createCriteria("identifier").add(Example.create(id));

        ClientType clientType = (ClientType) criteria.uniqueResult();
        if (clientType != null) {
            return clientType.getAcl();
        }

        return emptyList();
    }
}
