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

package org.niis.xroad.edc.extension.edr.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Getter;
import lombok.Setter;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class AssetInProgressRegistry {

    @SuppressWarnings("checkstyle:magicnumber")
    private final Cache<String, AssetInProgress> cache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    public Optional<AssetInProgress> get(String contractNegotiationId) {
        return Optional.ofNullable(cache.getIfPresent(contractNegotiationId));
    }

    public Optional<AssetInProgress> getByTransferProcessId(String transferProcessId) {
        return cache.asMap().values().stream()
                .filter(assetInProgress -> transferProcessId.equals(assetInProgress.getTransferProcessId()))
                .findFirst();
    }

    public CompletableFuture<Void> register(String negotiationId, String clientId, String serviceId) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        cache.put(negotiationId, new AssetInProgress(future, clientId, serviceId));
        return future;
    }

    @Getter
    public static class AssetInProgress {
        private final CompletableFuture<Void> future;
        private final String clientId;
        private final String serviceId;
        @Setter
        private String transferProcessId;

        public AssetInProgress(CompletableFuture<Void> future, String clientId, String serviceId) {
            this.future = future;
            this.clientId = clientId;
            this.serviceId = serviceId;
        }

        public void complete() {
            future.complete(null);
        }
    }

}
