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
package org.niis.xroad.signer.softtoken.sync;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory cache for synchronized software token keys.
 * <p>
 * This cache stores private keys and their metadata synchronized from the signer service.
 * The entire cache is atomically replaced on each synchronization cycle to ensure consistency.
 * <p>
 * <strong>Concurrency Model:</strong>
 * <ul>
 *   <li>Read operations (during signing): Lock-free via volatile map reference</li>
 *   <li>Write operations (during sync): Atomic map replacement, no read blocking</li>
 *   <li>Thread safety: ConcurrentHashMap handles concurrent reads</li>
 * </ul>
 */
@Slf4j
@ApplicationScoped
public class SoftwareTokenKeyCache {

    private volatile Map<String, CachedKeyInfo> keys = new ConcurrentHashMap<>();

    public void updateKeys(Map<String, CachedKeyInfo> newKeys) {
        this.keys = new ConcurrentHashMap<>(newKeys);
        log.info("Key cache updated with {} keys", newKeys.size());
    }

    public Optional<CachedKeyInfo> getKey(String keyId) {
        return Optional.ofNullable(keys.get(keyId));
    }
}
