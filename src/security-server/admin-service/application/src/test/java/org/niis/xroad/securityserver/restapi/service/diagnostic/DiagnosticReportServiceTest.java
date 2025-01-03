/*
 * The MIT License
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
package org.niis.xroad.securityserver.restapi.service.diagnostic;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiagnosticReportServiceTest {
    private static final String NAME1 = "collector1";
    private static final String NAME2 = "collector2";

    @Mock
    private DiagnosticCollector collector1;
    @Mock
    private DiagnosticCollector collector2;

    private DiagnosticReportService service;

    @BeforeEach
    void setUp() {
        when(collector1.name()).thenReturn(NAME1);
        when(collector2.name()).thenReturn(NAME2);
        service = new DiagnosticReportService(List.of(collector1, collector2));
    }

    @Test
    void testGetDiagnosticReportFullSuccess() throws JsonProcessingException {
        when(collector1.collect()).thenReturn(7);
        when(collector2.collect()).thenReturn("Ubuntu");

        var bytes = service.collectSystemInformation();

        assertThatJson(new String(bytes, UTF_8))
                .isEqualTo("""
                        [
                            {
                                "name":"collector1",
                                "value":7
                            },
                            {
                                "name":"collector2",
                                "value":"Ubuntu"
                            }
                        ]
                        """
                );
    }

    @Test
    void testGetDiagnosticReportWhenCollectorFails() throws JsonProcessingException {
        when(collector1.collect()).thenReturn(7);
        when(collector2.collect()).thenThrow(new RuntimeException("Failed access OS info"));

        var bytes = service.collectSystemInformation();

        assertThatJson(new String(bytes, UTF_8))
                .isEqualTo("""
                        [
                            {
                                "name":"collector1",
                                "value":7
                            },
                            {
                                "name":"collector2",
                                "errorMessage":"Failed access OS info"
                            }
                        ]
                        """
                );
    }
}
