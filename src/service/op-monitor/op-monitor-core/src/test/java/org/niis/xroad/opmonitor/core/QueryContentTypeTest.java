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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class QueryContentTypeTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "text/xml; charset=UTF-8", "text/xml;charset=UTF-8", "TEXT/XML; CHARSET=UTF-8",
            "text/xml; charset=ISO-8859-1", "text/xml", "Text/Xml", "text/plain; charset=UTF-8", "application/json;charset=UTF-8"})
    void commonMediaTypeAndCharsetCombinationsAreSpelledInLowerCase(String contentType) {
        assertThat(QueryContentType.spell(contentType)).isEqualTo(contentType.toLowerCase());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "application/json", "application/json; charset=iso-8859-1", "application/json; charset=utf-8",
            "application/json;charset=iso-8859-1", "application/json;charset=utf-8",
            "application/x-www-form-urlencoded", "application/x-www-form-urlencoded; charset=iso-8859-1",
            "application/x-www-form-urlencoded; charset=utf-8", "application/x-www-form-urlencoded;charset=iso-8859-1",
            "application/x-www-form-urlencoded;charset=utf-8",
            "message/http", "multipart/byteranges", "multipart/form-data",
            "text/html", "text/html; charset=iso-8859-1", "text/html; charset=utf-8",
            "text/html;charset=iso-8859-1", "text/html;charset=utf-8",
            "text/json", "text/json; charset=iso-8859-1", "text/json; charset=utf-8",
            "text/json;charset=iso-8859-1", "text/json;charset=utf-8",
            "text/plain", "text/plain; charset=iso-8859-1", "text/plain; charset=utf-8",
            "text/plain;charset=iso-8859-1", "text/plain;charset=utf-8",
            "text/xml", "text/xml; charset=iso-8859-1", "text/xml; charset=utf-8",
            "text/xml;charset=iso-8859-1", "text/xml;charset=utf-8"})
    void everyContentTypeSpellingOfTheFormerHttpServerIsReproducedFromUpperCase(String httpServerSpelling) {
        assertThat(QueryContentType.spell(httpServerSpelling.toUpperCase(Locale.ROOT))).isEqualTo(httpServerSpelling);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "multipart/related; type=\"text/xml\"; charset=UTF-8; boundary=Abc",
            "text/xml;  charset=UTF-8", "text/xml; charset=UTF-16", "text/xml; charset=UTF-8; version=1",
            "application/soap+xml; charset=UTF-8"})
    void otherContentTypesAreKeptAsReceived(String contentType) {
        assertThat(QueryContentType.spell(contentType)).isSameAs(contentType);
    }

    @Test
    void missingContentTypeStaysMissing() {
        assertThat(QueryContentType.spell(null)).isNull();
    }
}
