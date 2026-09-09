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
package org.niis.xroad.edc.extension.catalog;

import org.niis.xroad.serverconf.ServerConfProvider;

import java.util.List;

/**
 * Clears the underlying {@link ServerConfProvider}'s own cache, then fans
 * {@link StoreEnumerationCache#invalidate()} out across every catalog store's cache instance.
 *
 * <p>Flushing only the store caches would leave a caching {@code ServerConfProvider} (used whenever
 * {@code cachePeriod > 0}) serving stale reads underneath a freshly-rebuilt store cache, so the
 * serverconf cache is cleared first — {@link ServerConfProvider#clearCache()} defaults to a no-op,
 * so this is harmless against a non-caching provider too.</p>
 */
final class DefaultCatalogCacheInvalidator implements CatalogCacheInvalidator {

    private final ServerConfProvider serverConfProvider;
    private final List<StoreEnumerationCache<?>> caches;

    DefaultCatalogCacheInvalidator(ServerConfProvider serverConfProvider, List<StoreEnumerationCache<?>> caches) {
        this.serverConfProvider = serverConfProvider;
        this.caches = List.copyOf(caches);
    }

    @Override
    public void invalidate() {
        serverConfProvider.clearCache();
        caches.forEach(StoreEnumerationCache::invalidate);
    }
}
