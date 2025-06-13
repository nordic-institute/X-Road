package org.niis.xroad.signer.core.healthcheck;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.niis.xroad.signer.core.tokenmanager.TokenRegistry;

/**
 * TODO this is a sample readiness check for the token registry.
 * Technically it should not trigger as it is loaded from post-construct.
 */
@Slf4j
@Readiness
@ApplicationScoped
@RequiredArgsConstructor
public class TokenRegistryReadinessCheck implements HealthCheck {
    private static final String NAME = "TOKEN_REGISTRY_READINESS_CHECK";

    private final TokenRegistry tokenRegistry;

    @Override
    public HealthCheckResponse call() {
        if (tokenRegistry.isInitialized()) {
            return HealthCheckResponse.up(NAME);
        }
        log.warn("Token registry is not initialized, returning down status for readiness check");
        return HealthCheckResponse.down(NAME);
    }
}
