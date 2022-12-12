package org.niis.xroad.centralserver.globalconf.generator;

import ee.ria.xroad.common.util.HashCalculator;

import org.junit.jupiter.api.Test;

import javax.xml.crypto.dsig.DigestMethod;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

public class DirectoryContentBuilderTest {
    public static final Pattern HEADER_PATTERN = Pattern.compile("^Content-Type: multipart/mixed; charset=UTF-8; boundary=(\\w+)\r\n.*", Pattern.DOTALL);

    @Test
    void empty() {
        var directoryContentBuilder = new DirectoryContentBuilder(
                new HashCalculator(DigestMethod.SHA1),
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



        var headerMatcher = HEADER_PATTERN.matcher(dirContent);
        assertThat(headerMatcher).as("Expecting header to match pattern").matches();
        var boundary = headerMatcher.group(1);
        assertThat(dirContent).isNotEmpty()
                .isEqualTo("Content-Type: multipart/mixed; charset=UTF-8; boundary=%s\r\n" +
                        "\r\n" +
                        "--%s\r\n" +
                        "Expire-date: 2022-12-08T08:05:01Z\r\n" +
                        "Version: 2\r\n" +
                        "\r\n" +
                        "--%s\r\n" +
                        "Content-type: application/octet-stream\r\n" +
                        "Content-transfer-encoding: base64\r\n" +
                        "Content-identifier: CONTENT-ID; instance='CS-INSTANCE'\r\n" +
                        "Content-location: /V2/some/path/config-file.txt\r\n" +
                        "Hash-algorithm-id: http://www.w3.org/2000/09/xmldsig#sha1\r\n" +
                        "\r\n" +
                        "e4VJC7nd16bg9QniBVyvjcaeTwE=\r\n" +
                        "--%s--\r\n", boundary, boundary, boundary, boundary);
    }



}
