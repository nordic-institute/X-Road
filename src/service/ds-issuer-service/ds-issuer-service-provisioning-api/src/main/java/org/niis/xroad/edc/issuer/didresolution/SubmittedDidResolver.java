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
package org.niis.xroad.edc.issuer.didresolution;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.failsafe.RetryPolicy;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import okhttp3.ConnectionPool;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import org.eclipse.edc.http.client.EdcHttpClientImpl;
import org.eclipse.edc.iam.did.web.resolution.WebDidResolver;
import org.eclipse.edc.spi.monitor.Monitor;

import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Fetches the DID document of a submitted did:web DID over HTTPS and checks that the document names the DID.
 *
 * <p>The fetch uses a copy of the runtime's OkHttp client, so it trusts exactly what the runtime trusts. The copy
 * follows no redirects, does not retry, and has its own connection pool and call timeout.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class SubmittedDidResolver {

    private static final String DID_WEB_PREFIX = "did:web:";
    private static final String HTTPS_SCHEME_PREFIX = "https://";

    private final WebDidResolver webDidResolver;
    private final RecordingEdcHttpClient httpClient;
    private final Monitor monitor;

    /**
     * @param runtimeHttpClient the runtime's OkHttp client; copied, not modified
     * @param mapper            the JSON mapper that reads DID documents
     * @param fetchTimeout      the call timeout of one DID document fetch
     * @param monitor           the monitor of the runtime
     */
    public static SubmittedDidResolver create(OkHttpClient runtimeHttpClient, ObjectMapper mapper,
                                              Duration fetchTimeout, Monitor monitor) {
        var edcMonitor = new SevereToDebugMonitor(monitor);
        var fetchClient = runtimeHttpClient.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .connectionPool(new ConnectionPool(0, 1, TimeUnit.SECONDS))
                .callTimeout(fetchTimeout)
                .build();
        var recordingClient = new RecordingEdcHttpClient(new EdcHttpClientImpl(
                fetchClient, RetryPolicy.<Response>builder().withMaxRetries(0).build(), edcMonitor));
        return new SubmittedDidResolver(new WebDidResolver(recordingClient, true, mapper, edcMonitor),
                recordingClient, monitor);
    }

    public Resolution resolve(String did) {
        if (!hasParsableAuthority(did)) {
            return Resolution.notResolved(did, Reason.MALFORMED_DID);
        }
        httpClient.forget();
        try {
            var result = webDidResolver.resolve(did);
            if (result.succeeded()) {
                return did.equals(result.getContent().getId())
                        ? Resolution.resolved(did)
                        : Resolution.notResolved(did, Reason.ID_MISMATCH);
            }
            monitor.debug("DID document of %s not resolved: %s".formatted(did, result.getFailureDetail()));
            return Resolution.notResolved(did, failureReason());
        } catch (RuntimeException e) {
            monitor.debug("DID document of %s could not be fetched".formatted(did), e);
            return Resolution.notResolved(did, Reason.FETCH_FAILURE);
        } finally {
            httpClient.forget();
        }
    }

    private Reason failureReason() {
        var attempt = httpClient.lastAttempt();
        if (attempt == null) {
            return Reason.MALFORMED_DID;
        }
        if (attempt.failure() == null && attempt.status() != null && attempt.status() != HttpURLConnection.HTTP_OK) {
            return Reason.NO_DOCUMENT;
        }
        return Reason.FETCH_FAILURE;
    }

    private static boolean hasParsableAuthority(String did) {
        if (did == null || !did.startsWith(DID_WEB_PREFIX)) {
            return false;
        }
        var methodSpecificId = did.substring(DID_WEB_PREFIX.length());
        var authority = methodSpecificId.substring(0, indexOfPathStart(methodSpecificId));
        try {
            return !authority.isEmpty()
                    && HttpUrl.parse(HTTPS_SCHEME_PREFIX + URLDecoder.decode(authority, StandardCharsets.UTF_8)) != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static int indexOfPathStart(String methodSpecificId) {
        var separator = methodSpecificId.indexOf(':');
        return separator < 0 ? methodSpecificId.length() : separator;
    }

    public enum Reason {
        MALFORMED_DID,
        NO_DOCUMENT,
        ID_MISMATCH,
        FETCH_FAILURE
    }

    /**
     * @param did    the DID that was resolved
     * @param reason why the DID did not resolve, {@code null} when it resolved
     */
    public record Resolution(String did, Reason reason) {

        static Resolution resolved(String did) {
            return new Resolution(did, null);
        }

        static Resolution notResolved(String did, Reason reason) {
            return new Resolution(did, reason);
        }

        public boolean isResolved() {
            return reason == null;
        }
    }
}
