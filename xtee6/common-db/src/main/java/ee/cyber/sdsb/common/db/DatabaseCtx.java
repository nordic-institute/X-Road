package ee.cyber.sdsb.common.db;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hibernate.HibernateException;
import org.hibernate.JDBCException;
import org.hibernate.Session;
import org.hibernate.Transaction;

import ee.cyber.sdsb.common.CodedException;

import static ee.cyber.sdsb.common.ErrorCodes.X_DATABASE_ERROR;
import static ee.cyber.sdsb.common.db.HibernateUtil.getSessionFactory;

@Slf4j
@RequiredArgsConstructor
public class DatabaseCtx {

    private final String sessionFactoryName;

    public <T> T doInTransaction(TransactionCallback<T> callback)
            throws Exception {
        Session session = null;
        try {
            session = beginTransaction();

            T result = callback.call(session);

            commitTransaction();
            return result;
        } catch (Exception e) {
            if (e instanceof HibernateException) {
                log.error("Error while executing in transaction", e);
            }

            try {
                rollbackTransaction();
            } catch (Exception logIt) {
                log.debug("Error rollbacking transaction", logIt);
            }

            try {
                if (session != null && session.isOpen()) {
                    session.close();
                }
            } catch (Exception logIt) {
                log.debug("Error closing session", logIt);
            }

            throw customizeException(e);
        }
    }

    public Session getSession() {
        return getSessionFactory(sessionFactoryName).getCurrentSession();
    }

    public Session beginTransaction() {
        log.trace("beginTransaction({})", sessionFactoryName);

        Session session = getSession();
        if (!session.getTransaction().isActive()) {
            session.beginTransaction();
        }

        return session;
    }

    public void commitTransaction() {
        log.trace("commitTransaction({})", sessionFactoryName);

        Transaction tx = getSession().getTransaction();
        if (tx.isActive() && !tx.wasCommitted()) {
            tx.commit();
        }
    }

    public void rollbackTransaction() {
        log.trace("rollbackTransaction({})", sessionFactoryName);

        Transaction tx = getSession().getTransaction();
        if (tx.isActive() && !tx.wasRolledBack()) {
            tx.rollback();
        }
    }

    public void closeSessionFactory() {
        HibernateUtil.closeSessionFactory(sessionFactoryName);
    }

    private Exception customizeException(Exception e) {
        if (e instanceof JDBCException) {
            return new CodedException(X_DATABASE_ERROR,
                    "Error accessing database (%s)", sessionFactoryName);
        }

        return e;
    }
}
