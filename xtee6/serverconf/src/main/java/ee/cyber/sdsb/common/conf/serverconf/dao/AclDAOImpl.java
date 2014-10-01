package ee.cyber.sdsb.common.conf.serverconf.dao;

import java.util.List;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Example;

import ee.cyber.sdsb.common.conf.serverconf.model.AclType;
import ee.cyber.sdsb.common.conf.serverconf.model.ClientType;
import ee.cyber.sdsb.common.identifier.ClientId;

import static java.util.Collections.emptyList;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AclDAOImpl extends AbstractDAOImpl<AclType> {

    private static final AclDAOImpl instance = new AclDAOImpl();

    public static AclDAOImpl getInstance() {
        return instance;
    }

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
