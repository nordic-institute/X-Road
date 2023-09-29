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
package ee.ria.xroad.proxy.clientproxy;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.proxy.clientproxy.FastestConnectionSelectingSSLSocketFactory.CacheKey;

import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Test FastestConnectionSelectingSSLSocketFactory
 */
public class FastestConnectionSelectingSSLSocketFactoryConnectionCacheTest {

    @Test
    public void testCacheKeyEqualsAndHashcode() throws URISyntaxException {
        URI[] a1 = new URI[] {
                new URI("https://localhost:5500"), new URI("http://10.10.10.10"), new URI("http://1.2.3.4")
        };

        final List<URI> tmp = Arrays.asList(a1.clone());
        Collections.rotate(tmp, 1);
        final URI[] a2 = tmp.toArray(new URI[0]);

        assert !Arrays.equals(a1, a2);

        URI[] a3 = new URI[] {
                new URI("https://localhost:80"), new URI("http://10.10.10.10"), new URI("http://1.2.3.4")
        };

        CacheKey k1 = new CacheKey(a1);
        CacheKey k2 = new CacheKey(a2);
        CacheKey k3 = new CacheKey(a3);

        // null
        assertFalse(k1.equals(null));

        // reflexive
        assertTrue(k1.equals(k1));
        assertTrue(k2.equals(k2));
        assertTrue(k3.equals(k3));

        // symmetric
        assertTrue(k1.equals(k2) == k2.equals(k1));
        assertTrue(k1.equals(k3) == k3.equals(k1));
        assertTrue(k2.equals(k3) == k3.equals(k2));

        // keys with same urls in different order are equal
        assertEquals(k1, k2);
        // consistent with equals
        assertEquals(k1.hashCode(), k2.hashCode());

        // different urls
        assertNotEquals(k1, k3);
        assertNotEquals(k2, k3);
    }

    /**
     * Cache implementation sanity check
     */
    @Test
    public void testExpiration() throws URISyntaxException {

        FakeTicker ticker = new FakeTicker();
        final int cachePeriod = SystemProperties.getClientProxyFastestConnectingSslUriCachePeriod();
        final Cache<CacheKey, URI> cache = CacheBuilder.newBuilder()
                .expireAfterWrite(cachePeriod, TimeUnit.SECONDS)
                .ticker(ticker)
                .build();

        CacheKey k = new CacheKey(new URI[] {new URI("https://localhost:5500")});
        URI v = new URI("https://localhost:5500");

        cache.put(k, v);

        assertEquals(v, cache.getIfPresent(k));
        ticker.advance(cachePeriod + 1, TimeUnit.SECONDS);
        assertNull(cache.getIfPresent(k));
    }

    static class FakeTicker extends Ticker {
        long ticks = 0;

        @Override
        public long read() {
            return ticks;
        }

        void advance(long t, TimeUnit unit) {
            ticks += unit.toNanos(t);
        }
    }

}
