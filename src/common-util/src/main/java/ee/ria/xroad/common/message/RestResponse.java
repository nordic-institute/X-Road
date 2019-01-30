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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Rest message
 */
@Getter
public class RestResponse extends RestMessage {

    private final int responseCode;
    private final String reason;
    private final byte[] requestHash;

    /**
     * create response from raw messageBytes
     *
     * @param messageBytes
     */
    private RestResponse(byte[] messageBytes, String queryId, byte[] requestHash, int code, String reason,
            List<Header> headers) {
        this.messageBytes = messageBytes;
        this.queryId = queryId;
        this.requestHash = requestHash;
        this.responseCode = code;
        this.reason = reason;
        this.headers = headers;
    }

    /**
     * create response from data
     */
    public RestResponse(String queryId, byte[] requestHash, int code, String reason, List<Header> headers) {
        this.responseCode = code;
        this.reason = reason;
        this.queryId = queryId;
        this.requestHash = requestHash;
        final ArrayList<Header> tmp = headers.stream()
                .filter(h -> !SKIPPED_HEADERS.contains(h.getName().toLowerCase()))
                .filter(h -> !h.getName().equalsIgnoreCase(MimeUtils.HEADER_QUERY_ID)
                        && !h.getName().equalsIgnoreCase(MimeUtils.HEADER_REQUEST_HASH))
                .collect(Collectors.toCollection(ArrayList::new));
        tmp.add(new BasicHeader(MimeUtils.HEADER_QUERY_ID, queryId));
        tmp.add(new BasicHeader(MimeUtils.HEADER_REQUEST_HASH, CryptoUtils.encodeBase64(requestHash)));
        this.headers = tmp;
    }

    @Override
    protected byte[] toByteArray() {
        try (ByteArrayOutputStream bof = new ByteArrayOutputStream()) {

            writeString(bof, String.valueOf(responseCode));
            bof.write(CRLF);
            writeString(bof, reason);
            bof.write(CRLF);

            for (Header h : headers) {
                writeString(bof, h.getName());
                writeString(bof, ":");
                writeString(bof, h.getValue());
                bof.write(CRLF);
            }

            return bof.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * serialize
     *
     * @return
     */
    @Override
    public byte[] getFilteredMessage() {
        try (ByteArrayOutputStream bof = new ByteArrayOutputStream()) {
            writeString(bof, String.valueOf(responseCode));
            bof.write(CRLF);
            writeString(bof, reason);
            bof.write(CRLF);

            for (Header h : headers) {
                if (h.getName().toLowerCase().startsWith("x-road-")) {
                    writeString(bof, h.getName());
                    writeString(bof, ":");
                    writeString(bof, h.getValue());
                    bof.write(CRLF);
                }
            }
            return bof.toByteArray();
        } catch (Exception io) {
            throw new IllegalStateException("Unable to serialize request", io);
        }
    }

    /**
     * Parse restresponse from a byte array
     */
    @SuppressWarnings("checkstyle:magicnumber")
    public static RestResponse of(byte[] messageBytes) throws IOException {
        final BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(messageBytes), StandardCharsets.UTF_8));

        int responseCode = Integer.parseInt(reader.readLine(), 10);
        String reason = reader.readLine();
        List<Header> headers = reader.lines()
                .map(RestMessage::split)
                .map(s -> new BasicHeader(s[0], s.length > 1 ? s[1] : null))
                .collect(Collectors.toList());

        String queryId = null;
        byte[] requestHash = null;

        for (Header h : headers) {
            if (h.getName().equalsIgnoreCase(MimeUtils.HEADER_QUERY_ID)) {
                queryId = h.getValue();
            }
            if (h.getName().equalsIgnoreCase(MimeUtils.HEADER_REQUEST_HASH)) {
                requestHash = CryptoUtils.decodeBase64(h.getValue());
            }
        }

        if (queryId == null || requestHash == null || queryId.isEmpty() || requestHash.length == 0) {
            throw new IllegalArgumentException("Invalid REST Response message");
        }

        return new RestResponse(messageBytes, queryId, requestHash, responseCode, reason, headers);
    }
}
