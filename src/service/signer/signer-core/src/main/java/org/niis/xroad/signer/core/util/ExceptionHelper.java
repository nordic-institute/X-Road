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
package org.niis.xroad.signer.core.util;

import org.niis.xroad.common.core.exception.XrdRuntimeException;

import static org.niis.xroad.common.core.exception.ErrorCode.ACCESS_DENIED;
import static org.niis.xroad.common.core.exception.ErrorCode.CERT_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.CSR_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.KEY_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.LOGIN_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.LOGOUT_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.TOKEN_NOT_ACTIVE;
import static org.niis.xroad.common.core.exception.ErrorCode.TOKEN_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.TOKEN_NOT_INITIALIZED;
import static org.niis.xroad.common.core.exception.ErrorCode.TOKEN_PIN_INCORRECT;

/**
 * Contains helper methods for constructing XrdRuntimeExceptions which are used in
 * multiple places.
 */
public final class ExceptionHelper {

    private ExceptionHelper() {
    }

    /**
     * @param tokenId the token id
     * @return exception indicating a token is not found
     */
    public static XrdRuntimeException tokenNotFound(String tokenId) {
        return XrdRuntimeException.systemException(TOKEN_NOT_FOUND,
                "Token '%s' not found".formatted(tokenId));
    }

    /**
     * @param tokenId the token id
     * @return exception indicating a token is not active
     */
    public static XrdRuntimeException tokenNotActive(String tokenId) {
        return XrdRuntimeException.systemException(TOKEN_NOT_ACTIVE,
                "Token '%s' not active".formatted(tokenId));
    }

    public static XrdRuntimeException writeNotAvailable() {
        return XrdRuntimeException.systemException(ACCESS_DENIED, "Write operations are not allowed on secondary node");
    }

    /**
     * @param tokenId the token id
     * @return exception indicating a token is not initialized
     */
    public static XrdRuntimeException tokenNotInitialized(String tokenId) {
        return XrdRuntimeException.systemException(TOKEN_NOT_INITIALIZED,
                "Token '%s' not initialized".formatted(tokenId));
    }

    /**
     * @param keyId the key id
     * @return exception indicating a key is not found
     */
    public static XrdRuntimeException keyNotFound(String keyId) {
        return XrdRuntimeException.systemException(KEY_NOT_FOUND,
                "Key '%s' not found".formatted(keyId));
    }

    /**
     * @param keyId the key id
     * @return exception indicating a key is not available
     */
    public static XrdRuntimeException keyNotAvailable(String keyId) {
        return XrdRuntimeException.systemException(KEY_NOT_FOUND, "Key '%s' not available".formatted(keyId));
    }

    /**
     * @param certId the certificate id
     * @return exception indicating a certificate is not found
     */
    public static XrdRuntimeException certWithIdNotFound(String certId) {
        return XrdRuntimeException.systemException(CERT_NOT_FOUND, "Certificate with id '%s' not found".formatted(certId));
    }

    /**
     * @param certHash the certificate hash
     * @return exception indicating a certificate is not found
     */
    public static XrdRuntimeException certWithHashNotFound(String certHash) {
        return XrdRuntimeException.systemException(CERT_NOT_FOUND,
                "Certificate with hash '%s' not found".formatted(certHash));
    }

    /**
     * @param certRequestId the certificate request id
     * @return exception indicating a csr is not found
     */
    public static XrdRuntimeException csrWithIdNotFound(String certRequestId) {
        return XrdRuntimeException.systemException(CSR_NOT_FOUND,
                "Certificate request '%s' not found".formatted(certRequestId));
    }

    /**
     * @param message the message
     * @return exception indicating login to token failed
     */
    public static XrdRuntimeException loginFailed(String message) {
        return XrdRuntimeException.systemException(LOGIN_FAILED,
                "Login failed: %s".formatted(message));
    }

    /**
     * @param message the message
     * @return exception indicating logout of a token failed
     */
    public static XrdRuntimeException logoutFailed(String message) {
        return XrdRuntimeException.systemException(LOGOUT_FAILED,
                "Logout failed: %s".formatted(message));
    }

    /**
     * @return exception indicating the provided pin code was incorrect
     */
    public static XrdRuntimeException pinIncorrect() {
        return XrdRuntimeException.systemException(TOKEN_PIN_INCORRECT, "PIN incorrect");
    }
}
