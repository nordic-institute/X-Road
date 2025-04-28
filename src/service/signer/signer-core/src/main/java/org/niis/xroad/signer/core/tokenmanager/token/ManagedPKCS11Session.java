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

import iaik.pkcs.pkcs11.Session;
import iaik.pkcs.pkcs11.Token;
import iaik.pkcs.pkcs11.TokenException;
import iaik.pkcs.pkcs11.wrapper.PKCS11Exception;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.core.passwordstore.PasswordStore;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static iaik.pkcs.pkcs11.Token.SessionType.SERIAL_SESSION;

@Slf4j
@RequiredArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class ManagedPKCS11Session {
    private final String tokenId;
    private final Session session;

    public static ManagedPKCS11Session openSession(Token token, String tokenId) throws TokenException {
        if (token == null) {
            throw new CodedException(X_INTERNAL_ERROR, "Token is null");
        }

        try {
            Session session = token.openSession(SERIAL_SESSION, true, null, null);
            return new ManagedPKCS11Session(tokenId, session);
        } catch (TokenException e) {
            log.error("Failed to create session for token {}", tokenId, e);
            throw e;
        }
    }

    public Session get() {
        return session;
    }

    /**
     * Logs in to a session.
     *
     * @return true if login was successful
     */
    public boolean login() throws PKCS11Exception {
        try {
            char[] password = PasswordStore.getPassword(tokenId);
            if (password == null) {
                log.warn("Cannot login, no password stored for token {}", tokenId);
                return false;
            }

            HardwareTokenUtil.login(session, password);
            log.trace("Successfully logged in to session for token {}", tokenId);
            return true;
        } catch (PKCS11Exception pkcs11Exception) {
            throw pkcs11Exception;
        } catch (Exception e) {
            log.warn("Failed to login to session for token {}", tokenId, e);
            return false;
        }
    }

    /**
     * Logs out of a session.
     */
    public boolean logout() throws PKCS11Exception {
        try {
            HardwareTokenUtil.logout(session);
            log.trace("Successfully logged out of session for token {}", tokenId);
            return true;
        } catch (PKCS11Exception pkcs11Exception) {
            throw pkcs11Exception;
        } catch (Exception e) {
            log.warn("Failed to logout of session for token {}", tokenId, e);
            return false;
        }
    }

    public void close() {
        try {
            session.closeSession();
            log.trace("Successfully closed session for token {}", tokenId);
        } catch (TokenException e) {
            log.warn("Failed to close session for token {}", tokenId, e);
        }
    }


    public long getSessionHandle() {
        return session.getSessionHandle();
    }
}
