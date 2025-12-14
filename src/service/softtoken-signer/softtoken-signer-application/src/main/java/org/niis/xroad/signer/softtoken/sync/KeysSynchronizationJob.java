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
package org.niis.xroad.signer.softtoken.sync;

import ee.ria.xroad.common.util.CryptoUtils;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Startup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.softtoken.config.SoftTokenSignerKeysProperties;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Service responsible for synchronizing software token keys from the signer service.
 * <p>
 * This service periodically fetches all software token private keys
 * from the signer service and updates the local cache. The synchronization happens on startup
 * and then periodically based on the configured interval.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class KeysSynchronizationJob {

    private final SignerRpcClient signerRpcClient;
    private final SoftwareTokenKeyCache keyCache;
    private final SoftTokenSignerKeysProperties properties;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());

    public void startJob(@Observes Startup init) {
        log.info("Performing initial keys' synchronization on startup");
        synchronizeKeys();

        log.info("Scheduling keys' synchronization at fixed rate: {}", properties.refreshRateInSeconds());
        scheduler.scheduleAtFixedRate(this::synchronizeKeys, properties.refreshRateInSeconds(), properties.refreshRateInSeconds(), SECONDS);
    }

    @PreDestroy
    public void destroy() {
        scheduler.shutdown();
    }

    /**
     * Synchronizes software token keys from the signer service.
     * <p>
     * This method:
     * <ul>
     *   <li>Fetches all software token keys from the signer via gRPC</li>
     *   <li>Creates cached key information with metadata</li>
     *   <li>Only updates keys that have changed (preserving lastSynced for unchanged keys)</li>
     *   <li>Adds new keys that don't exist in the cache</li>
     *   <li>Removes keys that are no longer present</li>
     *   <li>Atomically updates the key cache</li>
     * </ul>
     */
    public void synchronizeKeys() {

        log.info("Starting software token key synchronization");

        try {
            final var softwareTokenKeys = signerRpcClient.listSoftwareTokenKeys();
            final Set<String> currentKeyIds = new HashSet<>();

            int addedCount = 0;
            int updatedCount = 0;
            int unchangedCount = 0;

            for (var key : softwareTokenKeys) {
                var privateKey = CryptoUtils.getPrivateKeyFromPKCS8(key.privateKey());
                final CachedKeyInfo newKey = new CachedKeyInfo(
                        key.keyId(),
                        privateKey,
                        key.tokenActive(),
                        key.keyAvailable(),
                        key.keyLabel(),
                        key.signMechanism()
                );

                currentKeyIds.add(key.keyId());

                Optional<CachedKeyInfo> existingKey = keyCache.getKey(newKey.keyId());
                if (existingKey.isEmpty()) {
                    log.debug("Adding software token key (id: {}, label: {}) to cache", newKey.keyId(), newKey.keyLabel());
                    keyCache.addKey(newKey);
                    addedCount++;
                } else {
                    if (existingKey.get().equals(newKey)) {
                        log.info("Software token key (id: {}, label: {}) unchanged in cache", newKey.keyId(), newKey.keyLabel());
                        unchangedCount++;
                    } else {
                        log.debug("Updating software token key (id: {}, label: {}) as it has changed", newKey.keyId(), newKey.keyLabel());
                        keyCache.addKey(newKey);
                        updatedCount++;
                    }
                }
            }

            // Remove keys that are no longer present in the signer response
            final Set<String> cachedKeyIds = keyCache.getAllKeyIds();
            final Set<String> keysToRemove = new HashSet<>(cachedKeyIds);
            keysToRemove.removeAll(currentKeyIds);

            int removedCount = 0;
            for (String keyId : keysToRemove) {
                log.debug("Removing software token key (id: {}) as it is no longer present", keyId);
                keyCache.removeKey(keyId);
                removedCount++;
            }

            log.info("Successfully synchronized software token keys (added: {}, updated: {}, unchanged: {}, removed: {})",
                    addedCount, updatedCount, unchangedCount, removedCount);

        } catch (Exception e) {
            log.error("Software token key synchronization failed: {}", e.getMessage(), e);
            throw XrdRuntimeException.systemInternalError("Key synchronization failed", e);
        }
    }
}
