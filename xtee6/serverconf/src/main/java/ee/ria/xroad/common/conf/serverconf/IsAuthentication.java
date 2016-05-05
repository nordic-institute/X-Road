/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.common.conf.serverconf;

import java.security.cert.X509Certificate;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.identifier.ClientId;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_SSL_AUTH_FAILED;

/**
 * Encapsulates the information system authentication method.
 */
@Slf4j
public enum IsAuthentication {

    NOSSL,
    SSLNOAUTH,
    SSLAUTH;

    /**
     * Verifies the authentication for the client certificate.
     * @param client the client identifier
     * @param auth the authentication data of the information system
     * @throws Exception if verification fails
     */
    public static void verifyClientAuthentication(ClientId client,
            IsAuthenticationData auth) throws Exception {
        IsAuthentication isAuthentication =
                ServerConf.getIsAuthentication(client);
        if (isAuthentication == null) {
            // Means the client was not found in the server conf.
            // The getIsAuthentication method implemented in ServerConfCommonImpl
            // checks if the client exists; if it does, returns the
            // isAuthentication value or NOSSL if no value is specified.
            throw new CodedException(X_INTERNAL_ERROR,
                    "Client '%s' not found", client);
        }

        log.trace("IS authentication for client '{}' is: {}", client,
                isAuthentication);

        if (isAuthentication == IsAuthentication.SSLNOAUTH
                && auth.isPlaintextConnection()) {
            throw new CodedException(X_SSL_AUTH_FAILED,
                    "Client (%s) specifies HTTPS NO AUTH but client made "
                            + " plaintext connection", client);
        } else if (isAuthentication == IsAuthentication.SSLAUTH) {
            if (auth.getCert() == null) {
                throw new CodedException(X_SSL_AUTH_FAILED,
                        "Client (%s) specifies HTTPS but did not supply"
                                + " TLS certificate", client);
            }

            if (auth.getCert().equals(InternalSSLKey.load().getCert())) {
                // do not check certificates for local TLS connections
                return;
            }

            List<X509Certificate> isCerts = ServerConf.getIsCerts(client);
            if (isCerts.isEmpty()) {
                throw new CodedException(X_SSL_AUTH_FAILED,
                        "Client (%s) has no IS certificates", client);
            }

            if (!isCerts.contains(auth.getCert())) {
                throw new CodedException(X_SSL_AUTH_FAILED,
                        "Client (%s) TLS certificate does not match any"
                                + " IS certificates", client);
            }
        }
    }

}
