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
package org.niis.xroad.securityserver.restapi.service;

import jakarta.annotation.Nullable;
import org.niis.xroad.globalconf.model.ServerSystemValues;

import java.util.function.Predicate;

/**
 * How the SYSTEM values globalconf carries compare with the SYSTEM DID this Security Server holds and the DSP base URL
 * built for it. Pure: no clock, no storage.
 *
 * @param systemDid  the comparison of the SYSTEM DID
 * @param dspBaseUrl the comparison of the DSP base URL
 */
public record SystemValuesComparison(GlobalConfComparison systemDid, GlobalConfComparison dspBaseUrl) {

    /**
     * Compares each carried value with the one held. Staleness is judged for the pair: when either carried value no
     * longer fits this Security Server, a value that differs is {@link GlobalConfComparison#STALE}. A DSP base URL
     * that cannot be built is stale whenever globalconf carries values.
     *
     * @param heldSystemDid   the SYSTEM DID this Security Server holds
     * @param builtDspBaseUrl the DSP base URL built for this server, {@code null} while it cannot be built
     * @param carried         the SYSTEM values globalconf carries, {@code null} when it carries none
     * @param pairIsStale     tells whether the carried values no longer fit this Security Server
     * @return the comparison of both values
     */
    public static SystemValuesComparison of(String heldSystemDid, @Nullable String builtDspBaseUrl,
            @Nullable ServerSystemValues carried, Predicate<ServerSystemValues> pairIsStale) {
        if (carried == null) {
            return new SystemValuesComparison(GlobalConfComparison.ABSENT, GlobalConfComparison.ABSENT);
        }
        Predicate<String> stale = _ -> pairIsStale.test(carried);
        var systemDid = GlobalConfComparison.classify(heldSystemDid, carried.systemDid(), stale);
        var dspBaseUrl = builtDspBaseUrl == null
                ? GlobalConfComparison.STALE
                : GlobalConfComparison.classify(builtDspBaseUrl, carried.dspBaseUrl(), stale);
        return new SystemValuesComparison(systemDid, dspBaseUrl);
    }

    /**
     * @return whether globalconf carries both values as held
     */
    public boolean isEqual() {
        return systemDid == GlobalConfComparison.EQUAL && dspBaseUrl == GlobalConfComparison.EQUAL;
    }
}
