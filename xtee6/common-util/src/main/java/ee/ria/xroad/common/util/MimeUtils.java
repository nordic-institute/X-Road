/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.RandomStringUtils;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.util.MultiPartWriter;

/**
 * Various MIME related utility methods.
 */
public final class MimeUtils {

    private static final int RANDOM_BOUNDARY_LENGTH = 30;

    public static final String HEADER_CONTENT_TYPE = "content-type";
    public static final String HEADER_CONTENT_DATE = "content-date";
    public static final String HEADER_SIG_ALGO_ID = "signature-algorithm-id";
    public static final String HEADER_HASH_ALGO_ID = "x-hash-algorithm";
    public static final String HEADER_PROXY_VERSION = "x-proxy-version";
    public static final String HEADER_CONTENT_TRANSFER_ENCODING =
            "content-transfer-encoding";
    public static final String HEADER_VERIFICATION_CERT_HASH =
            "verification-certificate-hash";
    public static final String HEADER_CONTENT_IDENTIFIER = "content-identifier";
    public static final String HEADER_CONTENT_LOCATION = "content-location";
    public static final String HEADER_HASH_ALGORITHM_ID = "hash-algorithm-id";
    public static final String HEADER_CONTENT_FILE_NAME = "content-file-name";
    public static final String HEADER_EXPIRE_DATE = "expire-date";
    public static final String PARAM_INSTANCE = "instance";

    public static final String HASH_CHAIN_CONTENT_TYPE =
            "application/hash-chain";
    public static final String HASH_CHAIN_RESULT_CONTENT_TYPE =
            "application/hash-chain-result";

    public static final String UTF8 = StandardCharsets.UTF_8.name();

    /** text/xml; charset="UTF-8" */
    public static final String TEXT_XML_UTF8 =
            contentTypeWithCharset(MimeTypes.TEXT_XML, UTF8);

    private MimeUtils() {
    }

    /**
     * Constructs content-type string for multipart/mixed content with given
     * boundary.
     * @param boundary boundary to be used in the content-type construction
     * @return String
     */
    public static String mpMixedContentType(String boundary) {
        return contentTypeWithCharsetAndBoundary(
                MultiPartWriter.MULTIPART_MIXED, UTF8, boundary);
    }

    /**
     * Constructs content-type string for multipart/related content with given
     * boundary.
     * @param boundary boundary to be used in the content-type construction
     * @return String
     */
    public static String mpRelatedContentType(String boundary) {
        return contentTypeWithCharsetAndBoundary(MimeTypes.MULTIPART_RELATED,
                UTF8, boundary);
    }

    /**
     * Constructs a content type with given type, charset and boundary.
     * @param mimeType mime type to be used in the content-type construction
     * @param charset charset to be used in the content-type construction
     * @param boundary boundary to be used in the content-type construction
     * @return String
     */
    public static String contentTypeWithCharsetAndBoundary(String mimeType,
            String charset, String boundary) {
        return mimeType + "; charset=" + charset + "; boundary=" + boundary;
    }

    /**
     * Constructs a content type with given type, charset.
     * @param mimeType mime type to be used in the content-type construction
     * @param charset charset to be used in the content-type construction
     * @return String
     */
    public static String contentTypeWithCharset(String mimeType,
            String charset) {
        return mimeType + "; charset=" + charset;
    }

    /**
     * Returns base content type without the modifiers.
     * @param mimeType mime type from which to extract the base content type
     * @return String
     */
    public static String getBaseContentType(String mimeType) {
        return HttpFields.valueParameters(mimeType, null);
    }

    /**
     * Returns the charset from the given mime type.
     * @param mimeType mime type from which to extract the charset
     * @return String
     */
    public static String getCharset(String mimeType) {
        Map<String, String> params = new HashMap<>();
        HttpFields.valueParameters(mimeType, params);
        return params.get("charset");
    }

    /**
     * Checks whether the specified content type contains a boundary.
     * @param contentType the content type to check
     * @return boolean
     */
    public static boolean hasBoundary(String contentType) {
        Map<String, String> map = new HashMap<>();
        HttpFields.valueParameters(contentType.toLowerCase(), map);
        return map.containsKey("boundary");
    }

    /**
     * Converts a map of header key-values to header array with
     * colon-separated values.
     * @param headers the map of header key-values
     * @return array of color-separated Strings
     */
    public static String[] toHeaders(Map<String, String> headers) {
        String[] result = null;
        if (headers != null && !headers.isEmpty()) {
            result = new String[headers.size()];
            int i = 0;
            for (Map.Entry<String, String> h: headers.entrySet()) {
                result[i++] = h.getKey() + ": " + h.getValue();
            }
        }

        return result;
    }

    /**
     * @return random boundary for use with MIME multiparts.
     */
    public static String randomBoundary() {
        return RandomStringUtils.randomAlphabetic(RANDOM_BOUNDARY_LENGTH);
    }
}

