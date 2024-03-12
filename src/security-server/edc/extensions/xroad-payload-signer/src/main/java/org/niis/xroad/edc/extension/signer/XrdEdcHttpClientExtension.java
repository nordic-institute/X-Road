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
import org.eclipse.edc.connector.core.base.EdcHttpClientImpl;
import org.eclipse.edc.connector.core.base.RetryPolicyFactory;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.http.EdcHttpClient;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.niis.xroad.ssl.SSLContextBuilder;

import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.SECONDS;

@Provides({EdcHttpClient.class})
public class XrdEdcHttpClientExtension implements ServiceExtension {
    public static final String NAME = "XRD Core Default Services";

    private static final int TIMEOUT = 10;
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
        return RetryPolicyFactory.create(context);
    }


}
