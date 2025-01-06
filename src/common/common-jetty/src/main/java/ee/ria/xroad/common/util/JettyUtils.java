/*
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
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
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

import java.util.Optional;

public final class JettyUtils {

    public static String getContentType(final Request request) {
        return Optional.of(request)
                .map(Request::getHeaders)
                .map(headers -> headers.get(HttpHeader.CONTENT_TYPE))
                .orElse(null);
    }

    public static String getCharacterEncoding(final Request request) {
        return Optional.of(request)
                .map(Request::getHeaders)
                .map(headers -> headers.get(HttpHeader.CONTENT_TYPE))
                .map(MimeTypes::getCharsetFromContentType)
                .orElse(null);
    }

    public static int getContentLength(final Request request) {
        return Optional.of(request)
                .map(Request::getHeaders)
                .map(headers -> headers.get(HttpHeader.CONTENT_LENGTH))
                .map(Integer::parseInt)
                .orElse(-1);
    }

    public static String getTarget(final Request request) {
        return Optional.of(request)
                .map(Request::getHttpURI)
                .map(HttpURI::getPath)
                .orElse(null);
    }

    public static String getTarget(final RequestWrapper request) {
        return Optional.of(request)
                .map(RequestWrapper::getHttpURI)
                .map(HttpURI::getPath)
                .orElse(null);
    }

    public static void setContentType(final Response response, final MimeTypes.Type contentType) {
        setContentType(response, contentType == null ? null : contentType.asString());
    }

    public static void setContentType(final Response response, final String contentType) {
        response.getHeaders().put(HttpHeader.CONTENT_TYPE, contentType);
    }

    /**
     * Set content-type with charset, if provided content-type value already has charset then provided one is ignored.
     *
     * @param contentType content-type value
     * @param charset     Charset to add
     */
    public static void setContentType(final Response response, final String contentType, final String charset) {
        if (StringUtils.isBlank(contentType)) {
            setContentType(response, (String) null);
            return;
        }

        if (StringUtils.isBlank(charset)) {
            setContentType(response, contentType);
            return;
        }

        var currentCharset = Optional.ofNullable(MimeTypes.getCharsetFromContentType(contentType))
                .or(() -> Optional.ofNullable(MimeTypes.getBaseType(contentType))
                        .filter(MimeTypes.Type::isCharsetAssumed)
                        .map(MimeTypes.Type::getCharsetString))
                .orElse(null);

        if (charset.equalsIgnoreCase(currentCharset)) {
            setContentType(response, contentType);
        } else {
            setContentType(response, MimeTypes.getContentTypeWithoutCharset(contentType)
                    + ";charset="
                    + MimeTypes.normalizeCharset(charset));
        }

    }

    public static void setContentLength(final Response response, final int length) {
        response.getHeaders().put(HttpHeader.CONTENT_LENGTH, length);
    }

    private JettyUtils() {
    }
}
