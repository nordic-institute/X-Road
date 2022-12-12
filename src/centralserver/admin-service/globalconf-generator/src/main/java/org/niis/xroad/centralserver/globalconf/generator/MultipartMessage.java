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
    @Singular
    List<Part> parts;
    String boundary;
    String contentType;

    MultipartMessage(List<Part> parts, String boundary, String contentType) {
        if (parts == null || parts.isEmpty()) {
            throw new RuntimeException("At least one part is required");
        }
        this.parts = parts;
        this.boundary = boundary != null ? boundary : RandomStringUtils.randomAlphanumeric(20);
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

    static Part.PartBuilder partBuilder() {
        return Part.builder();
    }


    @Value
    static class Header {
        String name;
        String value;

        public String toString() {
            return name + ": " + value;
        }
    }

    @Value
    @Builder
    static class Part {
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

}
