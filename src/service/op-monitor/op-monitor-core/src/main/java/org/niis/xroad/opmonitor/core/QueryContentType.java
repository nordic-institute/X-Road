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

import lombok.experimental.UtilityClass;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Spelling of the content type of a query. Responses repeat the content type of the query in their headers and
 * MIME part headers, and monitoring clients compare those headers as text. The common media type and charset
 * combinations are therefore written in lower case, with or without a space before the charset parameter as received.
 * Any other content type is kept as received.
 */
@UtilityClass
class QueryContentType {
    private static final List<String> MEDIA_TYPES_WITH_CHARSET = List.of(
            "application/x-www-form-urlencoded", "text/html", "text/plain", "text/xml", "text/json", "application/json");
    private static final List<String> MEDIA_TYPES_WITHOUT_CHARSET = List.of(
            "message/http", "multipart/byteranges", "multipart/form-data");
    private static final List<String> CHARSETS = List.of("utf-8", "iso-8859-1");
    private static final Set<String> LOWER_CASE_SPELLINGS = lowerCaseSpellings();

    /**
     * Returns the content type in lower case when it is one of the common media type and charset combinations,
     * otherwise as received.
     *
     * @param contentType the content type of the query, may be null
     * @return the content type to process the query with
     */
    static String spell(String contentType) {
        if (contentType == null) {
            return null;
        }
        var lowerCase = contentType.toLowerCase(Locale.ROOT);
        return LOWER_CASE_SPELLINGS.contains(lowerCase) ? lowerCase : contentType;
    }

    private static Set<String> lowerCaseSpellings() {
        var spellings = new HashSet<>(MEDIA_TYPES_WITHOUT_CHARSET);
        for (var mediaType : MEDIA_TYPES_WITH_CHARSET) {
            spellings.add(mediaType);
            for (var charset : CHARSETS) {
                spellings.add(mediaType + ";charset=" + charset);
                spellings.add(mediaType + "; charset=" + charset);
            }
        }
        return Set.copyOf(spellings);
    }
}
