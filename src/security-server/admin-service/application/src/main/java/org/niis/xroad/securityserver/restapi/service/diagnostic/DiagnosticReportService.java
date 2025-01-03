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

import ee.ria.xroad.common.util.JsonUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedList;
import java.util.List;

@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class DiagnosticReportService {
    private static final ObjectMapper MAPPER = JsonUtils.getObjectMapperCopy()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final List<DiagnosticCollector<?>> collectors;

    public byte[] collectSystemInformation() throws JsonProcessingException {
        var data = new LinkedList<DiagnosticReportService.InfoFragment>();

        collectors.forEach(collector -> data.add(collectFrom(collector)));

        return MAPPER.writeValueAsBytes(data);
    }

    private DiagnosticReportService.InfoFragment collectFrom(DiagnosticCollector collector) {
        try {
            return new DiagnosticReportService.InfoFragment(collector.name(), collector.collect(), null);
        } catch (Exception e) {
            log.error("Failed to read data for {}", collector.name(), e);
            return new DiagnosticReportService.InfoFragment(collector.name(), null, e.getMessage());
        }
    }

    private record InfoFragment(String name, Object value, String errorMessage) {
    }

}

