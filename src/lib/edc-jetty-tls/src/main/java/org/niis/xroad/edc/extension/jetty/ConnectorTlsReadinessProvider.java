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
package org.niis.xroad.edc.extension.jetty;

import org.eclipse.edc.spi.system.health.HealthCheckResult;
import org.eclipse.edc.spi.system.health.ReadinessProvider;
import org.niis.xroad.edc.reload.ReadinessDetailProvider;

import java.util.Map;

/**
 * Reports ready only while the HTTPS connectors serve connector TLS material. While newer material is refused
 * the previously served certificate keeps the service ready until that certificate expires, and the refusal is
 * published as readiness detail.
 */
final class ConnectorTlsReadinessProvider implements ReadinessProvider, ReadinessDetailProvider {

    static final String COMPONENT = "connector-tls";
    static final String DETAIL_SERVED_EXPIRES = COMPONENT + "-served-expires";
    static final String DETAIL_REJECTED_SINCE = COMPONENT + "-rejected-since";
    static final String DETAIL_REJECTED_CAUSE = COMPONENT + "-rejected-cause";

    private final XRoadJettyService jettyService;

    ConnectorTlsReadinessProvider(XRoadJettyService jettyService) {
        this.jettyService = jettyService;
    }

    @Override
    public HealthCheckResult get() {
        var result = HealthCheckResult.Builder.newInstance().component(COMPONENT);
        if (!jettyService.isServingConnectorTls()) {
            return result.failure("Connector TLS certificate is not served yet").build();
        }
        if (jettyService.isServedCertificateExpiredWhileRejecting()) {
            var rejection = jettyService.rejection().orElseThrow();
            return result.failure("Served connector TLS certificate expired at %s and newer material is rejected since %s: %s"
                    .formatted(rejection.servedNotAfter(), rejection.since(), rejection.cause())).build();
        }
        return result.success().build();
    }

    @Override
    public Map<String, String> readinessDetail() {
        return jettyService.rejection()
                .map(rejection -> Map.of(
                        DETAIL_SERVED_EXPIRES, rejection.servedNotAfter().toString(),
                        DETAIL_REJECTED_SINCE, rejection.since().toString(),
                        DETAIL_REJECTED_CAUSE, rejection.cause()))
                .orElse(Map.of());
    }
}
