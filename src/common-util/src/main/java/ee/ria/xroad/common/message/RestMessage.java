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

import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.MimeUtils;

import lombok.Getter;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Base class for rest messages
 */
public abstract class RestMessage {
    public static final Set<String> SKIPPED_HEADERS;

    public static final byte[] CRLF = "\r\n".getBytes(StandardCharsets.UTF_8);

    @Getter
    protected String queryId;

    @Getter
    protected List<Header> headers;
    protected byte[] hash;
    protected byte[] messageBytes;

    /**
     * get digest
     */
    public byte[] getHash() {
        if (hash == null) {
            try {
                hash = CryptoUtils.calculateDigest(CryptoUtils.DEFAULT_DIGEST_ALGORITHM_ID, getMessageBytes());
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

    protected abstract byte[] toByteArray();

    public abstract byte[] getFilteredMessage();

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
        tmp.add("pragma");
        tmp.add("user-agent");
        tmp.add("host");
        tmp.add("content-length");
        tmp.add("server");
        tmp.add("expect");
        SKIPPED_HEADERS = Collections.unmodifiableSet(tmp);
    }

}
