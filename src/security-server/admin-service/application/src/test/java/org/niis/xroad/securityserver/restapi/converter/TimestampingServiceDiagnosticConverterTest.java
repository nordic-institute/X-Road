/**
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
import org.niis.xroad.securityserver.restapi.openapi.model.DiagnosticStatusClass;
import org.niis.xroad.securityserver.restapi.openapi.model.TimestampingServiceDiagnostics;
import org.niis.xroad.securityserver.restapi.openapi.model.TimestampingStatus;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * Test TimestampingServiceDiagnosticConverter
 */
public class TimestampingServiceDiagnosticConverterTest {
    private TimestampingServiceDiagnosticConverter timestampingServiceDiagnosticConverter;
    private static final String URL_1 = "https://tsa1.example.com";
    private static final String URL_2 = "https://tsa2.example.com";

    @Before
    public void setup() {
        timestampingServiceDiagnosticConverter = new TimestampingServiceDiagnosticConverter();
    }

    @Test
    public void convertSingleTimestampingServiceDiagnostics() {
        final OffsetDateTime now = OffsetDateTime.now();
        DiagnosticsStatus diagnosticsStatus = new DiagnosticsStatus(DiagnosticsErrorCodes.RETURN_SUCCESS, now);
        diagnosticsStatus.setDescription(URL_1);
        TimestampingServiceDiagnostics timestampingServiceDiagnostics = timestampingServiceDiagnosticConverter.convert(
                diagnosticsStatus
        );

        assertEquals(TimestampingStatus.SUCCESS, timestampingServiceDiagnostics.getStatusCode());
        assertEquals(DiagnosticStatusClass.OK, timestampingServiceDiagnostics.getStatusClass());
        assertEquals(now, timestampingServiceDiagnostics.getPrevUpdateAt());
    }

    @Test
    public void convertMultipleTimestampingServiceDiagnostics() {
        final OffsetDateTime prevUpdate = OffsetDateTime.now();
        final OffsetDateTime prevUpdate2 = prevUpdate.plusSeconds(60);
        DiagnosticsStatus diagnosticsStatus1 =
                new DiagnosticsStatus(DiagnosticsErrorCodes.ERROR_CODE_INTERNAL, prevUpdate);
        diagnosticsStatus1.setDescription(URL_1);
        DiagnosticsStatus diagnosticsStatus2 =
                new DiagnosticsStatus(DiagnosticsErrorCodes.ERROR_CODE_TIMESTAMP_UNINITIALIZED, prevUpdate2);
        diagnosticsStatus2.setDescription(URL_2);
        List<DiagnosticsStatus> list = new ArrayList<>(Arrays.asList(diagnosticsStatus1, diagnosticsStatus2));
        Set<TimestampingServiceDiagnostics> timestampingServiceDiagnostics = timestampingServiceDiagnosticConverter
                .convert(list);

        assertEquals(2, timestampingServiceDiagnostics.size());
        TimestampingServiceDiagnostics firstDiagnostic = timestampingServiceDiagnostics
                .stream()
                .filter(item -> item.getUrl().equals(URL_1))
                .findFirst()
                .orElse(null);

        TimestampingServiceDiagnostics secondDiagnostic = timestampingServiceDiagnostics
                .stream()
                .filter(item -> item.getUrl().equals(URL_2))
                .findFirst()
                .orElse(null);
        assertEquals(TimestampingStatus.ERROR_CODE_INTERNAL, firstDiagnostic.getStatusCode());
        assertEquals(DiagnosticStatusClass.FAIL, firstDiagnostic.getStatusClass());
        assertEquals(prevUpdate, firstDiagnostic.getPrevUpdateAt());

        assertEquals(TimestampingStatus.ERROR_CODE_TIMESTAMP_UNINITIALIZED, secondDiagnostic.getStatusCode());
        assertEquals(DiagnosticStatusClass.WAITING, secondDiagnostic.getStatusClass());
        assertEquals(prevUpdate2, secondDiagnostic.getPrevUpdateAt());
    }
}
