/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import ee.ria.xroad.proxy.clientproxy.FastestConnectionSelectingSSLSocketFactory.CacheKey;

import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

/**
 * Test FastestConnectionSelectingSSLSocketFactory
 */
public class FastestConnectionSelectingSSLSocketFactoryCacheKeyTest {

    @Test
    public void testCacheKey() throws URISyntaxException {
        URI[] a1 = new URI[] {
                new URI("https://localhost:5500"), new URI("http://10.10.10.10"), new URI("http://1.2.3.4")
        };
        final List<URI> tmp = Arrays.asList(a1.clone());
        Collections.rotate(tmp, 1);
        final URI[] a2 = tmp.toArray(new URI[0]);
        URI[] a3 = new URI[] {
                new URI("https://localhost:80"), new URI("http://10.10.10.10"), new URI("http://1.2.3.4")
        };

        CacheKey k1 = new CacheKey(a1);
        CacheKey k2 = new CacheKey(a2);
        CacheKey k3 = new CacheKey(a3);

        assertFalse(Arrays.equals(a1, a2));
        assertEquals(k1.hashCode(), k2.hashCode());

        assertEquals(k2, k2);
        assertEquals(k1, k1);

        assertEquals(k1, k2);
        assertEquals(k2, k1);

        assertNotEquals(k1, k3);
        assertNotEquals(k3, k2);
    }

}
