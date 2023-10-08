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
package org.niis.xroad.securityserver.restapi.converter;

import ee.ria.xroad.common.DiagnosticsErrorCodes;
import ee.ria.xroad.common.DiagnosticsStatus;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.securityserver.restapi.openapi.model.ConfigurationStatus;
import org.niis.xroad.securityserver.restapi.openapi.model.DiagnosticStatusClass;
import org.niis.xroad.securityserver.restapi.openapi.model.GlobalConfDiagnostics;

import java.time.OffsetDateTime;

import static org.junit.Assert.assertEquals;

/**
 * Test GlobalConfDiagnosticConverter
 */
public class GlobalConfDiagnosticConverterTest {

    private GlobalConfDiagnosticConverter globalConfDiagnosticConverter;
    private static final OffsetDateTime PREVIOUS_UPDATE = OffsetDateTime.parse("2020-03-16T10:15:40.703Z");
    private static final OffsetDateTime NEXT_UPDATE = OffsetDateTime.parse("2020-03-16T10:16:40.703Z");

    @Before
    public void setup() {
        globalConfDiagnosticConverter = new GlobalConfDiagnosticConverter();
    }

    @Test
    public void convertSingleGlobalConfDiagnostics() {
        GlobalConfDiagnostics globalConfDiagnostics = globalConfDiagnosticConverter.convert(new DiagnosticsStatus(
                DiagnosticsErrorCodes.RETURN_SUCCESS, PREVIOUS_UPDATE, NEXT_UPDATE));

        assertEquals(ConfigurationStatus.SUCCESS, globalConfDiagnostics.getStatusCode());
        assertEquals(DiagnosticStatusClass.OK, globalConfDiagnostics.getStatusClass());
        assertEquals(PREVIOUS_UPDATE, globalConfDiagnostics.getPrevUpdateAt());
        assertEquals(NEXT_UPDATE, globalConfDiagnostics.getNextUpdateAt());
    }
}
