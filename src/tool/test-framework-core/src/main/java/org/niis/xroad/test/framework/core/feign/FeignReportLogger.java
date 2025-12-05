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
package org.niis.xroad.test.framework.core.feign;

import feign.Logger;
import feign.Request;
import feign.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.test.framework.core.report.ReportFormatter;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Custom Feign logger that captures HTTP requests and responses and adds them
 * to test reports.
 * This logger only captures data when called within a Cucumber test scope.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeignReportLogger extends Logger {
    private final ReportFormatter formatter;

    @Override
    protected void log(String configKey, String format, Object... args) {
        if (log.isDebugEnabled()) {
            log.debug(String.format(methodTag(configKey) + format, args));
        }
    }

    @Override
    protected void logRequest(String configKey, Level logLevel, Request request) {
        try {
            formatter.formatAndAddToReport(ReportFormatter.Attachment.create()
                    .setName("Request: " + request.httpMethod().name() + " " + request.url())
                    .addSection("Request:", ReportFormatter.SectionType.BARE, request.toString()));
        } catch (Exception e) {
            log(configKey, "Failed to capture request for reporting: %s", e.getMessage());
        }
    }

    @Override
    protected Response logAndRebufferResponse(String configKey, Level logLevel, Response response, long elapsedTime)
            throws IOException {
        try {
            formatter.formatAndAddToReport(ReportFormatter.Attachment.create()
                    .setName("Response: " + response.status())
                    .addSection("Request:", ReportFormatter.SectionType.BARE, response.toString()));
        } catch (Exception e) {
            log(configKey, "Failed to capture response for reporting: %s", e.getMessage());
        }
        return super.logAndRebufferResponse(configKey, logLevel, response, elapsedTime);
    }


}
