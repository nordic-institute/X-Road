package ee.ria.xroad.common.conf.serverconf.dao;

import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;

import ee.ria.xroad.common.conf.serverconf.ServerConfDatabaseCtx;
import ee.ria.xroad.common.conf.serverconf.model.UiUserType;

/**
 * UiUser data access object implementation.
 */
public class UiUserDAOImpl extends AbstractDAOImpl<UiUserType> {

    /**
     * Returns the UiUser object for the given user name or null.
     * @param username the user name
     * @return the UiUser object for the given user name or null
     * @throws Exception if an error occurs
     */
    public static UiUserType getUiUser(String username) throws Exception {
        Criteria criteria =
                ServerConfDatabaseCtx.getSession().createCriteria(
                        UiUserType.class);
        criteria.add(Restrictions.eq("username", username));
        return (UiUserType) criteria.uniqueResult();
    }
}
