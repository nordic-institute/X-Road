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
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.util.MimeUtils;

import lombok.Getter;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.util.UriUtils.uriSegmentPercentDecode;

/**
 * Rest message
 */
@Getter
public class RestRequest extends RestMessage {

    private ClientId clientId;
    private ServiceId serviceId;
    private final Verb verb;
    private String requestPath;
    private String query;
    private String servicePath;
    private SecurityServerId targetSecurityServer;
    private int version;
    private String xRequestId;
    private RepresentedParty representedParty;

    /**
     * Supported HTTP Verbs
     */
    public enum Verb {
        DELETE, GET, HEAD, OPTIONS, PATCH, POST, PUT, TRACE
    }

    /**
     * Create RestRequest from a byte array
     */
    public RestRequest(byte[] messageBytes) throws Exception {
        final BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(messageBytes), StandardCharsets.UTF_8));

        verb = Verb.valueOf(reader.readLine());
        final URI uri = new URI(reader.readLine());

        if (uri.getScheme() != null || uri.getAuthority() != null) {
            throw new IllegalArgumentException("Invalid request URI");
        }

        requestPath = uri.getRawPath();
        query = uri.getRawQuery();
        headers = reader.lines()
                .map(RestResponse::split)
                .map(s -> new BasicHeader(s[0], s.length > 1 ? s[1] : null))
                .collect(Collectors.toList());

        decodeIdentifiers();
    }

    /**
     * Create RestRequest
     */
    public RestRequest(String verb, String path, String query, List<Header> headers, String xRequestId) {
        this.verb = Verb.valueOf(verb);
        this.headers = headers.stream()
                .filter(h -> !SKIPPED_HEADERS.contains(h.getName().toLowerCase())
                        && !MimeUtils.HEADER_REQUEST_ID.equalsIgnoreCase(h.getName())
                        && !MimeUtils.HEADER_REQUEST_HASH.equalsIgnoreCase(h.getName()))
                .collect(Collectors.toCollection(ArrayList::new));
        this.requestPath = path;
        this.query = query;
        this.xRequestId = xRequestId;
        this.headers.add(new BasicHeader(MimeUtils.HEADER_REQUEST_ID, xRequestId));
        decodeIdentifiers();
    }

    /**
     * serialize
     * @return
     */
    @Override
    protected byte[] toByteArray() {
        try (ByteArrayOutputStream bof = new ByteArrayOutputStream()) {
            writeString(bof, verb.toString());
            bof.write(CRLF);
            writeString(bof, requestPath);
            if (query != null) {
                writeString(bof, "?");
                writeString(bof, query);
            }
            bof.write(CRLF);
            serializeHeaders(headers, bof, h -> true);
            return bof.toByteArray();
        } catch (Exception io) {
            throw new IllegalStateException("Unable to serialize request", io);
        }
    }

    /**
     * serialize
     * @return
     */
    @Override
    public byte[] getFilteredMessage() {
        try (ByteArrayOutputStream bof = new ByteArrayOutputStream()) {
            writeString(bof, verb.toString());
            bof.write(CRLF);
            if (servicePath.isEmpty()) {
                writeString(bof, requestPath);
            } else {
                writeString(bof, requestPath.substring(0, requestPath.length() - servicePath.length()));
            }
            bof.write(CRLF);
            serializeHeaders(headers, bof, RestMessage::isXroadHeader);
            return bof.toByteArray();
        } catch (Exception io) {
            throw new IllegalStateException("Unable to serialize request", io);
        }
    }

    @Override
    public ClientId getSender() {
        return clientId;
    }

    @SuppressWarnings(value = {"checkstyle:magicnumber", "checkstyle:innerassignment"})
    private void decodeIdentifiers() {
        if (requestPath == null) {
            throw new IllegalArgumentException("Request uri must not be null");
        }

        final String[] parts = requestPath.split("/", 8);
        if (parts.length < 7) {
            throw new IllegalArgumentException("Invalid request URI");
        }

        final int digit;
        if (parts[1].length() == 2 && parts[1].charAt(0) == 'r'
                && (digit = Character.digit(parts[1].charAt(1), 10)) != -1) {
            version = digit;

            if (version != PROTOCOL_VERSION) {
                throw new IllegalArgumentException("Unsupported protocol version " + version);
            }
        } else {
            throw new IllegalArgumentException("Invalid protocol version " + parts[1]);
        }

        serviceId = ServiceId.Conf.create(
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

        decodeHeaders();
    }

    private void decodeHeaders() {
        for (Header h : this.headers) {
            if (MimeUtils.HEADER_CLIENT_ID.equalsIgnoreCase(h.getName()) && h.getValue() != null) {
                this.clientId = decodeClientId(h.getValue());
            } else if (MimeUtils.HEADER_QUERY_ID.equalsIgnoreCase(h.getName())) {
                this.queryId = h.getValue();
            } else if (MimeUtils.HEADER_REQUEST_ID.equals(h.getName())) {
                this.xRequestId = h.getValue();
            } else if (MimeUtils.HEADER_SECURITY_SERVER.equalsIgnoreCase(h.getName()) && h.getValue() != null) {
                this.targetSecurityServer = decodeServerId(h.getValue());
            } else if (MimeUtils.HEADER_REPRESENTED_PARTY.equalsIgnoreCase(h.getName()) && h.getValue() != null) {
                this.representedParty = decodeRepresentedParty(h.getValue());
            }
        }
    }

    @SuppressWarnings(value = {"checkstyle:magicnumber"})
    private static SecurityServerId decodeServerId(String value) {
        final String[] parts = value.split("/", 5);
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid SecurityServer Id");
        }
        return SecurityServerId.Conf.create(
                uriSegmentPercentDecode(parts[0]),
                uriSegmentPercentDecode(parts[1]),
                uriSegmentPercentDecode(parts[2]),
                uriSegmentPercentDecode(parts[3])
        );
    }

    @SuppressWarnings(value = {"checkstyle:magicnumber"})
    private static RepresentedParty decodeRepresentedParty(String value) {
        final String[] parts = value.split("/");
        if (parts.length > 2) {
            throw new IllegalArgumentException("Invalid RepresentedParty Id");
        }
        switch (parts.length) {
            case 1:
                return new RepresentedParty(
                        null,
                        uriSegmentPercentDecode(parts[0])
                );
            case 2:
                return new RepresentedParty(
                        uriSegmentPercentDecode(parts[0]),
                        uriSegmentPercentDecode(parts[1])
                );
            default:
                return null;
        }
    }
}
