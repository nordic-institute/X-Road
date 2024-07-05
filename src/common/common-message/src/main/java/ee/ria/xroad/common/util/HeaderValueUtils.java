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
package ee.ria.xroad.common.util;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.http.HttpField;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Various MIME related utility methods.
 */
public final class HeaderValueUtils {
    public static final String HEADER_CONTENT_TYPE = "content-type";

    public static final String UTF8 = StandardCharsets.UTF_8.name();

    private HeaderValueUtils() {
    }

    /**
     * Returns the charset from the given mime type.
     *
     * @param contentType content type from which to extract the charset
     * @return String
     */
    public static String getCharset(String contentType) {
        return getParameterValue(contentType, "charset");
    }

    /**
     * Returns true, if content type has UTF-8 charset or "charset" is not set.
     *
     * @param contentType content type from which to extract the charset
     * @return true, if content type has UTF-8 charset or "charset" is not set
     */
    public static boolean hasUtf8Charset(String contentType) {
        String charset = getCharset(contentType);

        return StringUtils.isBlank(charset) || charset.equalsIgnoreCase(UTF8);
    }

    /**
     * Checks whether the specified content type contains a boundary.
     *
     * @param contentType the content type to check
     * @return boolean
     */
    public static boolean hasBoundary(String contentType) {
        return getBoundary(contentType) != null;
    }

    /**
     * Returns boundary from the content type.
     *
     * @param contentType the content type to check
     * @return boundary from the content type or null if the content type
     * does not contain boundary
     */
    public static String getBoundary(String contentType) {
        return getParameterValue(contentType, "boundary");
    }

    private static String getParameterValue(String contentType, String parameterName) {
        Map<String, String> params = new HashMap<>();
        HttpField.getValueParameters(contentType, params);

        return params.entrySet().stream()
                .filter(e -> e.getKey().trim().equalsIgnoreCase(parameterName))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}

