package org.niis.xroad.signer.core.tokenmanager.token;

import ee.ria.xroad.common.CodedException;

import iaik.pkcs.pkcs11.Session;
import iaik.pkcs.pkcs11.Token;
import iaik.pkcs.pkcs11.wrapper.PKCS11Exception;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.core.passwordstore.PasswordStore;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static iaik.pkcs.pkcs11.Token.SessionType.SERIAL_SESSION;

@Slf4j
class PooledSession {
    private final Token token;
    private final String tokenId;
    private final BlockingQueue<Session> sessionPool;
    private final int poolSize;
    private final long sessionAcquireTimeoutMillis;
    //    private final boolean loginPerSession;
    private volatile boolean initialized = false;

    /**
     * Creates a new session pool manager.
     *
     * @param token                       The PKCS#11 token
     * @param tokenId                     The token ID
     * @param poolSize                    The size of the session pool
     * @param sessionAcquireTimeoutMillis Timeout for acquiring a session from the pool
     */
    PooledSession(Token token, String tokenId,
                  int poolSize, long sessionAcquireTimeoutMillis) {
        this.token = token;
        this.tokenId = tokenId;
        this.poolSize = poolSize;
        this.sessionAcquireTimeoutMillis = sessionAcquireTimeoutMillis;
        this.sessionPool = new ArrayBlockingQueue<>(poolSize);
    }

    /**
     * Initializes the session pool.
     * Creates sessions and adds them to the pool.
     *
     * @throws Exception if initialization fails
     */
    public synchronized void initialize() throws Exception {
        if (initialized) {
            return;
        }

        log.debug("Initializing session pool with size {} for token {}", poolSize, tokenId);

        if (token == null) {
            throw new CodedException(X_INTERNAL_ERROR, "Token is null");
        }

        for (int i = 0; i < poolSize; i++) {
            try {
                Session session = token.openSession(SERIAL_SESSION, true, null, null);
                if (login(session)) {
                    sessionPool.add(session);
                    log.trace("Added session {} to pool for token {}", i + 1, tokenId);
                }
            } catch (PKCS11Exception e) {
                log.error("Failed to create session for token {}", tokenId, e);
                throw e;
            }
        }

        initialized = true;
        log.info("Session pool initialized for token {}", tokenId);
    }

    /**
     * Executes an operation with a session and returns a result.
     *
     * @param operation The operation to execute
     * @param <T>       The return type
     * @return The result of the operation
     * @throws Exception if the operation fails
     */
    public <T> T executeWithSession(FuncWithSession<T> operation) throws Exception {
        Session session = null;

        try {
            session = acquireSession();

            return operation.apply(session);
        } finally {
            releaseSession(session);
        }
    }

    @FunctionalInterface
    public interface FuncWithSession<R> {

        R apply(Session session) throws Exception;
    }

    @FunctionalInterface
    public interface ConsumerWithSession {

        void accept(Session session) throws Exception;
    }
    /**
     * Executes an operation with a session without returning a result.
     *
     * @param operation The operation to execute
     * @throws Exception if the operation fails
     */
    public void executeWithSession(ConsumerWithSession operation) throws Exception {
        executeWithSession(session -> {
            operation.accept(session);
            return null;
        });
    }

    /**
     * Acquires a session from the pool.
     *
     * @return A session
     * @throws Exception if a session cannot be acquired
     */
    private Session acquireSession() throws Exception {
        if (!initialized) {
            throw new CodedException(X_INTERNAL_ERROR, "Session pool not initialized for token %s", tokenId);
        }

        try {
            Session session = sessionPool.poll(sessionAcquireTimeoutMillis, TimeUnit.MILLISECONDS);
            if (session == null) {
                throw new CodedException(X_INTERNAL_ERROR,
                        "Timed out waiting for available session for token %s after %d ms",
                        tokenId, sessionAcquireTimeoutMillis);
            }
            return session;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CodedException(X_INTERNAL_ERROR, "Interrupted while waiting for session: %s", e.getMessage());
        }
    }

    /**
     * Releases a session back to the pool.
     *
     * @param session The session to release
     */
    private boolean releaseSession(Session session) {
        if (session != null) {
            try {
                return sessionPool.offer(session);
            } catch (Exception e) {
                log.error("Failed to return session to pool for token {}", tokenId, e);
            }
        }
        return false;
    }

    /**
     * Logs in to a session.
     *
     * @param session The session to log in to
     * @return true if login was successful
     */
    private boolean login(Session session) {
        try {
            char[] password = PasswordStore.getPassword(tokenId);
            if (password == null) {
                log.debug("Cannot login, no password stored for token {}", tokenId);
                return false;
            }

            HardwareTokenUtil.login(session, password);
            log.trace("Successfully logged in to session for token {}", tokenId);
            return true;
        } catch (Exception e) {
            log.warn("Failed to login to session for token {}", tokenId, e);
            return false;
        }
    }

    /**
     * Logs out of a session.
     *
     * @param session The session to log out of
     */
    private void logout(Session session) {
        try {
            HardwareTokenUtil.logout(session);
            log.trace("Successfully logged out of session for token {}", tokenId);
        } catch (Exception e) {
            log.warn("Failed to logout of session for token {}", tokenId, e);
        }
    }

    /**
     * Closes all sessions in the pool.
     */
    public void destroy() {
        log.debug("Destroying session pool for token {}", tokenId);

        Session session;
        while ((session = sessionPool.poll()) != null) {
            try {
                session.closeSession();
            } catch (Exception e) {
                log.warn("Failed to close session for token {}", tokenId, e);
            }
        }

        initialized = false;
        log.info("Session pool destroyed for token {}", tokenId);
    }
}
