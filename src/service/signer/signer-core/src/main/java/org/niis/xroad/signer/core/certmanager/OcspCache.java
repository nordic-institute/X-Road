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
package org.niis.xroad.signer.core.certmanager;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifier;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierOptions;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds OCSP response per key. When getting the response, it is checked
 * if the response is expired at the specified date, and if it is, the
 * response is removed from the cache and null is returned.
 */
@Slf4j
@RequiredArgsConstructor
public class OcspCache {
    private final GlobalConfProvider globalConfProvider;
    private final OcspVerifierFactory ocspVerifierFactory;

    protected final Map<String, OCSPResp> cache = new ConcurrentHashMap<>();

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
     *
     * @param key   the key
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

    protected boolean isExpired(OCSPResp response, Date atDate) throws OCSPException {
        OcspVerifier verifier = ocspVerifierFactory.create(globalConfProvider,
                new OcspVerifierOptions(globalConfProvider.getGlobalConfExtensions().shouldVerifyOcspNextUpdate()));
        return verifier.isExpired(response, atDate);
    }
}
