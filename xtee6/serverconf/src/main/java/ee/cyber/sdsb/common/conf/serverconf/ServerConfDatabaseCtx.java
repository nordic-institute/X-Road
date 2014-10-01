package ee.cyber.sdsb.common.conf.serverconf;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import org.hibernate.Session;

import ee.cyber.sdsb.common.db.DatabaseCtx;
import ee.cyber.sdsb.common.db.TransactionCallback;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ServerConfDatabaseCtx {

    private static final DatabaseCtx ctx = new DatabaseCtx("serverconf");

    public static DatabaseCtx get() {
        return ctx;
    }

    public static Session getSession() {
        return get().getSession();
    }

    public static <T> T doInTransaction(TransactionCallback<T> callback)
            throws Exception {
        return ctx.doInTransaction(callback);
    }

}
