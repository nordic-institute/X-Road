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
package ee.ria.xroad.common.ocsp;

import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.globalconfextension.GlobalConfExtensions;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.OCSPResp;

import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds OCSP response per key. When getting the response, it is checked
 * if the response is expired at the specified date, and if it is, the
 * response is removed from the cache and null is returned.
 */
@Slf4j
public class OcspCache {

    protected final Map<String, OCSPResp> cache = new ConcurrentHashMap<>();

    /**
     * @param key the key
     * @param atDate the date
     * @return the OCSP response or null if the response is not found or is
     * expired at the specified date
     */
    public OCSPResp get(Object key, Date atDate) {
        return getResponse(key, atDate);
    }

    /**
     * @param key the key
     * @return the OCSP response or null if the response is not found or is
     * expired at the current date
     */
    public OCSPResp get(Object key) {
        return getResponse(key, new Date());
    }

    /**
     * Associates a key with the OCSP response.
     * @param key the key
     * @param value the OCSP response
     * @return the OCSP response
     */
    public OCSPResp put(String key, OCSPResp value) {
        log.trace("Setting OCSP response for '{}'", key);
        return cache.put(key, value);
    }

    /**
     * Removes all OCSP responses from the cache.
     */
    public void clear() {
        cache.clear();
    }

    /**
     * @return a Set view of the mappings contained in this map.
     */
    public Set<Entry<String, OCSPResp>> entrySet() {
        return cache.entrySet();
    }

    protected OCSPResp getResponse(Object key, Date atDate) {
        log.trace("Retrieving OCSP response for certificate '{}' at {}", key,
                atDate);

        OCSPResp cachedResponse = cache.get(key);
        try {
            if (cachedResponse != null && isExpired(cachedResponse, atDate)) {
                log.trace("Cached OCSP response for certificate "
                        + "'{}' has expired", key);
                cache.remove(key);
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to check if OCSP response is expired", e);
            cache.remove(key);
            return null;
        }

        return cachedResponse;
    }

    protected static boolean isExpired(OCSPResp response, Date atDate)
            throws Exception {
        OcspVerifier verifier = new OcspVerifier(GlobalConf.getOcspFreshnessSeconds(true),
                new OcspVerifierOptions(GlobalConfExtensions.getInstance().shouldVerifyOcspNextUpdate()));
        return verifier.isExpired(response, atDate);
    }
}
