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
package org.niis.xroad.test.apitest.core.report;

import io.qameta.allure.attachment.DefaultAttachmentProcessor;
import io.qameta.allure.attachment.FreemarkerAttachmentRenderer;
import io.qameta.allure.attachment.http.HttpRequestAttachment;
import io.qameta.allure.attachment.http.HttpResponseAttachment;
import io.restassured.filter.FilterContext;
import io.restassured.filter.OrderedFilter;
import io.restassured.internal.support.Prettifier;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * RestAssured {@link OrderedFilter} that attaches request and response to the Allure report with
 * names in the form {@code Request: <METHOD> <path>} / {@code Response: <METHOD> <path>}.
 *
 * <p>The path is taken from the path template ({@link FilterableRequestSpecification#getDerivedPath()}) when
 * available, falling back to {@link FilterableRequestSpecification#getUserDefinedPath()} and finally to the
 * full URI — this keeps parameterised path variables in the name rather than resolved IDs,
 * making multiple calls to the same endpoint individually identifiable yet grouped by template.
 *
 * <p>The FreeMarker templates ({@code http-request.ftl} / {@code http-response.ftl}) and the attachment
 * machinery are sourced from {@code allure-attachments}, the same dependency used internally by the
 * official {@code allure-rest-assured} adapter. The {@code allure-rest-assured} artifact is therefore
 * not required on the classpath.
 */
public final class NamedHttpAttachmentFilter implements OrderedFilter {

    private static final String REQUEST_TEMPLATE = "http-request.ftl";
    private static final String RESPONSE_TEMPLATE = "http-response.ftl";

    @Override
    public Response filter(FilterableRequestSpecification requestSpec,
                           FilterableResponseSpecification responseSpec,
                           FilterContext ctx) {
        var prettifier = new Prettifier();
        var blacklisted = blacklistedHeaders(requestSpec);
        var method = requestSpec.getMethod();
        var path = resolvePath(requestSpec);
        var attachmentName = method + " " + path;

        var requestAttachment = HttpRequestAttachment.Builder
                .create("Request: " + attachmentName, requestSpec.getURI())
                .setMethod(method)
                .setHeaders(toHeaderMap(requestSpec.getHeaders(), blacklisted))
                .setCookies(toCookieMap(requestSpec.getCookies(), Set.of()))
                .setFormParams(toStringMap(requestSpec.getFormParams()))
                .setBody(requestBody(prettifier, requestSpec))
                .build();

        var processor = new DefaultAttachmentProcessor();
        processor.addAttachment(requestAttachment, new FreemarkerAttachmentRenderer(REQUEST_TEMPLATE));

        var response = ctx.next(requestSpec, responseSpec);

        var responseAttachment = HttpResponseAttachment.Builder
                .create("Response: " + attachmentName)
                .setResponseCode(response.statusCode())
                .setHeaders(toHeaderMap(response.getHeaders(), blacklisted))
                .setCookies(toCookieMap(response.getDetailedCookies(), Set.of()))
                .setBody(nullToEmpty(prettifier.getPrettifiedBodyIfPossible(response, response.getBody())))
                .build();

        processor.addAttachment(responseAttachment, new FreemarkerAttachmentRenderer(RESPONSE_TEMPLATE));

        return response;
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE;
    }

    private String resolvePath(FilterableRequestSpecification requestSpec) {
        var derived = requestSpec.getDerivedPath();
        if (derived != null && !derived.isBlank()) {
            return derived;
        }
        var userDefined = requestSpec.getUserDefinedPath();
        if (userDefined != null && !userDefined.isBlank()) {
            return userDefined;
        }
        return requestSpec.getURI();
    }

    private Set<String> blacklistedHeaders(FilterableRequestSpecification requestSpec) {
        var blacklisted = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        blacklisted.addAll(requestSpec.getConfig().getLogConfig().blacklistedHeaders());
        return blacklisted;
    }

    private Map<String, String> toHeaderMap(Iterable<? extends io.restassured.internal.NameAndValue> headers,
                                            Set<String> blacklisted) {
        var map = new LinkedHashMap<String, String>();
        for (var header : headers) {
            var name = header.getName();
            map.put(name, blacklisted.contains(name) ? "[ BLACKLISTED ]" : header.getValue());
        }
        return map;
    }

    private Map<String, String> toCookieMap(Iterable<? extends io.restassured.internal.NameAndValue> cookies,
                                            Set<String> blacklisted) {
        var map = new LinkedHashMap<String, String>();
        for (var cookie : cookies) {
            var name = cookie.getName();
            map.put(name, blacklisted.contains(name) ? "[ BLACKLISTED ]" : cookie.getValue());
        }
        return map;
    }

    private Map<String, String> toStringMap(Map<String, ?> source) {
        var map = new LinkedHashMap<String, String>();
        for (var entry : source.entrySet()) {
            if (entry.getValue() != null) {
                map.put(entry.getKey(), entry.getValue().toString());
            }
        }
        return map;
    }

    private String requestBody(Prettifier prettifier, FilterableRequestSpecification requestSpec) {
        if (requestSpec.getBody() == null) {
            return "";
        }
        return nullToEmpty(prettifier.getPrettifiedBodyIfPossible(requestSpec));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
