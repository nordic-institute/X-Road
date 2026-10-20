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

import org.niis.xroad.opmonitor.core.AbstractQueryTest.Answer;
import org.niis.xroad.opmonitor.core.AbstractQueryTest.QueryCase;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reads the parts of a query response that the comparison tests assert, and holds the order of SOAP elements,
 * MIME parts and MIME part headers that responses must have.
 */
final class QueryResponses {
    static final String NORMALISED_BOUNDARY = "xroadBOUNDARY";
    static final String NORMALISED_FAULT_DETAIL = "FAULT_DETAIL_UUID";
    static final String ATTACHMENT_CONTENT_ID = "operational-monitoring-data.json.gz";

    private static final Pattern BOUNDARY_PARAMETER = Pattern.compile("boundary=\"?([^\";\\s]+)\"?");
    private static final Pattern BOUNDARY_FORMAT = Pattern.compile("xroad[A-Za-z]{30}");
    private static final Pattern FAULT_DETAIL = Pattern.compile(
            "(<faultDetail[^>]*>)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(</faultDetail>)");
    private static final Pattern ATTACHMENT = Pattern.compile(
            "(content-id: <" + Pattern.quote(ATTACHMENT_CONTENT_ID) + ">\r\n\r\n)(.*)(\r\n--" + NORMALISED_BOUNDARY + "--)",
            Pattern.DOTALL);
    private static final String HEADER_END = "\r\n\r\n";
    private static final String SOAP_END = "</SOAP-ENV:Envelope>";

    private static final List<String> ENVELOPE_AND_HEADER = List.of(
            "SOAP-ENV:Envelope", "SOAP-ENV:Header",
            "xroad:client", "id:xRoadInstance", "id:memberClass", "id:memberCode", "id:subsystemCode",
            "xroad:service", "id:xRoadInstance", "id:memberClass", "id:memberCode", "id:serviceCode",
            "xroad:securityServer", "id:xRoadInstance", "id:memberClass", "id:memberCode", "id:serverCode",
            "xroad:id", "xroad:protocolVersion",
            "SOAP-ENV:Body");
    private static final List<String> HEALTH_DATA_BODY = List.of(
            "om:getSecurityServerHealthDataResponse",
            "om:monitoringStartupTimestamp", "om:statisticsPeriodSeconds",
            "om:servicesEvents", "om:serviceEvents",
            "om:service", "id:xRoadInstance", "id:memberClass", "id:memberCode", "id:subsystemCode", "id:serviceCode",
            "id:serviceVersion",
            "om:lastSuccessfulRequestTimestamp", "om:lastUnsuccessfulRequestTimestamp", "om:serviceType",
            "om:lastPeriodStatistics",
            "om:successfulRequestCount", "om:unsuccessfulRequestCount",
            "om:requestMinDuration", "om:requestAverageDuration", "om:requestMaxDuration", "om:requestDurationStdDev",
            "om:requestMinSize", "om:requestAverageSize", "om:requestMaxSize", "om:requestSizeStdDev",
            "om:responseMinSize", "om:responseAverageSize", "om:responseMaxSize", "om:responseSizeStdDev");
    private static final List<String> OPERATIONAL_DATA_BODY = List.of(
            "om:getSecurityServerOperationalDataResponse", "om:recordsCount", "om:records");
    private static final List<String> TRUNCATED_OPERATIONAL_DATA_BODY = List.of(
            "om:getSecurityServerOperationalDataResponse", "om:recordsCount", "om:records", "om:nextRecordsFrom");
    private static final List<String> FAULT = List.of(
            "SOAP-ENV:Envelope", "SOAP-ENV:Body", "SOAP-ENV:Fault",
            "faultcode", "faultstring", "faultactor", "detail", "faultDetail");
    private static final List<List<String>> OPERATIONAL_DATA_PART_HEADERS = List.of(
            List.of("content-type:text/xml; charset=utf-8"),
            List.of("content-type:application/gzip", "content-transfer-encoding: binary",
                    "content-id: <" + ATTACHMENT_CONTENT_ID + ">"));

    private QueryResponses() {
    }

    /**
     * Returns the SOAP elements in document order that the response of the case must have.
     */
    static List<String> expectedSoapElements(QueryCase queryCase) {
        return switch (queryCase) {
            case HEALTH_DATA -> Stream.concat(ENVELOPE_AND_HEADER.stream(), HEALTH_DATA_BODY.stream()).toList();
            case OPERATIONAL_DATA_EMPTY, OPERATIONAL_DATA_WITH_RECORDS ->
                    Stream.concat(ENVELOPE_AND_HEADER.stream(), OPERATIONAL_DATA_BODY.stream()).toList();
            case OPERATIONAL_DATA_TRUNCATED ->
                    Stream.concat(ENVELOPE_AND_HEADER.stream(), TRUNCATED_OPERATIONAL_DATA_BODY.stream()).toList();
            default -> FAULT;
        };
    }

    /**
     * Returns the headers of each MIME part, in the order the parts and the headers are written, that the response
     * of the case must have. A response that is not multipart has no parts.
     */
    static List<List<String>> expectedPartHeaders(QueryCase queryCase) {
        return switch (queryCase) {
            case OPERATIONAL_DATA_EMPTY, OPERATIONAL_DATA_WITH_RECORDS, OPERATIONAL_DATA_TRUNCATED -> OPERATIONAL_DATA_PART_HEADERS;
            default -> List.of();
        };
    }

    /**
     * Returns the Content-Type header the response of the case must have once the MIME boundary is normalised.
     */
    static String expectedContentType(QueryCase queryCase) {
        return switch (queryCase) {
            case HEALTH_DATA -> "text/xml; charset=utf-8";
            case OPERATIONAL_DATA_EMPTY, OPERATIONAL_DATA_WITH_RECORDS, OPERATIONAL_DATA_TRUNCATED ->
                    "multipart/related; type=\"text/xml\"; charset=UTF-8; boundary=" + NORMALISED_BOUNDARY;
            default -> "text/xml;charset=utf-8";
        };
    }

    /**
     * Replaces the random MIME boundary and fault detail of a response with constant tokens, after checking their format.
     */
    static Answer normalised(Answer answer) {
        var contentType = answer.contentType();
        var body = new String(answer.body(), StandardCharsets.ISO_8859_1);
        var boundary = boundary(contentType);
        if (boundary != null) {
            assertThat(boundary).matches(BOUNDARY_FORMAT);
            contentType = contentType.replace(boundary, NORMALISED_BOUNDARY);
            body = body.replace(boundary, NORMALISED_BOUNDARY);
        }
        body = FAULT_DETAIL.matcher(body).replaceAll("$1" + NORMALISED_FAULT_DETAIL + "$2");
        return new Answer(contentType, body.getBytes(StandardCharsets.ISO_8859_1));
    }

    static boolean hasFaultDetail(Answer answer) {
        return FAULT_DETAIL.matcher(new String(answer.body(), StandardCharsets.ISO_8859_1)).find();
    }

    static String boundary(String contentType) {
        var matcher = BOUNDARY_PARAMETER.matcher(contentType);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Returns the header lines of each MIME part in the order the parts and the lines are written, or no entries
     * for a response that is not multipart.
     */
    static List<List<String>> partHeaders(Answer answer) {
        var boundary = boundary(answer.contentType());
        var headers = new ArrayList<List<String>>();
        if (boundary == null) {
            return headers;
        }
        var parts = new String(answer.body(), StandardCharsets.ISO_8859_1).split("--" + boundary);
        for (int i = 1; i < parts.length; i++) {
            var part = parts[i];
            if (part.startsWith("--")) {
                break;
            }
            int headerEnd = part.indexOf(HEADER_END);
            var headerBlock = headerEnd < 0 ? part : part.substring(0, headerEnd);
            headers.add(headerBlock.lines().filter(line -> !line.isEmpty()).toList());
        }
        return headers;
    }

    /**
     * Returns the qualified names of the SOAP elements in document order.
     */
    static List<String> soapElements(Answer answer) throws XMLStreamException {
        var text = new String(answer.body(), StandardCharsets.UTF_8);
        int start = text.indexOf("<?xml");
        int end = text.indexOf(SOAP_END) + SOAP_END.length();
        var factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        var reader = factory.createXMLStreamReader(
                new ByteArrayInputStream(text.substring(start, end).getBytes(StandardCharsets.UTF_8)));
        var elements = new ArrayList<String>();
        while (reader.hasNext()) {
            if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                var prefix = reader.getPrefix();
                elements.add(prefix == null || prefix.isEmpty() ? reader.getLocalName() : prefix + ":" + reader.getLocalName());
            }
        }
        return elements;
    }

    /**
     * Returns the response as text for a fixture: boundary and fault detail normalised, the compressed attachment
     * replaced by its content, and carriage returns written as {@code \r} so that line ending conversion cannot
     * change the fixture.
     */
    static String fixtureText(Answer answer) throws IOException {
        var normalised = normalised(answer);
        var body = new String(normalised.body(), StandardCharsets.ISO_8859_1);
        var matcher = ATTACHMENT.matcher(body);
        byte[] bytes = normalised.body();
        if (matcher.find()) {
            var out = new ByteArrayOutputStream();
            out.write(body.substring(0, matcher.start(2)).getBytes(StandardCharsets.ISO_8859_1));
            out.write(gunzip(matcher.group(2).getBytes(StandardCharsets.ISO_8859_1)));
            out.write(body.substring(matcher.end(2)).getBytes(StandardCharsets.ISO_8859_1));
            bytes = out.toByteArray();
        }
        return (normalised.contentType() + "\n\n" + new String(bytes, StandardCharsets.UTF_8)).replace("\r", "\\r");
    }

    private static byte[] gunzip(byte[] compressed) throws IOException {
        try (var in = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return in.readAllBytes();
        }
    }
}
