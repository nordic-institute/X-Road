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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.identifier.XRoadId;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.MimeUtils;

import com.google.common.escape.Escaper;
import com.google.common.net.UrlEscapers;
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

    private static final int HTTP_ERROR_MIN = 400;
    private static final int HTTP_ERROR_MAX = 599;

    private final int responseCode;
    private final String reason;
    private final byte[] requestHash;
    private final ServiceId serviceId;
    private final ClientId clientId;
    private final String xRequestId;

    /**
     * create response from raw messageBytes
     * @param messageBytes
     */
    private RestResponse(byte[] messageBytes, ClientId clientId, String queryId, byte[] requestHash,
            ServiceId serviceId, int code, String reason, List<Header> headers, String xRequestId) {
        this.messageBytes = messageBytes;
        this.clientId = clientId;
        this.queryId = queryId;
        this.requestHash = requestHash;
        this.responseCode = code;
        this.reason = reason;
        this.headers = headers;
        this.serviceId = serviceId;
        this.xRequestId = xRequestId;
    }

    /**
     * create response from data
     */
    public RestResponse(ClientId clientId, String queryId, byte[] requestHash, ServiceId serviceId, int code,
            String reason, List<Header> headers, String xRequestId) {
        this.responseCode = code;
        this.reason = reason;
        this.queryId = queryId;
        this.requestHash = requestHash;
        this.serviceId = serviceId;
        this.clientId = clientId;
        this.xRequestId = xRequestId;
        final ArrayList<Header> tmp = headers.stream()
                .filter(h -> !SKIPPED_HEADERS.contains(h.getName().toLowerCase())
                        && !h.getName().equalsIgnoreCase(MimeUtils.HEADER_QUERY_ID)
                        && !h.getName().equalsIgnoreCase(MimeUtils.HEADER_REQUEST_HASH)
                        && !h.getName().equalsIgnoreCase(MimeUtils.HEADER_CLIENT_ID)
                        && !h.getName().equalsIgnoreCase(MimeUtils.HEADER_REQUEST_ID))
                .collect(Collectors.toCollection(ArrayList::new));

        tmp.add(new BasicHeader(MimeUtils.HEADER_QUERY_ID, queryId));
        tmp.add(new BasicHeader(MimeUtils.HEADER_CLIENT_ID, encodeXRoadId(clientId)));
        tmp.add(new BasicHeader(MimeUtils.HEADER_SERVICE_ID, encodeXRoadId(serviceId)));
        tmp.add(new BasicHeader(MimeUtils.HEADER_REQUEST_ID, xRequestId));
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
            serializeHeaders(headers, bof, h -> true);

            return bof.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Serialize the message including only X-Road headers
     */
    @Override
    public byte[] getFilteredMessage() {
        try (ByteArrayOutputStream bof = new ByteArrayOutputStream()) {
            writeString(bof, String.valueOf(responseCode));
            bof.write(CRLF);
            writeString(bof, reason);
            bof.write(CRLF);
            serializeHeaders(headers, bof, RestMessage::isXroadHeader);
            return bof.toByteArray();
        } catch (Exception io) {
            throw new IllegalStateException("Unable to serialize request", io);
        }
    }

    @Override
    public ClientId getSender() {
        return serviceId.getClientId();
    }

    /**
     * @return true if the HTTP response code indicates an error
     */
    public boolean isErrorResponse() {
        return responseCode >= HTTP_ERROR_MIN && responseCode <= HTTP_ERROR_MAX;
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
        ServiceId serviceId = null;
        ClientId clientId = null;
        String xRequestId = null;

        for (Header h : headers) {
            if (h.getName().equalsIgnoreCase(MimeUtils.HEADER_QUERY_ID)) {
                queryId = h.getValue();
            }
            if (h.getName().equalsIgnoreCase(MimeUtils.HEADER_REQUEST_HASH)) {
                requestHash = CryptoUtils.decodeBase64(h.getValue());
            }

            if (h.getName().equalsIgnoreCase(MimeUtils.HEADER_SERVICE_ID)) {
                serviceId = decodeServiceId(h.getValue());
            }

            if (h.getName().equalsIgnoreCase(MimeUtils.HEADER_CLIENT_ID)) {
                clientId = decodeClientId(h.getValue());
            }

            if (h.getName().equalsIgnoreCase(MimeUtils.HEADER_REQUEST_ID)) {
                xRequestId = h.getValue();
            }
        }

        if (queryId == null || requestHash == null || queryId.isEmpty() || requestHash.length == 0) {
            throw new IllegalArgumentException("Invalid REST Response message");
        }

        return new RestResponse(messageBytes, clientId, queryId, requestHash, serviceId, responseCode, reason,
                headers, xRequestId);
    }

    private static String encodeXRoadId(XRoadId xroadId) {
        final Escaper escaper = UrlEscapers.urlPathSegmentEscaper();
        StringBuilder sb = new StringBuilder();
        sb.append(escaper.escape(xroadId.getXRoadInstance()));

        final String[] parts = xroadId.getFieldsForStringFormat();
        for (String part : parts) {
            if (part != null) {
                sb.append('/');
                sb.append(escaper.escape(part));
            }
        }
        return sb.toString();
    }

}
