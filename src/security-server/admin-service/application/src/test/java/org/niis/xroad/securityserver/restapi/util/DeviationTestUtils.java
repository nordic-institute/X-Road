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
package org.niis.xroad.securityserver.restapi.util;

import org.niis.xroad.common.core.exception.Deviation;
import org.niis.xroad.common.core.exception.DeviationAware;
import org.niis.xroad.common.core.exception.WarningDeviation;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Util class for working with Errors and Warnings in tests
 */
public final class DeviationTestUtils {

    private DeviationTestUtils() {
        // noop
    }

    /**
     * Finds warning with matching code, or returns null
     * @param code
     * @param warningDeviations
     * @return
     */
    public static WarningDeviation findWarning(String code, Collection<WarningDeviation> warningDeviations) {
        if (warningDeviations != null) {
            return warningDeviations.stream()
                    .filter(warning -> code.equals(warning.code()))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    /**
     * Finds warning with matching code, or returns null
     * @param code
     * @param deviationAware
     * @return
     */
    public static WarningDeviation findWarning(String code, DeviationAware deviationAware) {
        if (deviationAware != null) {
            return findWarning(code, deviationAware.getWarningDeviations());
        }
        return null;
    }

    /**
     * ErrorDeviation with null metadata
     * @param errorCode
     * @param deviationAware
     */
    public static void assertErrorWithoutMetadata(String errorCode, DeviationAware deviationAware) {
        assertEquals(errorCode, deviationAware.getErrorDeviation().code());
        assertTrue(deviationAware.getErrorDeviation().metadata().isEmpty());
    }

    /**
     * Warning without metadata
     * @param warningCode
     * @param deviationAware
     */
    public static void assertWarningWithoutMetadata(String warningCode, DeviationAware deviationAware) {
        List<String> warningCodes = deviationAware.getWarningDeviations().stream()
                .map(Deviation::code)
                .toList();
        deviationAware.getWarningDeviations()
                .forEach(warningDeviation -> assertTrue(warningDeviation.metadata().isEmpty()));
        assertTrue(warningCodes.contains(warningCode));
    }

    /**
     * one warning with one metadata item
     */
    public static void assertWarning(String warningCode, String warningMetadata, DeviationAware deviationAware) {
        assertNotNull(deviationAware.getWarningDeviations());
        assertEquals(1, deviationAware.getWarningDeviations().size());
        WarningDeviation warningDeviation = deviationAware.getWarningDeviations().iterator().next();
        assertEquals(warningCode, warningDeviation.code());
        assertNotNull(warningDeviation.metadata());
        assertEquals(Collections.singletonList(warningMetadata), warningDeviation.metadata());
    }

    /**
     * Assert warning exists with given metadata in correct order.
     * Other warnings may exist as well.
     * @param warningCode
     * @param deviationAware
     * @param warningMetadata
     */
    public static void assertWarning(String warningCode, DeviationAware deviationAware, String... warningMetadata) {
        assertNotNull(deviationAware.getWarningDeviations());
        WarningDeviation warningDeviation = findWarning(warningCode, deviationAware);
        assertNotNull(warningDeviation);
        assertEquals(warningCode, warningDeviation.code());
        assertNotNull(warningDeviation.metadata());
        List<String> metadatas = Arrays.asList(warningMetadata);
        assertEquals(metadatas, warningDeviation.metadata());
    }

    /**
     * ErrorDeviation with one metadata item
     * @param errorCode
     * @param metadata
     * @param deviationAware
     */
    public static void assertErrorWithMetadata(String errorCode, String metadata, DeviationAware deviationAware) {
        assertEquals(errorCode, deviationAware.getErrorDeviation().code());
        assertNotNull(deviationAware.getErrorDeviation().metadata());
        assertEquals(Collections.singletonList(metadata),
                deviationAware.getErrorDeviation().metadata());
    }

    /**
     * multiple error metadata items
     */
    public static void assertErrorWithMetadata(String errorCode, DeviationAware deviationAware, String... metadata) {
        assertEquals(errorCode, deviationAware.getErrorDeviation().code());
        assertNotNull(deviationAware.getErrorDeviation().metadata());
        List<String> metadatas = Arrays.asList(metadata);
        assertEquals(metadatas, deviationAware.getErrorDeviation().metadata());
    }
}
