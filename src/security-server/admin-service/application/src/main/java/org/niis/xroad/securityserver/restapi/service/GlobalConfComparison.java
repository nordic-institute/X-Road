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

import java.util.function.Predicate;

/**
 * How the value globalconf announces compares with the value this Security Server holds. Pure: no clock, no storage.
 */
public enum GlobalConfComparison {
    /** Globalconf announces no value. */
    ABSENT,
    /** Globalconf announces the held value. */
    EQUAL,
    /** Globalconf announces another value that is still valid. */
    DIFFERENT,
    /** Globalconf announces another value that no longer fits this Security Server. */
    STALE;

    /**
     * Compares the held value with the carried one. A stale carried value is {@link #STALE} even though it differs;
     * a carried value equal to the held one is {@link #EQUAL} whatever the staleness test says.
     *
     * @param held           the value this Security Server holds
     * @param carried        the value globalconf announces, {@code null} when it announces none
     * @param carriedIsStale tells whether a carried value no longer fits this Security Server
     * @return the comparison result
     */
    public static GlobalConfComparison classify(String held, @Nullable String carried,
            Predicate<String> carriedIsStale) {
        if (carried == null) {
            return ABSENT;
        }
        if (held.equals(carried)) {
            return EQUAL;
        }
        return carriedIsStale.test(carried) ? STALE : DIFFERENT;
    }
}
