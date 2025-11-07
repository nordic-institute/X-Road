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

import ee.ria.xroad.common.DiagnosticStatus;
import ee.ria.xroad.common.DiagnosticsStatus;
import ee.ria.xroad.common.util.TimeUtils;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.securityserver.restapi.openapi.model.CostTypeDto;
import org.niis.xroad.securityserver.restapi.openapi.model.DiagnosticStatusClassDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TimestampingServiceDiagnosticsDto;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test TimestampingServiceDiagnosticConverter
 */
public class TimestampingServiceDiagnosticConverterTest {
    private TimestampingServiceDiagnosticConverter timestampingServiceDiagnosticConverter;
    private static final String URL_1 = "https://tsa1.example.com";
    private static final String URL_2 = "https://tsa2.example.com";

    @Before
    public void setup() {
        ServerConfProvider serverConfProvider = mock(ServerConfProvider.class);
        when(serverConfProvider.getTspCostType(URL_1)).thenReturn(CostTypeDto.PAID.name());
        when(serverConfProvider.getTspCostType(URL_2)).thenReturn(CostTypeDto.FREE.name());
        timestampingServiceDiagnosticConverter = new TimestampingServiceDiagnosticConverter(serverConfProvider);
    }

    @Test
    public void convertSingleTimestampingServiceDiagnostics() {
        final OffsetDateTime now = TimeUtils.offsetDateTimeNow();
        DiagnosticsStatus diagnosticsStatus = new DiagnosticsStatus(DiagnosticStatus.OK, now);
        diagnosticsStatus.setDescription(URL_1);
        TimestampingServiceDiagnosticsDto timestampingServiceDiagnostics = timestampingServiceDiagnosticConverter.convert(
                diagnosticsStatus
        );

        assertEquals(DiagnosticStatusClassDto.OK, timestampingServiceDiagnostics.getStatusClass());
        assertEquals(now, timestampingServiceDiagnostics.getPrevUpdateAt());
        assertEquals(URL_1, timestampingServiceDiagnostics.getUrl());
        assertEquals(CostTypeDto.PAID, timestampingServiceDiagnostics.getCostType());
    }

    @Test
    public void convertMultipleTimestampingServiceDiagnostics() {
        final OffsetDateTime prevUpdate = TimeUtils.offsetDateTimeNow();
        final OffsetDateTime prevUpdate2 = prevUpdate.plusSeconds(60);
        DiagnosticsStatus diagnosticsStatus1 =
                new DiagnosticsStatus(DiagnosticStatus.ERROR, prevUpdate, URL_1, ErrorCode.TIMESTAMP_TOKEN_SIGNER_INFO_NOT_FOUND);
        DiagnosticsStatus diagnosticsStatus2 = new DiagnosticsStatus(DiagnosticStatus.UNINITIALIZED, prevUpdate2, URL_2);
        List<DiagnosticsStatus> list = new ArrayList<>(Arrays.asList(diagnosticsStatus1, diagnosticsStatus2));
        Set<TimestampingServiceDiagnosticsDto> timestampingServiceDiagnostics = timestampingServiceDiagnosticConverter
                .convert(list);

        assertEquals(2, timestampingServiceDiagnostics.size());
        TimestampingServiceDiagnosticsDto firstDiagnostic = timestampingServiceDiagnostics
                .stream()
                .filter(item -> item.getUrl().equals(URL_1))
                .findFirst()
                .orElse(null);

        TimestampingServiceDiagnosticsDto secondDiagnostic = timestampingServiceDiagnostics
                .stream()
                .filter(item -> item.getUrl().equals(URL_2))
                .findFirst()
                .orElse(null);
        assertEquals(ErrorCode.TIMESTAMP_TOKEN_SIGNER_INFO_NOT_FOUND.code(), firstDiagnostic.getError().getCode());
        assertEquals(DiagnosticStatusClassDto.FAIL, firstDiagnostic.getStatusClass());
        assertEquals(prevUpdate, firstDiagnostic.getPrevUpdateAt());
        assertEquals(URL_1, firstDiagnostic.getUrl());
        assertEquals(CostTypeDto.PAID, firstDiagnostic.getCostType());

        assertEquals(DiagnosticStatusClassDto.WAITING, secondDiagnostic.getStatusClass());
        assertEquals(prevUpdate2, secondDiagnostic.getPrevUpdateAt());
        assertEquals(URL_2, secondDiagnostic.getUrl());
        assertEquals(CostTypeDto.FREE, secondDiagnostic.getCostType());
    }
}
