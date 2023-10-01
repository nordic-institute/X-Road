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

/**
 * Mime types class containing mime type constants used in MessageEncoder/Decoder and other places.
 */
public final class MimeTypes {

    public static final String OCSP_REQUEST = "application/ocsp-request";
    public static final String OCSP_RESPONSE = "application/ocsp-response";

    public static final String SIGNATURE_BDOC = "signature/bdoc-1.0/ts";

    public static final String MULTIPART_MIXED = "multipart/mixed";
    public static final String MULTIPART_RELATED = "multipart/related";

    public static final String BINARY = "application/octet-stream";

    public static final String ZIP = "application/zip";
    public static final String ASIC_ZIP = "application/vnd.etsi.asic-e+zip";

    public static final String GZIP = "application/gzip";

    public static final String HASH_CHAIN_RESULT = "application/hash-chain-result";

    public static final String HASH_CHAIN = "application/hash-chain";

    public static final String JSON = "application/json";
    public static final String JSON_RPC = "application/json-rpc";

    public static final String XOP_XML = "application/xop+xml";

    public static final String TEXT_PLAIN = "text/plain";
    public static final String TEXT_PLAIN_UTF8 = MimeUtils.contentTypeWithCharset(TEXT_PLAIN, MimeUtils.UTF8);

    public static final String TEXT_XML = "text/xml";
    public static final String TEXT_XML_UTF8 = MimeUtils.contentTypeWithCharset(TEXT_XML, MimeUtils.UTF8);

    public static final String TEXT_HTML = "text/html";
    public static final String TEXT_HTML_UTF8 = MimeUtils.contentTypeWithCharset(TEXT_HTML, MimeUtils.UTF8);

    private MimeTypes() {
    }
}
