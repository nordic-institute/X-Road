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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class DefaultCatalogCacheInvalidatorTest {

    @Mock
    private ServerConfProvider serverConfProvider;
    @Mock
    private StoreEnumerationCache<?> assetIndexCache;
    @Mock
    private StoreEnumerationCache<?> policyDefinitionCache;
    @Mock
    private StoreEnumerationCache<?> contractDefinitionCache;

    @Test
    void invalidateClearsServerConfCacheThenFansOutToEveryStoreCache() {
        var invalidator = new DefaultCatalogCacheInvalidator(
                serverConfProvider, List.of(assetIndexCache, policyDefinitionCache, contractDefinitionCache));

        invalidator.invalidate();

        var order = inOrder(serverConfProvider, assetIndexCache, policyDefinitionCache, contractDefinitionCache);
        order.verify(serverConfProvider).clearCache();
        order.verify(assetIndexCache).invalidate();
        order.verify(policyDefinitionCache).invalidate();
        order.verify(contractDefinitionCache).invalidate();
    }

    @Test
    void invalidateOnRealCacheDiscardsWhatWasCached() {
        var cache = new StoreEnumerationCache<String>(true, 3600, 1000, "test");
        var invalidator = new DefaultCatalogCacheInvalidator(serverConfProvider, List.of(cache));

        var firstLoad = cache.getEnumeration(() -> List.of("stale"));
        assertThat(firstLoad).containsExactly("stale");

        invalidator.invalidate();

        var reloaded = cache.getEnumeration(() -> List.of("fresh"));
        assertThat(reloaded).containsExactly("fresh");
    }

    @Test
    void invalidateIsNoOpWhenCachingIsDisabled() {
        var disabledCache = new StoreEnumerationCache<String>(false, 3600, 1000, "test");
        var invalidator = new DefaultCatalogCacheInvalidator(serverConfProvider, List.of(disabledCache));

        assertThatCode(invalidator::invalidate).doesNotThrowAnyException();
    }
}
