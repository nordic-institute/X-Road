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


import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.niis.xroad.cs.admin.globalconf.generator.MultipartMessage.header;
import static org.niis.xroad.cs.admin.globalconf.generator.MultipartMessage.partBuilder;
import static org.niis.xroad.cs.admin.globalconf.generator.MultipartMessage.rawPart;

class MultipartMessageTest {

    @Test
    void partToStringNoContent() {
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
                .isEqualTo("Header: value\r\n"
                        + "\r\n"
                        + "content");
    }

    @Test
    void partToStringNoHeader() {
        var part = partBuilder()
                .content("content")
                .build();
        var partAsString = part.toString();

        assertThat(partAsString)
                .isEqualTo("\r\n"
                        + "content");
    }

    @Test
    void rawPartToString() {
        var part = rawPart("content");
        var partAsString = part.toString();

        assertThat(partAsString)
                .isEqualTo("content");
    }

    @Test
    void writeToString() {
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
                .isEqualTo("Content-Type: multipart/mixed; charset=UTF-8; boundary=message_part_boundary\r\n"
                        + "\r\n"
                        + "--message_part_boundary\r\n"
                        + "Version: 2\r\n"
                        + "Expire-date: 2022-12-08T08:05:01Z\r\n"
                        + "\r\n"
                        + "--message_part_boundary\r\n"
                        + "Content-id: some-id\r\n"
                        + "\r\n"
                        + "content body\r\n"
                        + "--message_part_boundary--\r\n");
    }

    @Test
    void writeToStringSkipContentType() {
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

        var messageAsString = multipartMessage.bodyToString();

        assertThat(messageAsString)
                .isEqualTo("--message_part_boundary\r\n"
                        + "Version: 2\r\n"
                        + "Expire-date: 2022-12-08T08:05:01Z\r\n"
                        + "\r\n"
                        + "--message_part_boundary\r\n"
                        + "Content-id: some-id\r\n"
                        + "\r\n"
                        + "content body\r\n"
                        + "--message_part_boundary--\r\n");
    }

    @Test
    void emptyCannotBuild() {
        var builder = MultipartMessage.builder();
        assertThatThrownBy(() -> builder.build())
                .isInstanceOf(ConfGeneratorException.class)
                .hasMessage("At least one part is required");
    }
}
