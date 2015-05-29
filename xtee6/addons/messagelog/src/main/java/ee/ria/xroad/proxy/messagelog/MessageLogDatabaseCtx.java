package ee.ria.xroad.proxy.messagelog;

import ee.ria.xroad.common.db.DatabaseCtx;
import ee.ria.xroad.common.db.TransactionCallback;

/**
 * Message log database context.
 */
public final class MessageLogDatabaseCtx {

    private static final DatabaseCtx CTX = new DatabaseCtx("messagelog");

    private MessageLogDatabaseCtx() {
    }

    /**
     * @return the current context
     */
    public static DatabaseCtx get() {
        return CTX;
    }

    /**
     * Convenience method for a transaction callback.
     * @param <T> the type of result
     * @param callback the callback
     * @return the result
     * @throws Exception if an error occurs
     */
    public static <T> T doInTransaction(TransactionCallback<T> callback)
            throws Exception {
        return CTX.doInTransaction(callback);
    }

}
