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

import io.quarkus.vault.VaultKVSecretEngine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import static org.niis.xroad.common.healthcheck.HealthCheckConstants.ERROR;
import static org.niis.xroad.common.healthcheck.HealthCheckConstants.STATUS;

/**
 * Readiness check for OpenBao/Vault KV secret engine connectivity.
 * This check verifies that the KV secret engine is accessible.
 *
 * <p>This check is automatically activated in services that have
 * VaultKVSecretEngine configured (Signer, Proxy, Op-Monitor).
 * If no VaultKVSecretEngine is configured, the check reports UP
 * with status NOT_CONFIGURED.
 */
@Slf4j
@Readiness
@ApplicationScoped
@RequiredArgsConstructor
public class OpenBaoKvReadinessCheck implements HealthCheck {

    private static final String NAME = "OPENBAO_KV_READINESS_CHECK";

    private final Instance<VaultKVSecretEngine> kvSecretEngineInstance;

    @Override
    public HealthCheckResponse call() {
        if (kvSecretEngineInstance.isUnsatisfied()) {
            return HealthCheckResponse.builder()
                    .name(NAME)
                    .up()
                    .withData(STATUS, "NOT_CONFIGURED")
                    .build();
        }

        try {
            VaultKVSecretEngine kvSecretEngine = kvSecretEngineInstance.get();
            // Execute a simple operation to verify connectivity
            kvSecretEngine.listSecrets("");
            return HealthCheckResponse.up(NAME);
        } catch (Exception e) {
            log.warn("OpenBao KV readiness check failed: {}", e.getMessage());
            return HealthCheckResponse.builder()
                    .name(NAME)
                    .down()
                    .withData(ERROR, e.getMessage())
                    .build();
        }
    }
}
