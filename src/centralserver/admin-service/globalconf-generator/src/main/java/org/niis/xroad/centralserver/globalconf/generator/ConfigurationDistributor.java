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

import ee.ria.xroad.common.SystemProperties;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
public class ConfigurationDistributor {

    public static final DateTimeFormatter DIRECTORY_TIMESTAMP_FORMATTER = DateTimeFormatter
            .ofPattern("uuuuMMddHHmmssnnnnnnnnn")
            .withZone(ZoneOffset.UTC);

    final private Path generatedConfDir;
    private Path configLocation;

    public ConfigurationDistributor() {
        this(Path.of(SystemProperties.getCenterGeneratedConfDir()));
    }

    ConfigurationDistributor(Path generatedConfDir) {
        this.generatedConfDir = generatedConfDir;
    }


    public Path initConfLocation(int version, Instant timestamp) {
        try {
            configLocation = generatedConfDir.resolve(
                    Path.of("V" + version, DIRECTORY_TIMESTAMP_FORMATTER.format(timestamp)));
            Files.createDirectories(configLocation);
            return configLocation;
        } catch (IOException e) {
            log.error("Failed to create config dir", e);
            throw new RuntimeException(e);
        }
    }

    public void writeConfigurationFiles(List<ConfigurationPart> configurationParts) {
        configurationParts.forEach(this::writeConfigurationFile);
    }

    private void writeConfigurationFile(ConfigurationPart configurationPart) {
        try {
            Files.write(getConfigLocation().resolve(configurationPart.getFilename()), configurationPart.getData());
        } catch (IOException e) {
            log.error("Failed to write configuration part {}", configurationPart.getFilename(), e);
            throw new RuntimeException(e);
        }
    }

    private Path getConfigLocation() {
        if (configLocation == null) {
            throw new RuntimeException("Config location not initialized.");
        }
        return configLocation;
    }


}
