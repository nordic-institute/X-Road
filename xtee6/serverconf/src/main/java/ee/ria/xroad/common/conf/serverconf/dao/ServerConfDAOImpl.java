package ee.ria.xroad.common.conf.serverconf.dao;

import org.hibernate.Criteria;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.serverconf.model.ServerConfType;

import static ee.ria.xroad.common.ErrorCodes.X_MALFORMED_SERVERCONF;
import static ee.ria.xroad.common.conf.serverconf.ServerConfDatabaseCtx.doInTransaction;
import static ee.ria.xroad.common.conf.serverconf.ServerConfDatabaseCtx.get;

/**
 * Server conf data access object implementation.
 */
public class ServerConfDAOImpl {

    /**
     * Saves the server conf to the database.
     * @param conf the server conf
     * @throws Exception if an error occurs
     */
    public void save(final ServerConfType conf) throws Exception {
        doInTransaction(session -> {
            session.saveOrUpdate(conf);
            return null;
        });
    }

    /**
     * @return true, if configuration exists in the database
     * @throws Exception if an error occurs
     */
    public boolean confExists() throws Exception {
        return getFirst(ServerConfType.class) != null;
    }

    /**
     * @return the server conf
     */
    public ServerConfType getConf() {
        ServerConfType confType = getFirst(ServerConfType.class);
        if (confType == null) {
            throw new CodedException(X_MALFORMED_SERVERCONF,
                    "Server conf is not initialized!");
        }

        return confType;
    }

    @SuppressWarnings("unchecked")
    private <T> T getFirst(final Class<?> clazz) {
        Criteria c = get().getSession().createCriteria(clazz);
        c.setFirstResult(0);
        c.setMaxResults(1);
        T t = (T) c.uniqueResult();
        return t;
    }
}
