package ee.cyber.sdsb.common.db;


import org.hibernate.Session;

public interface TransactionCallback<T> {
    T call(Session session) throws Exception;
}
