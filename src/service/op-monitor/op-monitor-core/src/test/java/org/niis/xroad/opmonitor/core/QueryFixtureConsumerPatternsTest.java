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
package org.niis.xroad.opmonitor.core;

import ee.ria.xroad.common.util.JsonUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.niis.xroad.opmonitor.core.AbstractQueryTest.QueryCase;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Applies the patterns that known monitoring clients use to read the responses to the recorded responses.
 */
class QueryFixtureConsumerPatternsTest {
    private static final String OM_NAMESPACE = "http://x-road.eu/xsd/op-monitoring.xsd";

    private static final List<String> HEALTH_STATISTICS_FIELDS = List.of(
            "successfulRequestCount", "unsuccessfulRequestCount",
            "requestMinDuration", "requestAverageDuration", "requestMaxDuration", "requestDurationStdDev",
            "requestMinSize", "requestAverageSize", "requestMaxSize", "requestSizeStdDev",
            "responseMinSize", "responseAverageSize", "responseMaxSize", "responseSizeStdDev");

    // X-Road Metrics collector: reads the attachment without MIME parsing.
    private static final Pattern METRICS_ATTACHMENT = Pattern.compile(
            "content-id: <operational-monitoring-data.json.gz>\r\n\r\n(.+)\r\n--xroad", Pattern.DOTALL);
    private static final Pattern METRICS_NEXT_RECORDS_FROM = Pattern.compile("<om:nextRecordsFrom>(\\d+)</om:nextRecordsFrom>");
    // RIA Zabbix collector: skips the multipart framing of the response.
    private static final Pattern RIA_ENVELOPE = Pattern.compile("<SOAP-ENV:Envelope.+</SOAP-ENV:Envelope>", Pattern.DOTALL);

    @ParameterizedTest
    @CsvSource({"OPERATIONAL_DATA_EMPTY, 0", "OPERATIONAL_DATA_WITH_RECORDS, 2"})
    void metricsCollectorFindsTheAttachmentAndItsRecords(QueryCase queryCase, int recordsCount) throws Exception {
        var response = recordedBody(queryCase);

        var attachment = METRICS_ATTACHMENT.matcher(response);

        assertThat(attachment.find()).isTrue();
        var records = JsonUtils.getObjectReader().readTree(attachment.group(1)).get("records");
        assertThat(records.size()).isEqualTo(recordsCount);
        assertThat(response).contains("<om:recordsCount>" + recordsCount + "</om:recordsCount>");
        assertThat(METRICS_NEXT_RECORDS_FROM.matcher(response).find()).isFalse();
    }

    @Test
    void metricsCollectorResumesPagingFromNextRecordsFromOfATruncatedResponse() throws Exception {
        var response = recordedBody(QueryCase.OPERATIONAL_DATA_TRUNCATED);

        var attachment = METRICS_ATTACHMENT.matcher(response);
        var nextRecordsFrom = METRICS_NEXT_RECORDS_FROM.matcher(response);

        assertThat(attachment.find()).isTrue();
        assertThat(nextRecordsFrom.find()).isTrue();
        assertThat(nextRecordsFrom.find()).isFalse();
        var records = JsonUtils.getObjectReader().readTree(attachment.group(1)).get("records");
        assertThat(records.size()).isEqualTo(2);
        assertThat(response).contains("<om:recordsCount>2</om:recordsCount>");
        var lastRecordTimestamp = records.get(records.size() - 1).get("monitoringDataTs").asLong();
        nextRecordsFrom.reset().find();
        assertThat(Long.parseLong(nextRecordsFrom.group(1))).isEqualTo(lastRecordTimestamp + 1);
    }

    @Test
    void riaCollectorFindsTheHealthDataFields() throws Exception {
        var envelope = RIA_ENVELOPE.matcher(recordedBody(QueryCase.HEALTH_DATA));

        assertThat(envelope.find()).isTrue();
        var healthData = parse(envelope.group()).getElementsByTagNameNS(OM_NAMESPACE, "getSecurityServerHealthDataResponse");
        assertThat(healthData.getLength()).isEqualTo(1);
        var response = (Element) healthData.item(0);
        assertThat(text(response, "monitoringStartupTimestamp")).isNotEmpty();
        assertThat(text(response, "statisticsPeriodSeconds")).isNotEmpty();
        assertThat(response.getElementsByTagNameNS(OM_NAMESPACE, "serviceEvents").getLength()).isEqualTo(1);
        for (var field : HEALTH_STATISTICS_FIELDS) {
            assertThat(text(response, field)).as(field).isNotEmpty();
        }
    }

    @ParameterizedTest
    @EnumSource(value = QueryCase.class, names = "FAULT_.*", mode = EnumSource.Mode.MATCH_ALL)
    void metricsCollectorFindsTheFaultFields(QueryCase queryCase) throws Exception {
        var fault = parse(recordedBody(queryCase));

        assertThat(fault.getElementsByTagName("faultcode").item(0).getTextContent()).isNotEmpty();
        assertThat(fault.getElementsByTagName("faultstring").item(0).getTextContent()).isNotEmpty();
        assertThat(fault.getElementsByTagName("faultDetail").item(0).getTextContent()).isNotEmpty();
    }

    private static String recordedBody(QueryCase queryCase) throws IOException {
        var fixture = QueryFixtureTest.fixture(queryCase).replace("\\r", "\r");
        return fixture.substring(fixture.indexOf("\n\n") + 2);
    }

    private static String text(Element parent, String localName) {
        return parent.getElementsByTagNameNS(OM_NAMESPACE, localName).item(0).getTextContent();
    }

    private static Document parse(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
