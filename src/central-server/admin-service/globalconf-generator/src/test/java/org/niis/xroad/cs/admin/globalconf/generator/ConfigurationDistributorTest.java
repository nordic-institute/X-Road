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

import ee.ria.xroad.common.util.TimeUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurationDistributorTest {

    public static final int VERSION = 2;
    public static final String CONF_DIRECTORY = "internalconf";

    public static final ConfigurationPart CONFIGURATION_PART1 = ConfigurationPart.builder()
            .contentIdentifier("CONTENT-ID1")
            .filename("config-file1.txt")
            .data("test data".getBytes(UTF_8))
            .build();

    public static final ConfigurationPart CONFIGURATION_PART2 = ConfigurationPart.builder()
            .contentIdentifier("CONTENT-ID2")
            .filename("config-file2.txt")
            .data("test data2".getBytes(UTF_8))
            .build();

    @TempDir
    Path generatedConfDir;

    @Test
    void testInitConfLocation() {
        var timestamp = Instant.parse("2022-12-08T07:55:01.411146000Z");
        ConfigurationDistributor configurationDistributor = new ConfigurationDistributor(generatedConfDir, VERSION, timestamp);

        var path = configurationDistributor.initConfLocation();

        assertThat(path)
                .as("configuration folder")
                .isNotNull()
                .endsWith(Path.of("V2", "20221208075501411146000"))
                .exists()
                .isDirectory();
    }

    @Test
    void writeFiles() {
        ConfigurationDistributor configurationDistributor = new ConfigurationDistributor(generatedConfDir, VERSION, TimeUtils.now());
        var confDir = configurationDistributor.initConfLocation();

        configurationDistributor.writeConfigurationFiles(List.of(CONFIGURATION_PART1, CONFIGURATION_PART2));

        assertThat(confDir.resolve("config-file1.txt"))
                .exists()
                .content().isEqualTo("test data");
        assertThat(confDir.resolve("config-file2.txt"))
                .exists()
                .content().isEqualTo("test data2");
    }

    @Test
    void writeFilesBeforeInitShouldThrow() {
        ConfigurationDistributor configurationDistributor = new ConfigurationDistributor(generatedConfDir, VERSION, TimeUtils.now());
        // initConfLocation omitted

        var configurationParts = List.of(CONFIGURATION_PART1);
        assertThatThrownBy(() -> configurationDistributor.writeConfigurationFiles(configurationParts))
                .isExactlyInstanceOf(ConfGeneratorException.class)
                .hasMessage("Config location not initialized.");
    }

    @Test
    void writeDirectoryContent() {
        ConfigurationDistributor configurationDistributor = new ConfigurationDistributor(generatedConfDir, VERSION, TimeUtils.now());
        configurationDistributor.initConfLocation();

        configurationDistributor.writeDirectoryContentFile(CONF_DIRECTORY, "data".getBytes(UTF_8));

        assertThat(generatedConfDir.resolve(Path.of("V" + VERSION, CONF_DIRECTORY)))
                .exists()
                .content().isEqualTo("data");
    }

    @Test
    void moveDirectoryContentFile() {
        ConfigurationDistributor configurationDistributor = new ConfigurationDistributor(generatedConfDir, VERSION, TimeUtils.now());
        configurationDistributor.initConfLocation();
        configurationDistributor.writeDirectoryContentFile(CONF_DIRECTORY + ".tmp", "data".getBytes(UTF_8));

        configurationDistributor.moveDirectoryContentFile(CONF_DIRECTORY + ".tmp", CONF_DIRECTORY);

        assertThat(generatedConfDir.resolve(Path.of("V" + VERSION, CONF_DIRECTORY)))
                .exists()
                .content().isEqualTo("data");
    }
}
