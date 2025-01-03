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

import lombok.RequiredArgsConstructor;
import org.niis.xroad.securityserver.restapi.converter.GlobalConfDiagnosticConverter;
import org.niis.xroad.securityserver.restapi.openapi.model.GlobalConfDiagnostics;
import org.niis.xroad.securityserver.restapi.service.DiagnosticService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Order(DiagnosticCollector.ORDER_GROUP3)
public class GlobalConfigurationCollector implements DiagnosticCollector<GlobalConfDiagnostics> {
    private final DiagnosticService diagnosticService;
    private final GlobalConfDiagnosticConverter gcDiagnosticConverter;

    @Override
    public String name() {
        return "Global configuration";
    }

    @Override
    public GlobalConfDiagnostics collect() {
        return gcDiagnosticConverter.convert(diagnosticService.queryGlobalConfStatus());
    }
}
