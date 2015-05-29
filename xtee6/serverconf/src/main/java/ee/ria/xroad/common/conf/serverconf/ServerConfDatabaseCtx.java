package ee.ria.xroad.common.conf.serverconf;

import org.hibernate.Session;

import ee.ria.xroad.common.db.DatabaseCtx;
import ee.ria.xroad.common.db.TransactionCallback;

/**
 * Server conf database context.
 */
public final class ServerConfDatabaseCtx {

    private static final DatabaseCtx CTX = new DatabaseCtx("serverconf");

    private ServerConfDatabaseCtx() {
    }

    /**
     * @return the database context instance
     */
    public static DatabaseCtx get() {
        return CTX;
    }

    /**
     * @return shortcut for a session
     */
    public static Session getSession() {
        return get().getSession();
    }

    /**
     * Executes the unit of work transactionally.
     * @param callback the unit of work callback
     * @param <T> the type of the result
     * @return the result of the callback
     * @throws Exception if an error occurs
     */
    public static <T> T doInTransaction(TransactionCallback<T> callback)
            throws Exception {
        return CTX.doInTransaction(callback);
    }

}
