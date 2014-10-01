package ee.cyber.sdsb.common.util;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.RandomStringUtils;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.util.MultiPartWriter;

/**
 * Various utility methods.
 */
public class MimeUtils {

    public static final String HEADER_CONTENT_TYPE = "content-type";
    public static final String HEADER_CONTENT_DATE = "content-date";
    public static final String HEADER_SIG_ALGO_ID = "signature-algorithm-id";
    public static final String HEADER_HASH_ALGO_ID = "x-hash-algorithm";
    public static final String HEADER_PROXY_VERSION = "x-proxy-version";

    public static final String HASH_CHAIN_CONTENT_TYPE =
            "application/hash-chain";
    public static final String HASH_CHAIN_RESULT_CONTENT_TYPE =
            "application/hash-chain-result";

    public static final String UTF8 = StandardCharsets.UTF_8.name();

    /** text/xml; charset="UTF-8" */
    public static final String TEXT_XML_UTF8 =
            contentTypeWithCharset(MimeTypes.TEXT_XML, UTF8);

    /**
     * Constructs content-type string for multipart/mixed content with given
     * boundary.
     */
    public static String mpMixedContentType(String boundary) {
        return contentTypeWithCharsetAndBoundary(
                MultiPartWriter.MULTIPART_MIXED, UTF8, boundary);
    }

    /**
     * Constructs content-type string for multipart/related content with given
     * boundary.
     */
    public static String mpRelatedContentType(String boundary) {
        return contentTypeWithCharsetAndBoundary(MimeTypes.MULTIPART_RELATED,
                UTF8, boundary);
    }

    /** Constructs a content type with given type, charset and boundary. */
    public static String contentTypeWithCharsetAndBoundary(String mimeType,
            String charset, String boundary) {
        return mimeType + "; charset=" + charset + "; boundary=" + boundary;
    }

    /** Constructs a content type with given type, charset and boundary. */
    public static String contentTypeWithCharset(String mimeType,
            String charset) {
        return mimeType + "; charset=" + charset;
    }

    /** Returns base content type without the modifiers. */
    public static String getBaseContentType(String mimeType) {
        return HttpFields.valueParameters(mimeType, null);
    }

    /**
     * Returns the charset from the given mime type.
     */
    public static String getCharset(String mimeType) {
        Map<String, String> params = new HashMap<>();
        HttpFields.valueParameters(mimeType, params);
        return params.get("charset");
    }

    /**
     * Checks whether the specified content type contains a boundary.
     */
    public static boolean hasBoundary(String contentType) {
        Map<String, String> map = new HashMap<>();
        HttpFields.valueParameters(contentType.toLowerCase(), map);
        return map.containsKey("boundary");
    }

    /**
     * Converts a map of header key-values to header array with
     * colon-separated values.
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

    /** Generates random boundary for use with MIME multiparts. */
    public static String randomBoundary() {
        return RandomStringUtils.randomAlphabetic(30);
    }
}

