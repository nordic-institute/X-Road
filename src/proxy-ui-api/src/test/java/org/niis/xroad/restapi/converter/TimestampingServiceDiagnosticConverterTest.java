/**
 * The MIT License
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
package org.niis.xroad.restapi.converter;
import ee.ria.xroad.common.DiagnosticsErrorCodes;
import ee.ria.xroad.common.DiagnosticsStatus;

import org.joda.time.DateTimeUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.restapi.openapi.model.DiagnosticStatusClass;
import org.niis.xroad.restapi.openapi.model.TimestampingServiceDiagnostics;
import org.niis.xroad.restapi.openapi.model.TimestampingStatus;
import org.niis.xroad.restapi.util.TestUtils;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Test TimestampingServiceDiagnosticConverter
 */
public class TimestampingServiceDiagnosticConverterTest {
    private TimestampingServiceDiagnosticConverter timestampingServiceDiagnosticConverter;
    private static final String CURRENT_TIME = "2020-03-16T10:16:12.123";
    private static final String URL_1 = "https://tsa1.example.com";
    private static final String PREVIOUS_UPDATE_STR_1 = "2020-03-16T10:15:40.703";
    private static final LocalTime PREVIOUS_UPDATE_1 = LocalTime.of(10, 15, 40, 703000000);
    private static final String URL_2 = "https://tsa1.example.com";
    private static final String PREVIOUS_UPDATE_STR_2 = "2020-03-16T10:15:42.123";
    private static final LocalTime PREVIOUS_UPDATE_2 = LocalTime.of(10, 15, 42, 123000000);

    @Before
    public void setup() {
        timestampingServiceDiagnosticConverter = new TimestampingServiceDiagnosticConverter();
        DateTimeUtils.setCurrentMillisFixed(TestUtils.fromDateTimeToMilliseconds(CURRENT_TIME));
    }

    @After
    public final void tearDown() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    @Test
    public void convertSingleTimestampingServiceDiagnostics() {
        DateTimeUtils.setCurrentMillisFixed(TestUtils
                .fromDateTimeToMilliseconds(CURRENT_TIME));
        DiagnosticsStatus diagnosticsStatus = new DiagnosticsStatus(
                DiagnosticsErrorCodes.RETURN_SUCCESS, PREVIOUS_UPDATE_1);
        diagnosticsStatus.setDescription(URL_1);
        TimestampingServiceDiagnostics timestampingServiceDiagnostics = timestampingServiceDiagnosticConverter.convert(
                diagnosticsStatus
        );

        assertEquals(TimestampingStatus.SUCCESS, timestampingServiceDiagnostics.getStatusCode());
        assertEquals(DiagnosticStatusClass.OK, timestampingServiceDiagnostics.getStatusClass());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(PREVIOUS_UPDATE_STR_1),
                (Long)timestampingServiceDiagnostics.getPrevUpdateAt().toInstant().toEpochMilli());
    }

    @Test
    public void convertMultipleTimestampingServiceDiagnostics() {
        DateTimeUtils.setCurrentMillisFixed(TestUtils
                .fromDateTimeToMilliseconds(CURRENT_TIME));
        DiagnosticsStatus diagnosticsStatus1 = new DiagnosticsStatus(
                DiagnosticsErrorCodes.ERROR_CODE_INTERNAL, PREVIOUS_UPDATE_1);
        diagnosticsStatus1.setDescription(URL_1);
        DiagnosticsStatus diagnosticsStatus2 = new DiagnosticsStatus(
                DiagnosticsErrorCodes.ERROR_CODE_TIMESTAMP_UNINITIALIZED, PREVIOUS_UPDATE_2);
        diagnosticsStatus2.setDescription(URL_2);
        List<DiagnosticsStatus> list = new ArrayList<>(Arrays.asList(diagnosticsStatus1, diagnosticsStatus2));
        List<TimestampingServiceDiagnostics> timestampingServiceDiagnostics = timestampingServiceDiagnosticConverter
                .convert(list);

        assertEquals(2, timestampingServiceDiagnostics.size());

        assertEquals(URL_1, timestampingServiceDiagnostics.get(0).getUrl());
        assertEquals(TimestampingStatus.ERROR_CODE_INTERNAL, timestampingServiceDiagnostics.get(0).getStatusCode());
        assertEquals(DiagnosticStatusClass.FAIL, timestampingServiceDiagnostics.get(0).getStatusClass());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(PREVIOUS_UPDATE_STR_1),
                (Long)timestampingServiceDiagnostics.get(0).getPrevUpdateAt().toInstant().toEpochMilli());

        assertEquals(URL_2, timestampingServiceDiagnostics.get(1).getUrl());
        assertEquals(TimestampingStatus.ERROR_CODE_TIMESTAMP_UNINITIALIZED, timestampingServiceDiagnostics.get(1)
                .getStatusCode());
        assertEquals(DiagnosticStatusClass.WAITING, timestampingServiceDiagnostics.get(1).getStatusClass());
        assertEquals(TestUtils.fromDateTimeToMilliseconds(PREVIOUS_UPDATE_STR_2),
                (Long)timestampingServiceDiagnostics.get(1).getPrevUpdateAt().toInstant().toEpochMilli());
    }
}
