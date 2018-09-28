/**
 * The MIT License
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
package ee.ria.xroad.common.message;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.Getter;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rest message
 */
@Getter
public class RestRequest {

    private ClientId client;
    private ServiceId requestServiceId;
    private final String verb;
    private final URI uri;
    private String path;
    private final List<Header> headers;
    private byte[] hash;

    /**
     * Create RestRequest from a byte array
     *
     * @param messageBytes
     */
    public RestRequest(byte[] messageBytes) throws Exception {
        final BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(messageBytes), StandardCharsets.UTF_8));

        verb = reader.readLine();
        uri = new URI(reader.readLine());
        headers = reader.lines()
                .map(RestResponse::split)
                .filter(s -> s.length > 0 && !SKIPPED_HEADERS.contains(s[0].toLowerCase()))
                .map(s -> new BasicHeader(s[0], s.length > 1 ? s[1] : null))
                .collect(Collectors.toList());
        decodeUri();
        hash = CryptoUtils.calculateDigest(CryptoUtils.DEFAULT_DIGEST_ALGORITHM_ID, messageBytes);
    }

    /**
     * Create RestRequest
     */
    public RestRequest(String verb, String uri, List<Header> headers) throws Exception {
        this.verb = verb;
        this.headers = headers;
        this.uri = new URI(uri);
        decodeUri();
    }

    /**
     * get digest
     *
     * @return
     */
    public byte[] getHash() {
        if (hash == null) {
            try {
                hash = CryptoUtils.calculateDigest(CryptoUtils.DEFAULT_DIGEST_ALGORITHM_ID, toByteArray());
            } catch (Exception e) {
                throw new IllegalStateException("Unable to calculate hash", e);
            }
        }
        return hash;
    }

    /**
     * serialize
     *
     * @return
     */
    public byte[] toByteArray() {
        try (ByteArrayOutputStream bof = new ByteArrayOutputStream()) {
            writeString(bof, verb);
            bof.write(CRLF);
            writeString(bof, uri.getRawPath());
            final String query = uri.getRawQuery();
            if (query != null) {
                writeString(bof, "?");
                writeString(bof, query);
            }
            bof.write(CRLF);

            for (Header h : headers) {
                if (SKIPPED_HEADERS.contains(h.getName().toLowerCase())) continue;
                writeString(bof, h.getName());
                writeString(bof, ":");
                writeString(bof, h.getValue());
                bof.write(CRLF);
            }
            final byte[] bytes = bof.toByteArray();
            hash = CryptoUtils.calculateDigest(CryptoUtils.DEFAULT_DIGEST_ALGORITHM_ID, bytes);
            return bytes;
        } catch (Exception io) {
            throw new IllegalStateException("Unable to serialize request", io);
        }
    }

    @SuppressWarnings("checkstyle:magicnumber")
    private void decodeUri() {
        if (uri == null) {
            throw new IllegalArgumentException("Request uri must not be null");
        }
        final String[] parts = uri.getRawPath().split("/");
        if (parts.length < 12) {
            throw new IllegalArgumentException("Invalid request URI");
        }

        client = ClientId.create(
                urlDecode(parts[3]),
                urlDecode(parts[4]),
                urlDecode(parts[5]),
                urlDecode(parts[6]));

        requestServiceId = ServiceId.create(
                urlDecode(parts[7]),
                urlDecode(parts[8]),
                urlDecode(parts[9]),
                urlDecode(parts[10]),
                urlDecode(parts[11]));

        if (parts.length > 12) {
            path = String.join("/", Arrays.copyOfRange(parts, 12, parts.length));
        } else {
            path = "";
        }
    }

    private String urlDecode(String part) {
        try {
            return URLDecoder.decode(part, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF 8 not supported, should not happen");
        }
    }

    private static void writeString(OutputStream os, String s) throws IOException {
        os.write(s.getBytes(StandardCharsets.UTF_8));
    }

    private static final byte[] CRLF = "\r\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SPACE = " ".getBytes(StandardCharsets.UTF_8);
    public static final Set<String> SKIPPED_HEADERS;

    static {
        final HashSet<String> tmp = new HashSet<>();
        tmp.add("transfer-encoding");
        tmp.add("keep-alive");
        tmp.add("proxy-authenticate");
        tmp.add("proxy-authorization");
        tmp.add("te");
        tmp.add("trailer");
        tmp.add("upgrade");
        tmp.add("connection");
        tmp.add("pragma");
        tmp.add("user-agent");
        tmp.add("host");
        tmp.add("content-length");
        tmp.add("server");
        tmp.add("expect");
        SKIPPED_HEADERS = Collections.unmodifiableSet(tmp);
    }
}
