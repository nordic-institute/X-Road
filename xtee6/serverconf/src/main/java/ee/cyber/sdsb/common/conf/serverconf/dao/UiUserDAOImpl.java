package ee.cyber.sdsb.common.conf.serverconf.dao;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;

import ee.cyber.sdsb.common.conf.serverconf.ServerConfDatabaseCtx;
import ee.cyber.sdsb.common.conf.serverconf.model.UiUserType;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UiUserDAOImpl extends AbstractDAOImpl<UiUserType> {

    private static final UiUserDAOImpl instance = new UiUserDAOImpl();

    public static UiUserDAOImpl getInstance() {
        return instance;
    }

    public UiUserType getUiUser(String username) throws Exception {
        Criteria criteria =
                ServerConfDatabaseCtx.getSession().createCriteria(
                        UiUserType.class);
        criteria.add(Restrictions.eq("username", username));
        return findOne(criteria);
    }
}
