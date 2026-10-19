/*
 * The MIT License
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

package org.niis.xroad.proxy.core.configuration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.agreementtoken.key.AgreementTokenKeyInfo;
import org.niis.xroad.common.agreementtoken.key.AgreementTokenKeyProvider;
import org.niis.xroad.common.agreementtoken.key.VaultAgreementTokenKeyProvider;
import org.niis.xroad.common.vault.VaultClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.random.RandomGenerator;

/**
 * Owns the agreement-token signing key material for this proxy process: a single
 * {@link VaultAgreementTokenKeyProvider} shared by the data-plane minter and the server-proxy verifier. A
 * freshly rotated key starts signing only after {@link ProxyAgreementTokenProperties#keyRefreshInterval()} has
 * passed, so every replica's cache has had a chance to pick it up before it is relied on.
 * <p>
 * Construction never throws — an unreachable vault at startup, or at any later retry, leaves
 * {@link #provider()} empty rather than failing the proxy's own startup or a mint/verify call. A proxy-owned
 * scheduled job (see {@code AgreementTokenKeyRefreshJob}) drives {@link #retryOrRefresh()} off the request
 * path: once construction has succeeded, it calls {@link AgreementTokenKeyProvider#refresh()}; until then, it
 * keeps retrying construction.
 * <p>
 * When DSP is enabled and {@link ProxyAgreementTokenProperties#maxKeyAge()} is non-zero, each successful
 * refresh is followed by a check of the newest key's age (never the signing key's, which lags the newest by the
 * activation delay). A due rotation waits for a random per-replica delay of up to twice the refresh interval,
 * so replicas sharing one secret store rarely rotate together, and is re-checked against the freshly refreshed
 * snapshot before it runs.
 */
@Slf4j
@ApplicationScoped
public class AgreementTokenKeyMaterial {

    private final VaultClient vaultClient;
    private final ProxyAgreementTokenProperties properties;
    private final ProxyProperties proxyProperties;
    private final Clock clock;
    private final RandomGenerator random;
    private final AtomicReference<AgreementTokenKeyProvider> provider = new AtomicReference<>();
    private Instant rotationDeadline;

    @Inject
    public AgreementTokenKeyMaterial(VaultClient vaultClient, ProxyAgreementTokenProperties properties,
                                     ProxyProperties proxyProperties) {
        this(vaultClient, properties, proxyProperties, Clock.systemUTC(), RandomGenerator.getDefault());
    }

    AgreementTokenKeyMaterial(VaultClient vaultClient, ProxyAgreementTokenProperties properties,
                              ProxyProperties proxyProperties, Clock clock, RandomGenerator random) {
        this.vaultClient = vaultClient;
        this.properties = properties;
        this.proxyProperties = proxyProperties;
        this.clock = clock;
        this.random = random;
        tryBuild();
    }

    /**
     * @return the key provider, or empty when the vault-backed material has never been successfully built yet
     */
    public Optional<AgreementTokenKeyProvider> provider() {
        return Optional.ofNullable(provider.get());
    }

    /**
     * Retries construction if it has not yet succeeded, otherwise refreshes the existing provider's cached
     * snapshot and rotates the signing key when it is due. Never throws: a failed refresh or rotation is logged
     * at warn and the last good snapshot stays in use.
     */
    public synchronized void retryOrRefresh() {
        var current = provider.get();
        if (current == null) {
            tryBuild();
            return;
        }
        try {
            current.refresh();
        } catch (Exception e) {
            log.warn("Failed to refresh agreement-token signing key material; keeping the last known good snapshot", e);
            return;
        }
        rotateIfDue(current);
    }

    private void rotateIfDue(AgreementTokenKeyProvider current) {
        var maxKeyAge = properties.maxKeyAge();
        if (!proxyProperties.dspEnabled() || maxKeyAge.isZero()) {
            rotationDeadline = null;
            return;
        }
        var keys = current.listKeys();
        if (!isNewestKeyDue(keys, maxKeyAge)) {
            rotationDeadline = null;
            return;
        }
        var now = clock.instant();
        if (rotationDeadline == null) {
            rotationDeadline = now.plus(randomRotationDelay());
        }
        if (now.isBefore(rotationDeadline)) {
            return;
        }
        rotate(current, keys.getLast().keyId());
    }

    private boolean isNewestKeyDue(List<AgreementTokenKeyInfo> keys, Duration maxKeyAge) {
        if (keys.isEmpty()) {
            return false;
        }
        return keys.getLast().issueTime()
                .map(issueTime -> Duration.between(issueTime, clock.instant()).compareTo(maxKeyAge) > 0)
                .orElse(true);
    }

    private Duration randomRotationDelay() {
        var bound = 2 * properties.keyRefreshInterval().toMillis() + 1;
        return Duration.ofMillis(random.nextLong(bound));
    }

    private void rotate(AgreementTokenKeyProvider current, String newestKeyId) {
        try {
            var rotated = current.rotate();
            rotationDeadline = null;
            log.info("Rotated agreement-token signing key automatically: newest key was '{}', new key is '{}'",
                    newestKeyId, rotated.keyId());
        } catch (Exception e) {
            log.warn("Failed to rotate agreement-token signing key automatically; will retry on the next refresh", e);
        }
    }

    private void tryBuild() {
        try {
            provider.set(new VaultAgreementTokenKeyProvider(vaultClient, properties.keyRefreshInterval(), clock));
            log.info("Agreement-token signing key material initialized");
        } catch (Exception e) {
            log.warn("Agreement-token signing key material unavailable; agreement tokens will not be minted or "
                    + "verified until the secret store is reachable", e);
        }
    }
}
