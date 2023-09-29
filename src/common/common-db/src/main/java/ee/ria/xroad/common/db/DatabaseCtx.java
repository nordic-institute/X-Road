/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.common.db;

import ee.ria.xroad.common.CodedException;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.HibernateException;
import org.hibernate.Interceptor;
import org.hibernate.JDBCException;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.resource.transaction.spi.TransactionStatus;

import static ee.ria.xroad.common.ErrorCodes.X_DATABASE_ERROR;
import static ee.ria.xroad.common.db.HibernateUtil.getSessionFactory;

/**
 * Database context manages database connections for a specific session
 * factory.
 */
@Slf4j
@RequiredArgsConstructor
@AllArgsConstructor
public class DatabaseCtx {

    private final String sessionFactoryName;

    private Interceptor interceptor = null;

    /**
     * Gets called within a transactional context. Begins a transaction,
     * calls the callback and then commits the transaction or rollbacks the
     * transaction depending whether the callback finished successfully or
     * threw an exception.
     * @param <T> the type of result
     * @param callback the callback to call
     * @return the result from the callback
     * @throws Exception if an exception occurred
     */
    public <T> T doInTransaction(TransactionCallback<T> callback)
            throws Exception {
        Session session = null;
        try {
            boolean newTransaction = false;

            session = getSession();
            if (session.getTransaction().getStatus() == TransactionStatus.NOT_ACTIVE) {
                session.beginTransaction();
                newTransaction = true;
            }

            T result = callback.apply(session);

            if (newTransaction) {
                commitTransaction();
            }
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

    /**
     * @return the current session
     */
    public Session getSession() {
        return getSessionFactory(sessionFactoryName, interceptor)
                .getCurrentSession();
    }

    /**
     * Starts a new transaction.
     * @return the current session
     */
    public Session beginTransaction() {
        log.trace("beginTransaction({})", sessionFactoryName);

        Session session = getSession();
        if (session.getTransaction().getStatus() == TransactionStatus.NOT_ACTIVE) {
            session.beginTransaction();
        }

        return session;
    }

    /**
     * Commits the transaction.
     */
    public void commitTransaction() {
        log.trace("commitTransaction({})", sessionFactoryName);

        Transaction tx = getSession().getTransaction();
        if (tx.getStatus() == TransactionStatus.ACTIVE) {
            tx.commit();
        }
    }

    /**
     * Rollbacks the transaction.
     */
    public void rollbackTransaction() {
        log.trace("rollbackTransaction({})", sessionFactoryName);

        Transaction tx = getSession().getTransaction();
        if (tx.getStatus().canRollback()) {
            tx.rollback();
        }
    }

    /**
     * Closes the session factory.
     */
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
