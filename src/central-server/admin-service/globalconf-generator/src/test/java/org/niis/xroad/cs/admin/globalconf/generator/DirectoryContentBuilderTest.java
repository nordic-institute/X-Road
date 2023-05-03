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

import ee.ria.xroad.common.util.CryptoUtils;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class DirectoryContentBuilderTest {
    public static final Pattern HEADER_PATTERN = Pattern.compile(
            "^Content-Type: multipart/mixed; charset=UTF-8; boundary=(\\w+)\r\n.*", Pattern.DOTALL);

    @Test
    void buildDirectoryContent() {
        var directoryContentBuilder = new DirectoryContentBuilder(
                CryptoUtils.SHA1_ID,
                Instant.parse("2022-12-08T08:05:01.123Z"),
                "/V2/some/path",
                "CS-INSTANCE");
        var dirContent = directoryContentBuilder
                .contentPart(ConfigurationPart.builder()
                        .filename("config-file.txt")
                        .contentIdentifier("CONTENT-ID")
                        .data("config-data".getBytes(StandardCharsets.UTF_8))
                        .build())
                .build();

        var headerMatcher = HEADER_PATTERN.matcher(dirContent.getContent());
        assertThat(headerMatcher).as("Expecting header to match pattern").matches(Matcher::matches);
        var boundary = headerMatcher.group(1);
        assertThat(dirContent.getContent()).isNotEmpty()
                .isEqualTo("Content-Type: multipart/mixed; charset=UTF-8; boundary=%s\r\n"
                        + "\r\n"
                        + "--%s\r\n"
                        + "Expire-date: 2022-12-08T08:05:01Z\r\n"
                        + "Version: 2\r\n"
                        + "\r\n"
                        + "--%s\r\n"
                        + "Content-type: application/octet-stream\r\n"
                        + "Content-transfer-encoding: base64\r\n"
                        + "Content-identifier: CONTENT-ID; instance='CS-INSTANCE'\r\n"
                        + "Content-location: /V2/some/path/config-file.txt\r\n"
                        + "Hash-algorithm-id: http://www.w3.org/2000/09/xmldsig#sha1\r\n"
                        + "\r\n"
                        + "e4VJC7nd16bg9QniBVyvjcaeTwE=\r\n"
                        + "--%s--\r\n", boundary, boundary, boundary, boundary);

        assertThat(dirContent.getSignableContent()).isNotEmpty()
                .isEqualTo("--%s\r\n"
                        + "Expire-date: 2022-12-08T08:05:01Z\r\n"
                        + "Version: 2\r\n"
                        + "\r\n"
                        + "--%s\r\n"
                        + "Content-type: application/octet-stream\r\n"
                        + "Content-transfer-encoding: base64\r\n"
                        + "Content-identifier: CONTENT-ID; instance='CS-INSTANCE'\r\n"
                        + "Content-location: /V2/some/path/config-file.txt\r\n"
                        + "Hash-algorithm-id: http://www.w3.org/2000/09/xmldsig#sha1\r\n"
                        + "\r\n"
                        + "e4VJC7nd16bg9QniBVyvjcaeTwE=\r\n"
                        + "--%s--\r\n", boundary, boundary, boundary, boundary);
    }


}
