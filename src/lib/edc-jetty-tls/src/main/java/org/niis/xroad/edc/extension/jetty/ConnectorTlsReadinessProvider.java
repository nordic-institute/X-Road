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

/**
 * Reports ready only while the HTTPS connectors serve connector TLS material that has not expired. While
 * connector TLS is not served the reason is the text of the failure.
 */
final class ConnectorTlsReadinessProvider implements ReadinessProvider {
    static final String COMPONENT = "connector-tls";

    private final XRoadJettyService jettyService;

    ConnectorTlsReadinessProvider(XRoadJettyService jettyService) {
        this.jettyService = jettyService;
    }

    @Override
    public HealthCheckResult get() {
        var result = HealthCheckResult.Builder.newInstance().component(COMPONENT);
        if (!jettyService.isServingConnectorTls()) {
            return result.failure(jettyService.notServedCause()
                    .map(cause -> "Connector TLS is not served: " + cause)
                    .orElse("Connector TLS is not served yet")).build();
        }
        var expiredAt = jettyService.servedCertificateExpiredAt();
        if (expiredAt.isEmpty()) {
            return result.success().build();
        }
        return result.failure("Served connector TLS certificate expired at " + expiredAt.get()).build();
    }
}
