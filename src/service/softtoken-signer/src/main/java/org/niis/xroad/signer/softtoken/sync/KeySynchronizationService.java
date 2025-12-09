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

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.softtoken.config.SofttokenSignerKeysProperties;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Service responsible for synchronizing software token keys from the signer service.
 * <p>
 * This service periodically fetches all software token keys (including their PKCS#12 keystores)
 * from the signer service and updates the local cache. The synchronization happens on startup
 * and then periodically based on the configured interval.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class KeySynchronizationService {

    private final SignerRpcClient signerRpcClient;
    private final SoftwareTokenKeyCache keyCache;
    private final SofttokenSignerKeysProperties properties;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());

    /**
     * Performs initial synchronization on service startup.
     * Ensures that keys are available before the service starts accepting sign requests.
     */
    @PostConstruct
    public void initialize() {
        log.info("Performing initial key synchronization on startup");
        synchronizeKeys();
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
     *   <li>Atomically updates the key cache</li>
     * </ul>
     */
    public void synchronizeKeys() {
        final Instant syncStart = Instant.now();

        log.info("Starting software token key synchronization");

        try {
            final var softwareTokenKeys = signerRpcClient.listSoftwareTokenKeys();
            final Map<String, CachedKeyInfo> newKeys = new HashMap<>();

            for (var key : softwareTokenKeys) {
                try (InputStream privateKeyInputstream = new ByteArrayInputStream(key.privateKey())) {
                    var privateKey = CryptoUtils.getPrivateKey(privateKeyInputstream);
                    final CachedKeyInfo cachedKey = new CachedKeyInfo(
                            key.keyId(),
                            privateKey,
                            key.tokenActive(),
                            key.keyAvailable(),
                            key.keyLabel(),
                            key.signMechanism(),
                            syncStart
                    );

                    newKeys.put(key.keyId(), cachedKey);
                    log.debug("Synchronized key: {} (label: {}, token active: {}, key available: {})",
                            key.keyId(), key.keyLabel(), cachedKey.tokenActive(), cachedKey.keyAvailable());
                }
            }

            keyCache.updateKeys(newKeys);
            log.info("Successfully synchronized {} software token keys", newKeys.size());

        } catch (Exception e) {
            log.error("Software token key synchronization failed: {}", e.getMessage(), e);
            throw XrdRuntimeException.systemInternalError("Key synchronization failed", e);
        } finally {
            scheduler.scheduleAtFixedRate(this::synchronizeKeys, properties.refreshRate(), properties.refreshRate(), SECONDS);
        }
    }
}
