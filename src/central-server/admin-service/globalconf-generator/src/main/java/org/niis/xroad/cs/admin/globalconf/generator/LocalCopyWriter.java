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

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

import static ee.ria.xroad.common.conf.globalconf.ConfigurationUtils.escapeInstanceIdentifier;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

@Slf4j
class LocalCopyWriter {
    private static final DateTimeFormatter EXPIRE_TIME_FORMAT = DateTimeFormatter
            .ofPattern("uuuu-MM-dd'T'HH:mm:ssX")
            .withZone(ZoneOffset.UTC);
    public static final String METADATA_SUFFIX = ".metadata";
    public static final String INSTANCE_IDENTIFIER_FILE = "instance-identifier";
    public static final String FILE_LIST_FILE = "files";

    private final String instanceIdentifier;
    private final Path localConfDirectory; // defaults to /etc/xroad/globalconf
    private final Instant confExpireTime;

    LocalCopyWriter(String instanceIdentifier, Path localConfDirectory, Instant confExpireTime) {
        this.instanceIdentifier = instanceIdentifier;
        this.localConfDirectory = localConfDirectory;
        this.confExpireTime = confExpireTime;
    }

    @SneakyThrows
    public void write(Collection<ConfigurationPart> configurationParts) {
        FileUtils.createDirectories(getTargetDir());

        configurationParts.forEach(this::writePart);

        writeFileList(configurationParts);
        writeInstanceIdentifier();

        deleteStaleConfigFiles(configurationParts);
    }

    @SneakyThrows
    private void writePart(ConfigurationPart configurationPart) {
        var filePath = getFilePath(configurationPart);
        var metadataFilePath = metadataFilePath(filePath);
        FileUtils.write(filePath, configurationPart.getData());
        FileUtils.writeString(metadataFilePath, configurationMetadataJson());
    }

    private Path getFilePath(ConfigurationPart configurationPart) {
        return getTargetDir().resolve(configurationPart.getFilename());
    }

    private String configurationMetadataJson() {
        return String.format("{\"contentIdentifier\":\"DUMMY\","
                + "\"instanceIdentifier\":\"%s\",\"contentFileName\":null,"
                + "\"contentLocation\":\"\""
                + ",\"expirationDate\":\"%s\"}", instanceIdentifier, EXPIRE_TIME_FORMAT.format(confExpireTime));
    }

    @SneakyThrows
    private void writeFileList(Collection<ConfigurationPart> configurationParts) {
        var fileList = configurationParts.stream()
                .map(this::getFilePath)
                .map(Path::toAbsolutePath)
                .map(Path::toString)
                .sorted()
                .collect(joining("\n"));

        FileUtils.writeString(fileListPath(), fileList);
    }

    private void writeInstanceIdentifier() throws IOException {
        FileUtils.writeString(instanceIdentifierPath(), instanceIdentifier);
    }

    private void deleteStaleConfigFiles(Collection<ConfigurationPart> configurationParts) throws IOException {
        var validFileNames = configurationParts.stream()
                .map(ConfigurationPart::getFilename)
                .collect(toSet());

        try (var fileStream = Files.list(getTargetDir())) {
            fileStream
                    .filter(path -> !path.getFileName().toString().endsWith(METADATA_SUFFIX))
                    .filter(path -> !validFileNames.contains(path.getFileName().toString()))
                    .filter(Files::isRegularFile)
                    .forEach(this::deleteStaleConfigFile);
        }
    }

    private Path getTargetDir() {
        return localConfDirectory.resolve(escapeInstanceIdentifier(instanceIdentifier));
    }

    private Path instanceIdentifierPath() {
        return localConfDirectory.resolve(INSTANCE_IDENTIFIER_FILE);
    }

    private Path fileListPath() {
        return localConfDirectory.resolve(FILE_LIST_FILE);
    }

    @SneakyThrows
    private void deleteStaleConfigFile(Path path) {
        log.trace("Deleting stale config file {}", path);
        Files.delete(path);
        Files.deleteIfExists(metadataFilePath(path));
    }

    private static Path metadataFilePath(Path path) {
        return path.getParent().resolve(path.getFileName().toString() + METADATA_SUFFIX);
    }
}
