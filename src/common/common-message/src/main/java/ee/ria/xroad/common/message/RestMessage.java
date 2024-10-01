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
package ee.ria.xroad.common.message;

import ee.ria.xroad.common.crypto.Digests;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.util.CachingStream;
import ee.ria.xroad.common.util.MimeUtils;

import lombok.Getter;
import lombok.Setter;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static ee.ria.xroad.common.util.UriUtils.uriSegmentPercentDecode;

/**
 * Base class for rest messages
 */
public abstract class RestMessage {
    public static final int PROTOCOL_VERSION = 1;

    protected static final Set<String> SKIPPED_HEADERS;
    protected static final byte[] CRLF = "\r\n".getBytes(StandardCharsets.UTF_8);

    @Getter
    protected String queryId;

    @Getter
    protected List<Header> headers;
    protected byte[] hash;
    protected byte[] messageBytes;

    @Getter
    @Setter
    private CachingStream body;

    /**
     * get digest
     */
    public byte[] getHash() {
        if (hash == null) {
            try {
                hash = Digests.calculateDigest(Digests.DEFAULT_DIGEST_ALGORITHM, getMessageBytes());
            } catch (Exception e) {
                throw new IllegalStateException("Unable to calculate hash", e);
            }
        }
        return hash;
    }

    /**
     * Get the message bytes
     */
    public byte[] getMessageBytes() {
        if (messageBytes == null) {
            messageBytes = toByteArray();
        }
        return messageBytes;
    }

    /**
     * Sets queryId
     */
    public void setQueryId(String queryId) {
        if (this.queryId == null) {
            this.queryId = queryId;
            this.headers.add(new BasicHeader(MimeUtils.HEADER_QUERY_ID, queryId));
        } else {
            throw new IllegalStateException("Can not change queryId");
        }
    }

    /**
     * Finds header value as a String
     *
     * @param name http header name
     * @return http header value as a String or null if header not found
     */
    public String findHeaderValueByName(String name) {
        Optional<Header> header = headers.stream()
                .filter(h -> h.getName().equalsIgnoreCase(name))
                .findFirst();
        return header.map(Header::getValue).orElse(null);
    }

    protected abstract byte[] toByteArray();

    public abstract byte[] getFilteredMessage();

    public abstract ClientId getSender();

    /**
     * Create rest message from message bytes
     *
     * @param messageBytes
     * @return parsed rest message (response or request)
     * @see RestResponse
     * @see RestRequest
     */
    public static RestMessage of(byte[] messageBytes) throws Exception {
        if (messageBytes != null && messageBytes.length > 0) {
            if (messageBytes[0] >= '1' && messageBytes[0] <= '9') {
                return RestResponse.of(messageBytes);
            } else {
                return new RestRequest(messageBytes);
            }
        } else {
            throw new IllegalArgumentException("Invalid message");
        }
    }

    static String[] split(String header) {
        if (header == null || header.isEmpty()) {
            throw new IllegalArgumentException("Invalid header");
        }
        final int i = header.indexOf(':');

        if (i < 0) {
            throw new IllegalArgumentException("Invalid header");
        }

        String[] result = new String[2];
        result[0] = header.substring(0, i);

        if (SKIPPED_HEADERS.contains(result[0].toLowerCase())) {
            throw new IllegalArgumentException("Invalid header: " + result[0]);
        }

        result[1] = header.substring(i + 1);
        return result;
    }

    @SuppressWarnings("checkstyle:magicnumber")
    static ServiceId decodeServiceId(String value) {
        final String[] parts = value.split("/", 6);
        if (parts.length != 5) {
            throw new IllegalArgumentException("Invalid Service Id");
        }
        return ServiceId.Conf.create(
                uriSegmentPercentDecode(parts[0]),
                uriSegmentPercentDecode(parts[1]),
                uriSegmentPercentDecode(parts[2]),
                uriSegmentPercentDecode(parts[3]),
                uriSegmentPercentDecode(parts[4])
        );
    }

    @SuppressWarnings("checkstyle:magicnumber")
    public static ClientId decodeClientId(String value) {
        final String[] parts = value.split("/", 5);
        if (parts.length < 3 || parts.length > 4) {
            throw new IllegalArgumentException("Invalid Client Id");
        }
        return ClientId.Conf.create(
                uriSegmentPercentDecode(parts[0]),
                uriSegmentPercentDecode(parts[1]),
                uriSegmentPercentDecode(parts[2]),
                parts.length == 4 ? uriSegmentPercentDecode(parts[3]) : null
        );
    }

    static void serializeHeaders(List<Header> headers, OutputStream os, Predicate<Header> filter) throws IOException {
        headers.stream()
                .forEach(h -> {
                    if (filter.test(h)) {
                        try {
                            writeString(os, h.getName());
                            writeString(os, ":");
                            writeString(os, h.getValue());
                            os.write(CRLF);
                        } catch (IOException e) {
                            throw new IllegalStateException("Unable to serialize headers", e);
                        }
                    }
                });
    }

    static boolean isXroadHeader(Header h) {
        return h != null && h.getName() != null && h.getName().toLowerCase().startsWith("x-road-");
    }

    static void writeString(OutputStream os, String s) throws IOException {
        os.write(s.getBytes(StandardCharsets.UTF_8));
    }

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
        tmp.add("user-agent");
        tmp.add("host");
        tmp.add("content-length");
        tmp.add("server");
        tmp.add("expect");
        SKIPPED_HEADERS = Collections.unmodifiableSet(tmp);
    }


    public static final class HeadersComparator implements Comparator<Header> {

        @Override
        public int compare(Header h1, Header h2) {
            int nameCompare = h1.getName().compareToIgnoreCase(h2.getName());
            if (nameCompare != 0) {
                return nameCompare;
            }
            if (h1.getValue() == null) {
                return h2.getValue() == null ? 0 : -1;
            }
            if (h2.getValue() == null) {
                return 1;
            }
            return h1.getValue().compareTo(h2.getValue());
        }

    }

}
