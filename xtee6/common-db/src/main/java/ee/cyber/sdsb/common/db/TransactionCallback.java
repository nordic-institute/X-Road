package ee.cyber.sdsb.common.db;


import java.util.function.Function;

import org.hibernate.Session;

/**
 * Callback that can be called during a transaction.
 * @param <R> the type of the callback result
 */
public interface TransactionCallback<R> extends Function<Session, R> {
}
