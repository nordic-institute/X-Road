package ee.cyber.xroad.common.util;

/**
 * Extends jetty MimeTypes class, adding some other mime type constants
 * used in MessageEncoder/Decoder.
 */
public class MimeTypes extends org.eclipse.jetty.http.MimeTypes {

    public static final String OCSP_REQUEST = "application/ocsp-request";
    public static final String OCSP_RESPONSE = "application/ocsp-response";

    public static final String SIGNATURE_BDOC = "signature/bdoc-1.0/ts";

    public static final String MULTIPART_MIXED = "multipart/mixed";
    public static final String MULTIPART_RELATED = "multipart/related";

    public static final String BINARY = "application/octet-stream";

    public static final String HASH_CHAIN_RESULT =
            "application/hash-chain-result";

    public static final String HASH_CHAIN = "application/hash-chain";
}
