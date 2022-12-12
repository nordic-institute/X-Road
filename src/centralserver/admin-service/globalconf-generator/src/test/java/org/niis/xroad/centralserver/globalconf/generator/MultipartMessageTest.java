package org.niis.xroad.centralserver.globalconf.generator;


import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.niis.xroad.centralserver.globalconf.generator.MultipartMessage.header;
import static org.niis.xroad.centralserver.globalconf.generator.MultipartMessage.partBuilder;

class MultipartMessageTest {

    @Test
    void partToString_noContent() {
        var part = partBuilder()
                .header(header("Header", "value"))
                .build();
        var partAsString = part.toString();

        assertThat(partAsString)
                .isEqualTo("Header: value\r\n");
    }

    @Test
    void partToString() {
        var part = partBuilder()
                .header(header("Header", "value"))
                .content("content")
                .build();
        var partAsString = part.toString();

        assertThat(partAsString)
                .isEqualTo("Header: value\r\n" +
                        "\r\n" +
                        "content");
    }

    @Test
    void write_toString() {
        var multipartMessage = MultipartMessage.builder()
                .boundary("message_part_boundary")
                .part(partBuilder()
                        .header(header("Version", "2"))
                        .header(header("Expire-date", "2022-12-08T08:05:01Z"))
                        .build())
                .part(partBuilder()
                        .header(header("Content-id", "some-id"))
                        .content("content body")
                        .build())
                .build();

        var messageAsString = multipartMessage.toString();

        assertThat(messageAsString)
                .isEqualTo("Content-Type: multipart/mixed; charset=UTF-8; boundary=message_part_boundary\r\n" +
                        "\r\n" +
                        "--message_part_boundary\r\n" +
                        "Version: 2\r\n" +
                        "Expire-date: 2022-12-08T08:05:01Z\r\n" +
                        "\r\n" +
                        "--message_part_boundary\r\n" +
                        "Content-id: some-id\r\n" +
                        "\r\n" +
                        "content body\r\n" +
                        "--message_part_boundary--\r\n");
    }

    @Test
    void empty_cannot_build() {
        assertThatThrownBy(() -> MultipartMessage.builder().build())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("At least one part is required");
    }
}
