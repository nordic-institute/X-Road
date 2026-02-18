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
package org.niis.xroad.common.healthcheck;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.niis.xroad.common.rpc.RpcProperties;
import org.niis.xroad.common.vault.VaultKeyClient;

import static org.niis.xroad.common.healthcheck.HealthCheckConstants.ERROR;
import static org.niis.xroad.common.healthcheck.HealthCheckConstants.REASON;
import static org.niis.xroad.common.healthcheck.HealthCheckConstants.STATUS;

/**
 * Readiness check for OpenBao/Vault PKI secret engine connectivity.
 * This check verifies that the PKI secret engine is accessible for certificate provisioning.
 *
 * <p>This check is conditional on gRPC mTLS being enabled (rpcProperties.useTls()).
 * If mTLS is not enabled, the check reports UP with status NOT_REQUIRED.
 *
 * <p>This check is automatically activated in services that use gRPC with mTLS
 * (Configuration-Client, Signer, Softtoken-Signer, Proxy, Op-Monitor).
 */
@Slf4j
@Readiness
@ApplicationScoped
@RequiredArgsConstructor
public class VaultPkiReadinessCheck implements HealthCheck {

    private static final String NAME = "OPENBAO_PKI_READINESS_CHECK";

    private final RpcProperties rpcProperties;

    private final Instance<VaultKeyClient> vaultKeyClient;

    @Override
    public HealthCheckResponse call() {

        // Check if mTLS is enabled
        if (!rpcProperties.useTls()) {
            return HealthCheckResponse.builder()
                    .name(NAME)
                    .up()
                    .withData(STATUS, "NOT_REQUIRED")
                    .withData(REASON, "mTLS is disabled")
                    .build();
        }

        // Check if PKI factory is available
        if (vaultKeyClient.isUnsatisfied()) {
            log.warn("OpenBao PKI readiness check failed: VaultKeyClient instance not present but mTLS is enabled");
            return HealthCheckResponse.builder()
                    .name(NAME)
                    .down()
                    .withData(ERROR, "VaultKeyClient instance not present but mTLS is enabled")
                    .build();
        }

        try {
            // Execute health check to verify actual connectivity to OpenBao PKI engine
            // This makes a real network call, not just a null check
            vaultKeyClient.get().healthCheck();
            return HealthCheckResponse.builder()
                    .name(NAME)
                    .up()
                    .build();
        } catch (Exception e) {
            log.warn("OpenBao PKI readiness check failed: {}", e.getMessage());
            return HealthCheckResponse.builder()
                    .name(NAME)
                    .down()
                    .withData(ERROR, e.getMessage())
                    .build();
        }
    }
}
