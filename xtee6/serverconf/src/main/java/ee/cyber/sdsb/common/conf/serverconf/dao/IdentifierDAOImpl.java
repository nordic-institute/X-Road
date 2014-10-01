package ee.cyber.sdsb.common.conf.serverconf.dao;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import org.hibernate.Criteria;
import org.hibernate.criterion.Example;

import ee.cyber.sdsb.common.identifier.SdsbId;

import static ee.cyber.sdsb.common.conf.serverconf.ServerConfDatabaseCtx.get;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class IdentifierDAOImpl extends AbstractDAOImpl<SdsbId> {

    private static final IdentifierDAOImpl instance = new IdentifierDAOImpl();

    public static IdentifierDAOImpl getInstance() {
        return instance;
    }

    @SuppressWarnings("unchecked")
    public static <T extends SdsbId> T getIdentifier(T example)
            throws Exception {
        Criteria criteria =
                get().getSession().createCriteria(example.getClass());
        criteria.add(Example.create(example));
        return (T) criteria.uniqueResult();
    }
}
