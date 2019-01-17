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
import ee.ria.xroad.common.util.MimeUtils;

import lombok.Getter;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.util.UriUtils.uriSegmentPercentDecode;

/**
 * Rest message
 */
@Getter
public class RestRequest {

    private ClientId client;
    private ServiceId requestServiceId;
    private final String verb;
    private String requestPath;
    private String query;
    private String servicePath;
    private final List<Header> headers;
    private byte[] hash;
    private String messageId;
    private int version;

    /**
     * Create RestRequest from a byte array
     *
     * @param messageBytes
     */
    public RestRequest(byte[] messageBytes) throws Exception {
        final BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(messageBytes), StandardCharsets.UTF_8));

        verb = reader.readLine();
        final URI uri = new URI(reader.readLine());
        requestPath = uri.getRawPath();
        query = uri.getRawQuery();
        headers = reader.lines()
                .map(RestResponse::split)
                .filter(s -> s.length > 0 && !SKIPPED_HEADERS.contains(s[0].toLowerCase()))
                .map(s -> new BasicHeader(s[0], s.length > 1 ? s[1] : null))
                .collect(Collectors.toList());

        decodeIdentifiers();
        hash = CryptoUtils.calculateDigest(CryptoUtils.DEFAULT_DIGEST_ALGORITHM_ID, messageBytes);
    }

    /**
     * Create RestRequest
     */
    public RestRequest(String verb, String path, String query, List<Header> headers) throws Exception {
        this.verb = verb;
        this.headers = headers;
        this.requestPath = path;
        this.query = query;
        decodeIdentifiers();
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
            writeString(bof, requestPath);
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

    @SuppressWarnings({"checkstyle:magicnumber", "checkstyle:innerassignment"})
    private void decodeIdentifiers() {
        if (requestPath == null) {
            throw new IllegalArgumentException("Request uri must not be null");
        }

        for (Header h : headers) {
            if (MimeUtils.HEADER_CLIENT_ID.equalsIgnoreCase(h.getName()) && h.getValue() != null) {
                final String[] parts = h.getValue().split("/", 5);
                if (parts.length != 4) {
                    throw new IllegalArgumentException("Invalid Client Id");
                }
                client = ClientId.create(
                        uriSegmentPercentDecode(parts[0]),
                        uriSegmentPercentDecode(parts[1]),
                        uriSegmentPercentDecode(parts[2]),
                        uriSegmentPercentDecode(parts[3])
                );
            } else if (MimeUtils.HEADER_MESSAGE_ID.equalsIgnoreCase(h.getName())) {
                this.messageId = h.getValue();
            }
            //TBD optional header values
        }

        final String[] parts = requestPath.split("/", 8);
        if (parts.length < 7) {
            throw new IllegalArgumentException("Invalid request URI");
        }

        final int digit;
        if (parts[1].length() == 2 && parts[1].charAt(0) == 'r'
                && (digit = Character.digit(parts[1].charAt(1), 10)) != -1) {
            version = digit;
        } else {
            throw new IllegalArgumentException("Invalid version");
        }

        requestServiceId = ServiceId.create(
                uriSegmentPercentDecode(parts[2]),
                uriSegmentPercentDecode(parts[3]),
                uriSegmentPercentDecode(parts[4]),
                uriSegmentPercentDecode(parts[5]),
                uriSegmentPercentDecode(parts[6]));

        if (parts.length == 8) {
            servicePath = "/" + parts[7];
        } else {
            servicePath = "";
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
