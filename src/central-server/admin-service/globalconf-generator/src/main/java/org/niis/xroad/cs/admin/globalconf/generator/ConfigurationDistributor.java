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

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

@Slf4j
public class ConfigurationDistributor {
    public static final DateTimeFormatter DIRECTORY_TIMESTAMP_FORMATTER = DateTimeFormatter
            .ofPattern("uuuuMMddHHmmssnnnnnnnnn")
            .withZone(ZoneOffset.UTC);

    private final Path generatedConfDir;
    private final int version;
    private final Instant timestamp;

    private boolean initialized = false;

    ConfigurationDistributor(@NonNull Path generatedConfDir, int version, @NonNull Instant timestamp) {
        this.generatedConfDir = generatedConfDir;
        this.version = version;
        this.timestamp = timestamp;
    }

    public Path initConfLocation() {
        try {
            var configLocation = getConfigLocationPath();
            FileUtils.createDirectories(configLocation);
            initialized = true;
            return configLocation;
        } catch (IOException e) {
            log.error("Failed to create config dir", e);
            throw new ConfGeneratorException(e);
        }
    }

    public void writeConfigurationFiles(Collection<ConfigurationPart> configurationParts) {
        configurationParts.forEach(this::writeConfigurationFile);
    }

    public void writeDirectoryContentFile(String fileName, byte[] data) {
        writeFile(getVersionSubPath().resolve(fileName), data);
    }

    @SneakyThrows
    public void moveDirectoryContentFile(String source, String target) {
        var versionPath = generatedConfDir.resolve(getVersionSubPath());
        var sourcePath = versionPath.resolve(source);
        Files.move(sourcePath, versionPath.resolve(target), StandardCopyOption.ATOMIC_MOVE);
    }

    private void writeConfigurationFile(ConfigurationPart configurationPart) {
        var fileSubPath = getConfigLocationPath().resolve(configurationPart.getFilename());
        var data = configurationPart.getData();

        writeFile(fileSubPath, data);
    }


    private void writeFile(Path fileSubPath, byte @NonNull [] data) {
        checkInitialized();
        var fileFullPath = generatedConfDir.resolve(fileSubPath);
        try {
            FileUtils.write(fileFullPath, data);
        } catch (IOException e) {
            log.error("Failed to write file {}", fileFullPath, e);
            throw new ConfGeneratorException(e);
        }
    }

    public int getVersion() {
        return version;
    }

    public Path getVersionSubPath() {
        return Path.of("V" + version);
    }

    public Path getSubPath() {
        return getVersionSubPath().resolve(DIRECTORY_TIMESTAMP_FORMATTER.format(timestamp));
    }

    private Path getConfigLocationPath() {
        return generatedConfDir.resolve(getSubPath());
    }

    private void checkInitialized() {
        if (!initialized) {
            throw new ConfGeneratorException("Config location not initialized.");
        }
    }

}
