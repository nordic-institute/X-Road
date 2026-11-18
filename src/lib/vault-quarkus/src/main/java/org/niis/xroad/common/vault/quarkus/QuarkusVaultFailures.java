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
package org.niis.xroad.common.vault.quarkus;

import io.quarkus.vault.client.VaultClientException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.exception.ExceptionUtils;

import javax.net.ssl.SSLException;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Decides which failures of a Quarkus secret store call are worth retrying: server errors, a redirect to a leader
 * the extension does not follow, and connection or timeout failures anywhere in the cause chain.
 * Client errors such as 403 and 404 and TLS failures such as a rejected certificate are never retried.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class QuarkusVaultFailures {

    private static final int HTTP_TEMPORARY_REDIRECT = 307;
    private static final int HTTP_SERVER_ERROR = 500;
    private static final String VAULT_CLIENT_PACKAGE = "io.quarkus.vault.client.";

    public static boolean isRetryable(Throwable failure) {
        var chain = ExceptionUtils.getThrowableList(failure);
        if (chain.stream().anyMatch(SSLException.class::isInstance)) {
            return false;
        }
        for (var cause : chain) {
            if (cause instanceof VaultClientException vaultFailure && vaultFailure.getStatus() != null) {
                return isRetryableStatus(vaultFailure.getStatus());
            }
            if (cause instanceof IOException || cause instanceof TimeoutException || isVaultClientNullPointer(cause)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRetryableStatus(int status) {
        return status >= HTTP_SERVER_ERROR || status == HTTP_TEMPORARY_REDIRECT;
    }

    /**
     * quarkus-vault-client 4.10.0 throws a bare NullPointerException from its own retry check when the
     * connection is closed without a response.
     */
    private static boolean isVaultClientNullPointer(Throwable cause) {
        if (!(cause instanceof NullPointerException)) {
            return false;
        }
        var frames = cause.getStackTrace();
        return frames.length > 0 && frames[0].getClassName().startsWith(VAULT_CLIENT_PACKAGE);
    }
}
