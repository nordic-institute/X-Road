package ee.ria.xroad.common.conf.serverconf.dao;

import org.hibernate.Criteria;
import org.hibernate.criterion.Example;

import ee.ria.xroad.common.identifier.XRoadId;

import static ee.ria.xroad.common.conf.serverconf.ServerConfDatabaseCtx.get;

/**
 * Identifier data access object implementation.
 */
public class IdentifierDAOImpl extends AbstractDAOImpl<XRoadId> {

    /**
     * Returns the identifier.
     * @param example the example type
     * @param <T> the type of the example
     * @return the identifier
     * @throws Exception if an error occurs
     */
    @SuppressWarnings("unchecked")
    public static <T extends XRoadId> T getIdentifier(T example)
            throws Exception {
        Criteria criteria =
                get().getSession().createCriteria(example.getClass());
        criteria.add(Example.create(example));
        return (T) criteria.uniqueResult();
    }
}
