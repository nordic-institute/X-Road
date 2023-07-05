/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.admin.core.util;

import org.niis.xroad.restapi.exceptions.Deviation;
import org.niis.xroad.restapi.exceptions.DeviationAware;
import org.niis.xroad.restapi.exceptions.WarningDeviation;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Util class for working with Errors and Warnings in tests
 */
public final class DeviationTestUtils {

    private DeviationTestUtils() {
        // noop
    }

    /**
     * Finds warning with matching code, or returns null
     * @param code error code for which  warning is searched
     * @param warningDeviations collection of deviations
     * @return matching code or null
     */
    public static WarningDeviation findWarning(String code, Collection<WarningDeviation> warningDeviations) {
        if (warningDeviations != null) {
            return warningDeviations.stream()
                    .filter(warning -> code.equals(warning.getCode()))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    /**
     * Finds warning with matching code, or returns null
     * @param code  error code for which warning is searched
     * @param deviationAware warnings are contained here
     * @return matching code or null
     */
    public static WarningDeviation findWarning(String code, DeviationAware deviationAware) {
        if (deviationAware != null) {
            return findWarning(code, deviationAware.getWarningDeviations());
        }
        return null;
    }

    /**
     * ErrorDeviation with null metadata
     * @param errorCode error code that must match with deviationAware
     * @param deviationAware shall not contain any metadata in errorDeviation field
     */
    public static void assertErrorWithoutMetadata(String errorCode, DeviationAware deviationAware) {
        assertEquals(errorCode, deviationAware.getErrorDeviation().getCode());
        assertNull(deviationAware.getErrorDeviation().getMetadata());
    }

    /**
     * Warning without metadata
     * @param warningCode error code with which one error code of one warning deviation must match
     * @param deviationAware which is tested
     */
    public static void assertWarningWithoutMetadata(String warningCode, DeviationAware deviationAware) {
        List<String> warningCodes = deviationAware.getWarningDeviations().stream()
                .map(Deviation::getCode)
                .collect(toList());
        deviationAware.getWarningDeviations()
                .forEach(warningDeviation -> assertNull(warningDeviation.getMetadata()));
        assertTrue(warningCodes.contains(warningCode));
    }

    /**
     * one warning with one metadata item
     */
    public static void assertWarning(String warningCode, String warningMetadata, DeviationAware deviationAware) {
        assertNotNull(deviationAware.getWarningDeviations());
        assertEquals(1, deviationAware.getWarningDeviations().size());
        WarningDeviation warningDeviation = deviationAware.getWarningDeviations().iterator().next();
        assertEquals(warningCode, warningDeviation.getCode());
        assertNotNull(warningDeviation.getMetadata());
        assertEquals(Collections.singletonList(warningMetadata), warningDeviation.getMetadata());
    }

    /**
     * Assert warning exists with given metadata in correct order.
     * Other warnings may exist as well.
     * @param warningCode deviationAware parameter must contain warning with this code
     * @param deviationAware deviation to test
     * @param warningMetadata deviation must have all these in it's warnings
     */
    public static void assertWarning(String warningCode, DeviationAware deviationAware, String... warningMetadata) {
        assertNotNull(deviationAware.getWarningDeviations());
        WarningDeviation warningDeviation = findWarning(warningCode, deviationAware);
        assertNotNull(warningDeviation);
        assertEquals(warningCode, warningDeviation.getCode());
        assertNotNull(warningDeviation.getMetadata());
        List<String> metadatas = Arrays.asList(warningMetadata);
        assertEquals(metadatas, warningDeviation.getMetadata());
    }

    /**
     * ErrorDeviation with one metadata item
     * @param errorCode deviation must have errorDeviation with this code
     * @param metadata errorDeviation of deviation must have only this metadata
     * @param deviationAware this deviation must have errorDeviation metadata.
     */
    public static void assertErrorWithMetadata(String errorCode, String metadata, DeviationAware deviationAware) {
        assertEquals(errorCode, deviationAware.getErrorDeviation().getCode());
        assertNotNull(deviationAware.getErrorDeviation().getMetadata());
        assertEquals(Collections.singletonList(metadata),
                deviationAware.getErrorDeviation().getMetadata());
    }

    /**
     * multiple error metadata items
     */
    public static void assertErrorWithMetadata(String errorCode, DeviationAware deviationAware, String... metadata) {
        assertEquals(errorCode, deviationAware.getErrorDeviation().getCode());
        assertNotNull(deviationAware.getErrorDeviation().getMetadata());
        List<String> metadatas = Arrays.asList(metadata);
        assertEquals(metadatas, deviationAware.getErrorDeviation().getMetadata());
    }
}
