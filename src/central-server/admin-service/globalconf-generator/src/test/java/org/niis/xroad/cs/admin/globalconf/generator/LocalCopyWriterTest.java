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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class LocalCopyWriterTest {

    static final ConfigurationPart CONFIGURATION_PART1 = ConfigurationPart.builder()
            .contentIdentifier("CONTENT-ID1")
            .filename("config-file1.txt")
            .data("test data".getBytes(UTF_8))
            .build();

    static final ConfigurationPart CONFIGURATION_PART2 = ConfigurationPart.builder()
            .contentIdentifier("CONTENT-ID2")
            .filename("config-file2.txt")
            .data("test data2".getBytes(UTF_8))
            .build();
    static final Set<ConfigurationPart> CONFIGURATION_PARTS = Set.of(CONFIGURATION_PART1, CONFIGURATION_PART2);
    static final String INSTANCE = "XROAD-INSTANCE";
    public static final String CONF_EXPIRE_TIME = "2023-01-03T15:52:33Z";
    static final Instant CONF_EXPIRE_INSTANT = Instant.parse(CONF_EXPIRE_TIME);


    @TempDir
    Path localConfDir;

    @Test
    void shouldCreateDir() {
        var localCopyWriter = new LocalCopyWriter(INSTANCE, localConfDir, Instant.now());
        localCopyWriter.write(CONFIGURATION_PARTS);

        assertThat(localConfDir.resolve(INSTANCE))
                .exists()
                .isDirectory();
    }

    @Test
    void shouldWriteFiles() {
        var localCopyWriter = new LocalCopyWriter(INSTANCE, localConfDir, Instant.now());
        localCopyWriter.write(CONFIGURATION_PARTS);

        CONFIGURATION_PARTS.forEach(part ->
                assertThat(localConfDir.resolve(INSTANCE).resolve(part.getFilename()))
                        .exists()
                        .content().isEqualTo(new String(part.getData()))
        );
    }

    @Test
    void shouldWriteMetadata() {
        var localCopyWriter = new LocalCopyWriter(INSTANCE, localConfDir, CONF_EXPIRE_INSTANT);
        localCopyWriter.write(CONFIGURATION_PARTS);

        CONFIGURATION_PARTS.forEach(part ->
                assertThat(localConfDir.resolve(INSTANCE).resolve(part.getFilename() + ".metadata"))
                        .exists()
                        .content().isEqualTo("{\"contentIdentifier\":\"DUMMY\","
                                + "\"instanceIdentifier\":\"%s\",\"contentFileName\":null,"
                                + "\"contentLocation\":\"\""
                                + ",\"expirationDate\":\"%s\"}", INSTANCE, CONF_EXPIRE_TIME)
        );
    }

    @Test
    void shouldWriteFileList() {
        String expectedFileList = String.format("%s\n%s",
                localConfDir.resolve(INSTANCE).resolve(CONFIGURATION_PART1.getFilename()).toAbsolutePath(),
                localConfDir.resolve(INSTANCE).resolve(CONFIGURATION_PART2.getFilename()).toAbsolutePath());

        var localCopyWriter = new LocalCopyWriter(INSTANCE, localConfDir, CONF_EXPIRE_INSTANT);
        localCopyWriter.write(CONFIGURATION_PARTS);

        assertThat(localConfDir.resolve("files"))
                .exists()
                .content().isEqualTo(expectedFileList);
    }

    @Test
    void shouldWriteInstanceIdentifier() {
        var localCopyWriter = new LocalCopyWriter(INSTANCE, localConfDir, CONF_EXPIRE_INSTANT);
        localCopyWriter.write(CONFIGURATION_PARTS);

        assertThat(localConfDir.resolve("instance-identifier"))
                .exists()
                .content().isEqualTo(INSTANCE);
    }

    @Test
    void shouldDeleteStaleFiles() throws IOException {
        var targetDir = localConfDir.resolve(INSTANCE);
        Files.createDirectories(targetDir);
        Files.writeString(targetDir.resolve("some-config.xml"), "data");
        Files.writeString(targetDir.resolve("some-config.xml.metadata"), "metadata");
        Files.writeString(targetDir.resolve("some-config-without-metadata.xml"), "data");

        var localCopyWriter = new LocalCopyWriter(INSTANCE, localConfDir, CONF_EXPIRE_INSTANT);
        localCopyWriter.write(CONFIGURATION_PARTS);

        assertAll(
                () -> assertThat(targetDir.resolve("some-config.xml")).doesNotExist(),
                () -> assertThat(targetDir.resolve("some-config.xml.metadata")).doesNotExist(),
                () -> assertThat(targetDir.resolve("some-config-without-metadata.xml")).doesNotExist());
    }
}
