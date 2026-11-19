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
package org.niis.xroad.opmonitor.application.healthcheck;

import io.quarkus.vault.client.VaultClient;
import io.quarkus.vault.client.api.sys.health.VaultHealthStatus;
import io.smallrye.health.api.AsyncHealthCheck;
import io.smallrye.mutiny.Uni;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.health.HealthCheckResponse;

import java.util.function.Supplier;

/**
 * Secret store (OpenBao) readiness check with the name, result and reasons of the Quarkus Vault extension check.
 * The client is resolved on each call, so a client that cannot be created gives DOWN instead of a failed request.
 * The check is asynchronous because programmatically registered checks run on the event loop that also completes
 * the secret store response.
 */
@RequiredArgsConstructor
public class OpMonitorSecretStoreReadinessCheck implements AsyncHealthCheck {

    static final String NAME = "Vault connection health check";
    static final String REASON = "reason";

    private final Supplier<VaultClient> vaultClient;
    private final boolean standByOk;
    private final boolean performanceStandByOk;

    @Override
    public Uni<HealthCheckResponse> call() {
        return Uni.createFrom()
                .completionStage(() -> vaultClient.get().sys().health().status(standByOk, performanceStandByOk))
                .map(OpMonitorSecretStoreReadinessCheck::response)
                .onFailure().recoverWithItem(e -> HealthCheckResponse.named(NAME).down()
                        .withData(REASON, e.getMessage()).build());
    }

    private static HealthCheckResponse response(VaultHealthStatus status) {
        return status == VaultHealthStatus.INITIALIZED_UNSEALED_ACTIVE
                ? HealthCheckResponse.named(NAME).up().build()
                : HealthCheckResponse.named(NAME).down().withData(REASON, reason(status)).build();
    }

    private static String reason(VaultHealthStatus status) {
        return switch (status) {
            case UNSEALED_STANDBY -> "Unsealed and Standby";
            case RECOVERY_REPLICATION_SECONDARY_ACTIVE -> "Disaster recovery mode replication secondary and active";
            case PERFORMANCE_STANDBY -> "Performance standby";
            case NOT_INITIALIZED -> "Not initialized";
            case SEALED -> "Sealed";
            default -> status.name();
        };
    }
}
