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
package org.niis.xroad.proxy.core.healthcheck.readiness;

import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.niis.xroad.common.healthcheck.CachingHealthCheck;
import org.niis.xroad.common.healthcheck.TimedHealthCheck;
import org.niis.xroad.proxy.core.configuration.ProxyHealthCheckProperties;
import org.niis.xroad.signer.client.SignerRpcClient;

import static org.niis.xroad.common.healthcheck.HealthCheckConstants.STATUS;

/**
 * Readiness check for HSM operational status via signer gRPC.
 * <p>
 * Conditionally registered: only constructed when
 * {@code xroad.proxy.hsm-health-check-enabled=true} (property key preserved verbatim
 * for operator config backward-compatibility). When the property is absent or set to any other
 * value, Quarkus ARC skips bean construction entirely — the check does NOT appear in
 * {@code /q/health/ready} response.
 * <p>
 * Self-wraps in its constructor: {@code CachingHealthCheck(TimedHealthCheck(this::doCheck))}.
 * Cache outside, timeout inside. Repeated probes within {@code hsm.successTtl} serve cached response
 * without invoking signer RPC; hung signer RPC returns DOWN within {@code hsm.timeout}.
 */
@Slf4j
@Readiness
@ApplicationScoped
@LookupIfProperty(name = "xroad.proxy.hsm-health-check-enabled", stringValue = "true")
public final class HsmOperationalReadinessCheck implements HealthCheck {

    private static final String NAME = "PROXY_HSM_READINESS_CHECK";

    private final SignerRpcClient signerRpcClient;
    private final HealthCheck wrapped;

    public HsmOperationalReadinessCheck(SignerRpcClient signerRpcClient,
                                        ProxyHealthCheckProperties props) {
        this.signerRpcClient = signerRpcClient;
        ProxyHealthCheckProperties.TtlGroup cfg = props.hsm();
        HealthCheck timed = new TimedHealthCheck(this::doCheck, cfg.timeout(), NAME);
        this.wrapped = new CachingHealthCheck(timed,
                cfg.successTtl(), cfg.errorTtl(),
                cfg.maxErrorTtl(), cfg.backoffMultiplier());
    }

    @Override
    public HealthCheckResponse call() {
        return wrapped.call();
    }

    private HealthCheckResponse doCheck() {
        try {
            if (signerRpcClient.isHSMOperational()) {
                return HealthCheckResponse.named(NAME)
                        .up()
                        .withData(STATUS, "OK")
                        .build();
            }
            return HealthCheckResponse.named(NAME)
                    .down()
                    .withData(STATUS, "HSM_NON_OPERATIONAL")
                    .build();
        } catch (Exception e) {
            log.error("Exception when verifying HSM status", e);
            return HealthCheckResponse.named(NAME)
                    .down()
                    .withData(STATUS, "HSM_CHECK_FAILED")
                    .withData("error", e.getClass().getSimpleName())
                    .build();
        }
    }
}
