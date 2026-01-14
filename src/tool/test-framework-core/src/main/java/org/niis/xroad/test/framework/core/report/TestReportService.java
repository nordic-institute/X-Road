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

package org.niis.xroad.test.framework.core.report;

import io.cucumber.java.Scenario;
import org.springframework.stereotype.Component;

/**
 * Service responsible for exposing methods that attach information to the test
 * report.
 */
@Component
public class TestReportService {

    private final ReportFormatter reportFormatter;

    public TestReportService(ReportFormatter reportFormatter) {
        this.reportFormatter = reportFormatter;
    }

    /**
     * Attaches the provided object to the report, formatting it as json.
     *
     * @param title   of the section
     * @param content to be attached
     */
    public void attachJson(String title, Object content) {
        ReportFormatter.Attachment attachment = ReportFormatter.Attachment.create()
                .setName(title)
                .addSection("", ReportFormatter.SectionType.BARE, JsonFormattingUtils.prettyPrintJson(content));
        reportFormatter.formatAndAddToReport(attachment);
    }

    /**
     * Attaches the provided object to the report, formatting it as json.
     *
     * @param title   of the section
     * @param content to be attached
     */
    public void attachJson(String title, Object content, Scenario scenario) {
        ReportFormatter.Attachment attachment = ReportFormatter.Attachment.create()
                .setName(title)
                .addSection("", ReportFormatter.SectionType.BARE, JsonFormattingUtils.prettyPrintJson(content));
        reportFormatter.formatAndAddToReport(attachment, scenario);
    }

    /**
     * Attaches the provided text to the report.
     *
     * @param title of the section
     * @param text  to be attached
     */
    public void attachText(String title, String text) {
        ReportFormatter.Attachment attachment = ReportFormatter.Attachment.create()
                .setName(title)
                .addSection("", ReportFormatter.SectionType.BARE, text);
        reportFormatter.formatAndAddToReport(attachment);
    }
}
