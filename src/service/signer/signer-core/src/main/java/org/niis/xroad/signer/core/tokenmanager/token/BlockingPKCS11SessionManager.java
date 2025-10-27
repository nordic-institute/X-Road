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
import iaik.pkcs.pkcs11.wrapper.PKCS11Exception;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;

@Slf4j
public class BlockingPKCS11SessionManager implements SessionProvider {
    private final ManagedPKCS11Session session;

    BlockingPKCS11SessionManager(Token token, String tokenId) throws TokenException {
        this.session = createSession(token, tokenId);
    }

    ManagedPKCS11Session createSession(Token token, String tokenId) throws TokenException {
        try {
            return ManagedPKCS11Session.openSession(token, tokenId);
        } catch (TokenException e) {
            log.error("Failed to create session for token {}", tokenId, e);
            throw e;
        } catch (Exception e) {
            throw new CodedException(X_INTERNAL_ERROR, e, "Failed to create session for token %s: %s", tokenId, e.getMessage());
        }
    }

    @Override
    @ArchUnitSuppressed("NoVanillaExceptions")
    public synchronized <T> T executeWithSession(FuncWithSession<T> operation) {
        return operation.apply(session);
    }

    @Override
    @ArchUnitSuppressed("NoVanillaExceptions")
    public synchronized void executeWithSession(ConsumerWithSession operation) {
        operation.accept(session);
    }

    public synchronized boolean login() throws PKCS11Exception {
        return session.login();
    }

    public synchronized boolean logout() throws PKCS11Exception {
        return session.logout();
    }

    @Override
    public synchronized void close() {
        session.close();
    }
}
