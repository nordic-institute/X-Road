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
package org.niis.xroad.cs.admin.globalconf.generator;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.apache.commons.lang3.RandomStringUtils;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

@Value
@Builder
class MultipartMessage {
    private static final String CRLF = "\r\n";
    private static final Random SECURE_RANDOM = new SecureRandom();
    public static final int BOUNDARY_LENGTH = 20;
    @Singular
    List<Part> parts;
    String boundary;
    String contentType;

    MultipartMessage(List<Part> parts, String boundary, String contentType) {
        if (parts == null || parts.isEmpty()) {
            throw new ConfGeneratorException("At least one part is required");
        }
        this.parts = parts;
        this.boundary = boundary != null ? boundary : secureRandomBoundary();
        this.contentType = contentType != null ? contentType : "multipart/mixed";
    }

    private static String secureRandomBoundary() {
        return RandomStringUtils.random(BOUNDARY_LENGTH, 0, 0, Boolean.TRUE, Boolean.TRUE, null, SECURE_RANDOM);
    }

    public String toString() {
        return toString(false);
    }

    public String bodyToString() {
        return toString(true);
    }

    private String toString(boolean skipHeaders) {
        var sb = new StringBuilder();

        if (!skipHeaders) {
            var header = header("Content-Type",
                    String.format("%s; charset=UTF-8; boundary=%s", contentType, boundary));
            sb.append(header).append(CRLF);
            sb.append(CRLF);
        }

        parts.forEach(p -> {
            sb.append("--").append(boundary).append(CRLF);
            sb.append(p.toString()).append(CRLF);
        });
        sb.append("--").append(boundary).append("--").append(CRLF);
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
            var sb = new StringBuilder();
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
