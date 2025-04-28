/*
 * The MIT License
 *
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
package org.niis.xroad.signer.core.tokenmanager.token;

import ee.ria.xroad.common.CodedException;

import iaik.pkcs.pkcs11.Token;
import iaik.pkcs.pkcs11.TokenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.niis.xroad.signer.api.exception.SignerException;
import org.niis.xroad.signer.core.config.SignerHwTokenAddonProperties;
import org.niis.xroad.signer.core.passwordstore.PasswordStore;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;

/**
 * SessionProvider implementation using Apache Commons Pool 2 for managing PKCS#11 sessions.
 */
@Slf4j
class HardwareTokenSessionPool implements SessionProvider {
    private final GenericObjectPool<ManagedPKCS11Session> pool;

    HardwareTokenSessionPool(SignerHwTokenAddonProperties properties, Token token, String tokenId) throws Exception {
        this.pool = createPool(properties, token, tokenId);
    }

    @Override
    public <T> T executeWithSession(FuncWithSession<T> operation) throws Exception {
        ManagedPKCS11Session session = pool.borrowObject();
        try {
            return operation.apply(session);
        } finally {
            pool.returnObject(session);
        }
    }

    @Override
    public void executeWithSession(ConsumerWithSession operation) throws Exception {
        executeWithSession(session -> {
            operation.accept(session);
            return null;
        });
    }

    @Override
    public void close() {
        pool.close();
    }

    private static GenericObjectPool<ManagedPKCS11Session> createPool(SignerHwTokenAddonProperties properties, Token token, String tokenId)
            throws Exception {
        log.info("Initializing Apache Commons session pool with settings {} for token {}", properties, tokenId);

        if (token == null) {
            throw new CodedException(X_INTERNAL_ERROR, "Token is null for pool initialization");
        }

        var factory = new ManagedPKCS11SessionFactory(token, tokenId);
        GenericObjectPoolConfig<ManagedPKCS11Session> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(properties.poolMaxTotal());
        config.setMinIdle(properties.poolMinIdle());
        config.setMaxIdle(properties.poolMaxIdle());
        config.setBlockWhenExhausted(true);
        config.setMaxWait(properties.sessionAcquireTimeout());
        config.setTestOnBorrow(true);

        var objectPool = new GenericObjectPool<>(factory, config);

        prefillPool(objectPool, tokenId);
        return objectPool;
    }

    private static void prefillPool(GenericObjectPool<ManagedPKCS11Session> objectPool, String tokenId)throws Exception {
        try {
            log.debug("Pre-filling pool for token {}...", tokenId);
            objectPool.preparePool();
        } catch (Exception e) {
            log.error("Failed to pre-fill session pool for token {}, closing pool.", tokenId, e);
            try {
                objectPool.close();
            } catch (Exception closeEx) {
                log.error("Error closing pool after pre-fill failure for token {}", tokenId, closeEx);
            }
            throw new SignerException("Failed to pre-fill session pool for token " + tokenId, e);
        }
    }

    @RequiredArgsConstructor
    static class ManagedPKCS11SessionFactory extends BasePooledObjectFactory<ManagedPKCS11Session> {
        private final Token token;
        private final String tokenId;

        @Override
        public ManagedPKCS11Session create() throws Exception {
            log.debug("Creating new PKCS#11 session for token {}", tokenId);

            var session = ManagedPKCS11Session.openSession(token, tokenId);

            char[] pin = PasswordStore.getPassword(tokenId);
            if (pin == null) {
                throw new CodedException("PIN not available in PasswordStore for auto-login of pooled session on token " + tokenId);
            }
            if (session.login()) {
                log.debug("Immediate login successful for new pooled session {} on token {}", session.getSessionHandle(), tokenId);

            } else {
                session.close();
                throw new SignerException("Failed to login to session for token " + tokenId);
            }

            return session;
        }

        @Override
        public PooledObject<ManagedPKCS11Session> wrap(ManagedPKCS11Session session) {
            return new DefaultPooledObject<>(session);
        }

        @Override
        public void destroyObject(PooledObject<ManagedPKCS11Session> p) throws Exception {
            ManagedPKCS11Session session = p.getObject();
            if (session != null) {
                log.debug("Destroying PKCS#11 session {} for token {}", session.getSessionHandle(), tokenId);
                try {
                    session.close();
                } catch (Exception e) {
                    log.warn("Failed to close PKCS#11 session {} for token {} during destroy",
                            session.getSessionHandle(), tokenId, e);
                    // Swallow exception during destroy to avoid pool issues, but log it.
                }
            }
        }

        @Override
        public boolean validateObject(PooledObject<ManagedPKCS11Session> p) {
            var session = p.getObject();
            boolean isValid = session != null;
            log.info("Validating session {} for token {}: {}",
                    isValid ? session.getSessionHandle() : "null", tokenId, isValid);
            if (isValid) {
                try {
                    //check if session info is being returned.
                    if (session.get().getSessionInfo().getState() == null) {
                        isValid = false;
                    }
                } catch (TokenException e) {
                    log.trace("Session {} for token {} is invalid: {}", session.getSessionHandle(), tokenId, e.getMessage());
                    isValid = false;
                }

            }
            return isValid;
        }

    }

}
