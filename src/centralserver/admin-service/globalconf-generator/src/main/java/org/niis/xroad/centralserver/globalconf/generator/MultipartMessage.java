/*
 * The MIT License
 *
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
package org.niis.xroad.centralserver.globalconf.generator;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.List;

@Value
@Builder
class MultipartMessage {
    private static final String CRLF = "\r\n";
    public static final int BOUNDARY_LENGTH = 20;
    @Singular
    List<Part> parts;
    String boundary;
    String contentType;

    MultipartMessage(List<Part> parts, String boundary, String contentType) {
        if (parts == null || parts.isEmpty()) {
            throw new RuntimeException("At least one part is required");
        }
        this.parts = parts;
        this.boundary = boundary != null ? boundary : RandomStringUtils.randomAlphanumeric(BOUNDARY_LENGTH);
        this.contentType = contentType != null ? contentType : "multipart/mixed";
    }

    public String toString() {
        var header = header("Content-Type",
                String.format("%s; charset=UTF-8; boundary=%s", contentType, boundary));

        var sb = new StringBuilder(header.toString());
        sb.append(CRLF);
        parts.forEach(p -> {
            sb.append(CRLF).append("--").append(boundary).append(CRLF);
            sb.append(p.toString());
        });
        sb.append(CRLF).append("--").append(boundary).append("--").append(CRLF);
        return sb.toString();
    }

    static Header header(String name, String value) {
        return new Header(name, value);
    }

    static SimplePart.SimplePartBuilder partBuilder() {
        return SimplePart.builder();
    }

    static Part rawPart(String content) {
        return new RawPart(content);
    }

    @Value
    static class Header {
        String name;
        String value;

        public String toString() {
            return name + ": " + value;
        }
    }

    interface Part {
        String toString();
    }

    @Value
    @Builder
    static class SimplePart implements Part {
        @Singular
        List<Header> headers;
        String content;

        public String toString() {
            StringBuilder sb = new StringBuilder();
            headers.forEach(h -> sb.append(h).append(CRLF));

            if (content != null) {
                sb.append(CRLF);
                sb.append(content);
            }
            return sb.toString();
        }
    }

    @Value
    private static class RawPart implements Part {
        String content;

        public String toString() {
            return content;
        }
    }

}
