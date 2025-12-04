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
import feign.Response;
import feign.Util;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.niis.xroad.test.framework.core.report.ReportFormatter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static feign.Util.UTF_8;
import static feign.Util.decodeOrDefault;
import static feign.Util.ensureClosed;
import static feign.Util.valuesOrEmpty;

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
        //do nothing
    }

    @Override
    @SuppressWarnings("squid:S3776")
    protected Response logAndRebufferResponse(
            String configKey, Level logLevel, Response response, long elapsedTime) throws IOException {
        var responseLog = new ArrayList<String>();
        try {
            String protocolVersion = resolveProtocolVersion(response.protocolVersion());
            String reason =
                    response.reason() != null && logLevel.compareTo(Level.NONE) > 0
                            ? " " + response.reason()
                            : "";
            int status = response.status();
            collectLogRow(responseLog, "<--- %s %s%s (%sms)", protocolVersion, status, reason, elapsedTime);
            if (logLevel.ordinal() >= Level.HEADERS.ordinal()) {

                for (String field : response.headers().keySet()) {
                    if (shouldLogResponseHeader(field)) {
                        for (String value : valuesOrEmpty(response.headers(), field)) {
                            collectLogRow(responseLog, "%s: %s", field, value);
                        }
                    }
                }

                int bodyLength = 0;
                if (response.body() != null && !(status == HttpStatus.SC_NO_CONTENT || status == HttpStatus.SC_RESET_CONTENT)) {
                    // HTTP 204 No Content "...response MUST NOT include a message-body"
                    // HTTP 205 Reset Content "...response MUST NOT include an entity"
                    if (logLevel.ordinal() >= Level.FULL.ordinal()) {
                        collectLogRow(responseLog, ""); // CRLF
                    }
                    byte[] bodyData = Util.toByteArray(response.body().asInputStream());
                    ensureClosed(response.body());
                    bodyLength = bodyData.length;
                    if (logLevel.ordinal() >= Level.FULL.ordinal() && bodyLength > 0) {
                        collectLogRow(responseLog, "%s", decodeOrDefault(bodyData, UTF_8, "Binary data"));
                    }
                    collectLogRow(responseLog, "<--- END HTTP (%s-byte body)", bodyLength);
                    return response.toBuilder().body(bodyData).build();
                } else {
                    collectLogRow(responseLog, "<--- END HTTP (%s-byte body)", bodyLength);
                }
            }
            return response;
        } finally {
            var request = response.request();
            formatter.formatAndAddToReport(ReportFormatter.Attachment.create()
                    .setName("Feign request " + request.url())
                    .addSection("Request:", ReportFormatter.SectionType.BARE, request.toString())
                    .addSection("Response:", ReportFormatter.SectionType.BARE, responseLog.stream()
                            .reduce("", (a, b) -> a + b + System.lineSeparator())));
        }
    }

    private void collectLogRow(List<String> rows, String format, Object... args) {
        rows.add(String.format(format, args));
    }

}
