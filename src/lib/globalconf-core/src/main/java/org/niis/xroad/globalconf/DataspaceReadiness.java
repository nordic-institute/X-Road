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
package org.niis.xroad.globalconf;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The dataspace readiness rule: the own instance lists at least one credential issuer in its global configuration
 * and that global configuration is usable now. Shared by every component that gates dataspace behaviour on it.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DataspaceReadiness {

    /**
     * Returns {@code true} if the global configuration is usable and lists at least one credential issuer
     * for this instance.
     * Read on every call so the answer follows globalconf additions and removals.
     * Treats any globalconf failure (no anchor yet, no shared parameters yet) as "not ready".
     */
    public static boolean isReady(GlobalConfProvider globalConfProvider) {
        try {
            return !globalConfProvider.getCredentialIssuerDids(globalConfProvider.getInstanceIdentifier()).isEmpty()
                    && globalConfProvider.isValid();
        } catch (RuntimeException e) {
            log.debug("Data space: readiness check failed, treating the instance as not ready: {}",
                    e.getMessage());
            return false;
        }
    }
}
