package ee.cyber.sdsb.common.conf.serverconf.dao;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import org.hibernate.Criteria;
import org.hibernate.Session;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.conf.serverconf.model.ServerConfType;
import ee.cyber.sdsb.common.db.TransactionCallback;

import static ee.cyber.sdsb.common.ErrorCodes.X_MALFORMED_SERVERCONF;
import static ee.cyber.sdsb.common.conf.serverconf.ServerConfDatabaseCtx.doInTransaction;
import static ee.cyber.sdsb.common.conf.serverconf.ServerConfDatabaseCtx.get;


@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ServerConfDAOImpl {

    private static final ServerConfDAOImpl instance = new ServerConfDAOImpl();

    public static ServerConfDAOImpl getInstance() {
        return instance;
    }

    public void save(final ServerConfType conf) throws Exception {
        doInTransaction(new TransactionCallback<ServerConfType>() {
            @Override
            public ServerConfType call(Session session) throws Exception {
                session.saveOrUpdate(conf);
                return null;
            }
        });
    }

    public boolean confExists() throws Exception {
        return getFirst(ServerConfType.class) != null;
    }

    public ServerConfType getConf() throws Exception {
        ServerConfType confType = getFirst(ServerConfType.class);
        if (confType == null) {
            throw new CodedException(X_MALFORMED_SERVERCONF,
                    "Server conf is not initialized!");
        }

        return confType;
    }

    @SuppressWarnings("unchecked")
    private <T> T getFirst(final Class<?> clazz) throws Exception {
        Criteria c = get().getSession().createCriteria(clazz);
        c.setFirstResult(0);
        c.setMaxResults(1);
        T t = (T) c.uniqueResult();
        return t;
    }
}
