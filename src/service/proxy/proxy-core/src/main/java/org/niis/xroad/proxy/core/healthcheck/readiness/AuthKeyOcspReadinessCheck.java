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

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.niis.xroad.common.healthcheck.CachingHealthCheck;
import org.niis.xroad.common.healthcheck.TimedHealthCheck;
import org.niis.xroad.globalconf.cert.CertChain;
import org.niis.xroad.keyconf.KeyConfProvider;
import org.niis.xroad.keyconf.dto.AuthKey;
import org.niis.xroad.proxy.core.configuration.ProxyHealthCheckProperties;

import java.security.cert.X509Certificate;

import static org.niis.xroad.common.healthcheck.HealthCheckConstants.STATUS;

/**
 * Readiness check for the Proxy auth-key OCSP response status.
 * <p>
 * Startup-tolerant: null auth key / cert chain / end-entity cert return UP with a
 * fine-grained {@code status=AWAITING_*} payload rather than DOWN, to prevent restart
 * cascades during fresh Security Server installs where the signer has not yet synced.
 * <p>
 * Self-wraps in its constructor:
 * {@code CachingHealthCheck(TimedHealthCheck(this::doCheck))} — cache on the outside,
 * timeout on the inside. Repeated probes within {@code authKey.successTtl} serve the
 * cached response without invoking the timeout executor or signer RPC. A hung signer RPC
 * returns DOWN within {@code authKey.timeout} without blocking the probe thread.
 */
@Slf4j
@Readiness
@ApplicationScoped
public final class AuthKeyOcspReadinessCheck implements HealthCheck {

    private static final String NAME = "PROXY_AUTH_KEY_OCSP_READINESS_CHECK";

    private final KeyConfProvider keyConfProvider;
    private final HealthCheck wrapped;

    public AuthKeyOcspReadinessCheck(KeyConfProvider keyConfProvider,
                                     ProxyHealthCheckProperties props) {
        this.keyConfProvider = keyConfProvider;
        ProxyHealthCheckProperties.TtlGroup cfg = props.authKey();
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
        AuthKey authKey = keyConfProvider.getAuthKey();
        if (authKey == null) {
            return HealthCheckResponse.named(NAME)
                    .up()
                    .withData(STATUS, "AWAITING_AUTH_KEY")
                    .build();
        }
        CertChain certChain = authKey.certChain();
        if (certChain == null) {
            return HealthCheckResponse.named(NAME)
                    .up()
                    .withData(STATUS, "AWAITING_CERT_CHAIN")
                    .build();
        }
        X509Certificate certificate = certChain.getEndEntityCert();
        if (certificate == null) {
            return HealthCheckResponse.named(NAME)
                    .up()
                    .withData(STATUS, "AWAITING_END_ENTITY_CERT")
                    .build();
        }

        int ocspStatus;
        try {
            ocspStatus = keyConfProvider.getOcspResponse(certificate).getStatus();
        } catch (Exception e) {
            log.error("Getting OCSP response for authentication key failed", e);
            return HealthCheckResponse.named(NAME)
                    .down()
                    .withData(STATUS, "OCSP_LOOKUP_FAILED")
                    .withData("error", e.getClass().getSimpleName())
                    .build();
        }

        if (ocspStatus == OCSPResp.SUCCESSFUL) {
            return HealthCheckResponse.named(NAME)
                    .up()
                    .withData(STATUS, "OK")
                    .withData("ocsp_status", ocspStatus)
                    .build();
        }
        return HealthCheckResponse.named(NAME)
                .down()
                .withData(STATUS, "OCSP_FAILED")
                .withData("ocsp_status", ocspStatus)
                .build();
    }
}
