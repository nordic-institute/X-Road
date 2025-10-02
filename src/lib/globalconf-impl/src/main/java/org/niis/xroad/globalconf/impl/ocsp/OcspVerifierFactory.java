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

package org.niis.xroad.globalconf.impl.ocsp;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.SingleResp;
import org.niis.xroad.globalconf.GlobalConfProvider;

import java.util.concurrent.TimeUnit;

@Slf4j
public class OcspVerifierFactory {

    private static final int RESPONSE_VALIDITY_CACHE_MAX_SIZE = 1000;
    private static final int OCSP_VERIFIER_CACHE_PERIOD_MAX = 180;

    private final Cache<String, SingleResp> responseValidityCache;

    public OcspVerifier create(GlobalConfProvider globalConfProvider) {
        return new OcspVerifier(globalConfProvider, responseValidityCache);
    }

    public OcspVerifier create(GlobalConfProvider globalConfProvider, OcspVerifierOptions options) {
        return new OcspVerifier(globalConfProvider, options, responseValidityCache);
    }

    public OcspVerifierFactory(OcspVerifierProperties ocspVerifierProperties) {
        this(ocspVerifierProperties.cachePeriod());
    }

    public OcspVerifierFactory() {
        this(Integer.parseInt(OcspVerifierProperties.DEFAULT_OCSP_VERIFIER_CACHE_PERIOD));
    }

    private OcspVerifierFactory(int ocspVerifierCachePeriod) {
        int cachePeriod = ocspVerifierCachePeriod;

        if (cachePeriod > OCSP_VERIFIER_CACHE_PERIOD_MAX) {
            log.warn("OCSP verifier cache period too high, capping to {}", OCSP_VERIFIER_CACHE_PERIOD_MAX);
            cachePeriod = OCSP_VERIFIER_CACHE_PERIOD_MAX;
        }

        this.responseValidityCache = CacheBuilder.newBuilder()
                .expireAfterWrite(cachePeriod, TimeUnit.SECONDS)
                .maximumSize(RESPONSE_VALIDITY_CACHE_MAX_SIZE)
                .build();
    }

}
