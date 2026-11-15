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
package org.niis.xroad.common.vault.spring;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import javax.net.ssl.SSLException;

import java.io.IOException;

/**
 * Decides which failures of a Spring secret store call are worth retrying: server errors and connection or
 * timeout failures anywhere in the cause chain. Client errors such as 403 and 404 and TLS failures such as a
 * rejected certificate are never retried.
 * A redirect needs no case of its own: the HTTP client follows it before any status reaches this check, and a
 * redirect to an unreachable leader surfaces as a connection failure.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SpringVaultFailures {

    private static final int HTTP_SERVER_ERROR = 500;

    static boolean isRetryable(Throwable failure) {
        var chain = ExceptionUtils.getThrowableList(failure);
        if (chain.stream().anyMatch(SSLException.class::isInstance)) {
            return false;
        }
        for (var cause : chain) {
            if (cause instanceof RestClientResponseException responseFailure) {
                return responseFailure.getStatusCode().value() >= HTTP_SERVER_ERROR;
            }
            if (cause instanceof ResourceAccessException || cause instanceof IOException) {
                return true;
            }
        }
        return false;
    }
}
