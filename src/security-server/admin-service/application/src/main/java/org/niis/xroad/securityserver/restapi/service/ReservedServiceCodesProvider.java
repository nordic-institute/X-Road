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
package org.niis.xroad.securityserver.restapi.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Provides access to the list of reserved service codes defined via configuration.
 */
@Component
public class ReservedServiceCodesProvider {

    private final Set<String> reservedCodes;

    /**
     * Loads the reserved service codes from configuration (proxy-ui-api.reserved-service-codes)
     * @param reservedCodesList List of reserved codes from application configuration
     */
    public ReservedServiceCodesProvider(
            @Value("${xroad.proxy-ui-api.reserved-service-codes:}") List<String> reservedCodesList) {
        if (reservedCodesList != null) {
            this.reservedCodes = new HashSet<>(reservedCodesList);
        } else {
            this.reservedCodes = Collections.emptySet();
        }
    }

    /**
     * Check if the given service code is reserved.
     * @param code Service code to check
     * @return true if code is reserved, false otherwise
     */
    public boolean isReserved(String code) {
        return reservedCodes.contains(code);
    }

    /**
     * Returns an unmodifiable view of the reserved codes.
     * @return reserved service codes
     */
    public Set<String> getReservedCodes() {
        return Collections.unmodifiableSet(reservedCodes);
    }
}
