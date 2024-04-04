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
package org.niis.xroad.edc.extension.signer;

import dev.failsafe.RetryPolicy;
import lombok.SneakyThrows;
import okhttp3.EventListener;
import okhttp3.OkHttpClient;
import org.eclipse.edc.connector.core.base.RetryPolicyConfiguration;
import org.eclipse.edc.connector.core.base.RetryPolicyFactory;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.http.EdcHttpClient;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.http.client.EdcHttpClientImpl;
import org.niis.xroad.ssl.SSLContextBuilder;

import static java.lang.Integer.parseInt;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.SECONDS;

@Provides({EdcHttpClient.class})
public class XrdEdcHttpClientExtension implements ServiceExtension {
    public static final String NAME = "XRD Core Default Services";

    private static final int TIMEOUT = 10;

    private static final String RETRY_POLICY_DEFAULT_RETRIES = "5";
    private static final String RETRY_POLICY_DEFAULT_MIN_BACKOFF = "500";
    private static final String RETRY_POLICY_DEFAULT_MAX_BACKOFF = "10000";
    private static final String RETRY_POLICY_DEFAULT_LOG = "false";
    @Setting(value = "RetryPolicy: Maximum retries before a failure is propagated",
            defaultValue = RETRY_POLICY_DEFAULT_RETRIES)
    private static final String RETRY_POLICY_MAX_RETRIES = "edc.core.retry.retries.max";
    @Setting(value = "RetryPolicy: Minimum number of milliseconds for exponential backoff",
            defaultValue = RETRY_POLICY_DEFAULT_MIN_BACKOFF)
    private static final String RETRY_POLICY_BACKOFF_MIN_MILLIS = "edc.core.retry.backoff.min";
    @Setting(value = "RetryPolicy: Maximum number of milliseconds for exponential backoff.",
            defaultValue = RETRY_POLICY_DEFAULT_MAX_BACKOFF)
    private static final String RETRY_POLICY_BACKOFF_MAX_MILLIS = "edc.core.retry.backoff.max";
    @Setting(value = "RetryPolicy: Log onRetry events", defaultValue = RETRY_POLICY_DEFAULT_LOG)
    static final String RETRY_POLICY_LOG_ON_RETRY = "edc.core.retry.log.on.retry";
    @Setting(value = "RetryPolicy: Log onRetryScheduled events", defaultValue = RETRY_POLICY_DEFAULT_LOG)
    static final String RETRY_POLICY_LOG_ON_RETRY_SCHEDULED = "edc.core.retry.log.on.retry.scheduled";
    @Setting(value = "RetryPolicy: Log onRetriesExceeded events", defaultValue = RETRY_POLICY_DEFAULT_LOG)
    static final String RETRY_POLICY_LOG_ON_RETRIES_EXCEEDED = "edc.core.retry.log.on.retries.exceeded";
    @Setting(value = "RetryPolicy: Log onFailedAttempt events", defaultValue = RETRY_POLICY_DEFAULT_LOG)
    static final String RETRY_POLICY_LOG_ON_FAILED_ATTEMPT = "edc.core.retry.log.on.failed.attempt";
    @Setting(value = "RetryPolicy: Log onAbort events", defaultValue = RETRY_POLICY_DEFAULT_LOG)
    static final String RETRY_POLICY_LOG_ON_ABORT = "edc.core.retry.log.on.abort";

    /**
     * An optional OkHttp {@link EventListener} that can be used to instrument OkHttp client for collecting metrics.
     */
    @Inject(required = false)
    private EventListener okHttpEventListener;

    @Override
    public String name() {
        return NAME;
    }

    @Provider
    public EdcHttpClient edcHttpClient(ServiceExtensionContext context) {
        return new EdcHttpClientImpl(
                okHttpClient(context),
                retryPolicy(context),
                context.getMonitor()
        );
    }

    @Provider
    @SneakyThrows
    public OkHttpClient okHttpClient(ServiceExtensionContext context) {
        SSLContextBuilder.Result ctxResult = SSLContextBuilder.create();
        var builder = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT, SECONDS)
                .readTimeout(TIMEOUT, SECONDS)
                .hostnameVerifier((s, sslSession) -> true)
                .sslSocketFactory(ctxResult.sslContext().getSocketFactory(), ctxResult.trustManager());

        ofNullable(okHttpEventListener).ifPresent(builder::eventListener);

        return builder.build();
    }

    @Provider
    public <T> RetryPolicy<T> retryPolicy(ServiceExtensionContext context) {
        var configuration = RetryPolicyConfiguration.Builder.newInstance()
                .maxRetries(context.getSetting(RETRY_POLICY_MAX_RETRIES, parseInt(RETRY_POLICY_DEFAULT_RETRIES)))
                .minBackoff(context.getSetting(RETRY_POLICY_BACKOFF_MIN_MILLIS, parseInt(RETRY_POLICY_DEFAULT_MIN_BACKOFF)))
                .maxBackoff(context.getSetting(RETRY_POLICY_BACKOFF_MAX_MILLIS, parseInt(RETRY_POLICY_DEFAULT_MAX_BACKOFF)))
                .logOnRetry(context.getSetting(RETRY_POLICY_LOG_ON_RETRY, false))
                .logOnRetryScheduled(context.getSetting(RETRY_POLICY_LOG_ON_RETRY_SCHEDULED, false))
                .logOnRetriesExceeded(context.getSetting(RETRY_POLICY_LOG_ON_RETRIES_EXCEEDED, false))
                .logOnFailedAttempt(context.getSetting(RETRY_POLICY_LOG_ON_FAILED_ATTEMPT, false))
                .logOnAbort(context.getSetting(RETRY_POLICY_LOG_ON_ABORT, false))
                .build();

        return RetryPolicyFactory.create(configuration, context.getMonitor());
    }


}
