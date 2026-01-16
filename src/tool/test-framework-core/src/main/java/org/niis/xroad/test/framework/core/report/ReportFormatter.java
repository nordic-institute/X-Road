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
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.niis.xroad.test.framework.core.context.CucumberScenarioProvider;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This class is responsible for attaching an html section to the report.
 * <p>
 * The section is attached and has a name a title and several inner sections
 * (either collapsible or not)
 */
@Component
public class ReportFormatter implements InitializingBean {

    private static final String MAIN_TEMPLATE = "template.html";
    private final Map<String, String> templates = new HashMap<>();
    private final CucumberScenarioProvider scenarioProvider;

    public ReportFormatter(CucumberScenarioProvider scenarioProvider) {
        this.scenarioProvider = scenarioProvider;
    }

    public void formatAndAddToReport(Attachment attachment) {
        formatAndAddToReport(attachment, scenarioProvider.getCucumberScenario());
    }

    /**
     * Embeds the provided section to the html report as a collapsible section.
     *
     * @param attachment to embed
     */
    public void formatAndAddToReport(Attachment attachment, Scenario scenario) {
        String attachmentBody = attachment.getSections()
                .stream()
                .map(this::applySectionTemplate)
                .collect(Collectors.joining("\n"));

        String template = getTemplate(MAIN_TEMPLATE);
        if (template != null) {
            byte[] bytes = template
                    .replace("{{TITLE}}", attachment.getTitle())
                    .replace("{{CONTENT}}", attachmentBody)
                    .getBytes(StandardCharsets.UTF_8);

            scenario.attach(bytes, "text/html", attachment.getName());
        }
    }

    @Override
    public void afterPropertiesSet() {
        loadTemplate(MAIN_TEMPLATE);
        Arrays.stream(SectionType.values())
                .map(SectionType::getFileName)
                .forEach(this::loadTemplate);
    }

    private String getTemplate(String name) {
        return templates.get(name);
    }

    private void loadTemplate(String name) {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("report/" + name)) {
            Objects.requireNonNull(inputStream, "Template not found: " + name);
            templates.put(name, IOUtils.toString(inputStream, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AssertionError("Could not load template for reporting: " + name, e);
        }
    }

    private String applySectionTemplate(Triple<String, SectionType, String> section) {
        String template = getTemplate(section.getMiddle().getFileName());
        if (template == null) {
            throw new IllegalStateException("Template not found for type: " + section.getMiddle());
        }
        return String.format(template, section.getLeft(), section.getRight());
    }

    public static class Attachment {
        private String name = "Attachment"; // appears on the collapsible link
        private String title = ""; // appears on the top when expanded
        private final List<Triple<String, SectionType, String>> sections = new ArrayList<>();

        /**
         * Appears on the collapsible link.
         *
         * @param name string
         * @return this
         */
        public Attachment setName(String name) {
            this.name = name;
            return this;
        }

        /**
         * Appears on the top when expanded.
         *
         * @param title string
         * @return this
         */
        public Attachment setTitle(String title) {
            this.title = title;
            return this;
        }

        public Attachment addSection(String sectionName, SectionType type, String content) {
            sections.add(Triple.of(sectionName, type, content));
            return this;
        }

        public List<Triple<String, SectionType, String>> getSections() {
            return sections;
        }

        public String getName() {
            return name;
        }

        public String getTitle() {
            return title;
        }

        public static Attachment create() {
            return new Attachment();
        }
    }

    public enum SectionType {
        STANDARD("details_template.html"),
        TABLE("table_template.html"),
        COLLAPSIBLE("details_collapsible_template.html"),
        BARE("details_bare_template.html");

        private final String fileName;

        SectionType(String fileName) {
            this.fileName = fileName;
        }

        public String getFileName() {
            return fileName;
        }
    }
}
