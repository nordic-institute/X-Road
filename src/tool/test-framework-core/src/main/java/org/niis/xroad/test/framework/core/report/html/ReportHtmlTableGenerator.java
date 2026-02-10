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

package org.niis.xroad.test.framework.core.report.html;

import com.google.common.collect.ImmutableList;
import org.niis.xroad.test.framework.core.file.ClasspathFileResolver;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReportHtmlTableGenerator {

    private final ClasspathFileResolver classpathFileResolver;

    public ReportHtmlTableGenerator(ClasspathFileResolver classpathFileResolver) {
        this.classpathFileResolver = classpathFileResolver;
    }

    public String generateTable(List<List<String>> table, boolean appendRowNums) {
        StringBuilder html = new StringBuilder("<table class=\"table table-striped table-fit-content\">");
        populateHeader(html, table.getFirst(), appendRowNums);
        populateBody(html, table, appendRowNums);
        html.append("</table>");
        html.append(getTableCss());
        return html.toString();
    }

    private void populateHeader(StringBuilder html, List<String> headerValues, boolean appendRowNums) {
        List<String> finalHeaderValues;
        if (appendRowNums) {
            finalHeaderValues = ImmutableList.<String>builder().addAll(headerValues).add("#").build();
        } else {
            finalHeaderValues = headerValues;
        }
        html.append("<thead><tr>");
        finalHeaderValues.forEach(value -> html.append("<th>").append(value).append("</th>"));
        html.append("</tr></thead>");
    }

    private void populateBody(StringBuilder html, List<List<String>> table, boolean appendRowNums) {
        html.append("<tbody>");
        for (int i = 1; i < table.size(); i++) {
            List<String> rowValues = table.get(i);
            String rowCss = "";
            if (rowValues.stream().anyMatch("OK"::equals)) {
                rowCss = "success";
            } else if (rowValues.stream().anyMatch("FAILED"::equals)) {
                rowCss = "danger";
            } else if (rowValues.stream().anyMatch("SKIPPED"::equals)) {
                rowCss = "warning";
            }
            html.append("<tr class=\"").append(rowCss).append("\">");
            if (appendRowNums) {
                html.append("<td>").append(i).append("</td>");
            }
            rowValues.forEach(value -> html.append("<td>").append(value).append("</td>"));
            html.append("</tr>");
        }
        html.append("</tbody>");
    }

    private String getTableCss() {
        String css = classpathFileResolver.getFileAsString("report/css/table.css");
        return """
                <style>
                %s
                </style>
                """.formatted(css);
    }
}
