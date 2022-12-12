package org.niis.xroad.centralserver.globalconf.generator;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


public class ConfigurationDistributorTest {

    public static final int VERSION = 2;
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
        ConfigurationDistributor configurationDistributor = new ConfigurationDistributor(generatedConfDir);

        var timestamp = Instant.parse("2022-12-08T07:55:01.411146000Z");
        var path = configurationDistributor.initConfLocation(VERSION, timestamp);

        assertThat(path)
                .as("configuration folder")
                .isNotNull()
                .endsWith(Path.of("V2", "20221208075501411146000"))
                .exists()
                .isDirectory();
    }

    @Test
    void writeFiles() {
        ConfigurationDistributor configurationDistributor = new ConfigurationDistributor(generatedConfDir);
        var confDir = configurationDistributor.initConfLocation(VERSION, Instant.now());

        configurationDistributor.writeConfigurationFiles(List.of(CONFIGURATION_PART1, CONFIGURATION_PART2));

        assertThat(confDir.resolve("config-file1.txt"))
                .exists()
                .content().isEqualTo("test data");
        assertThat(confDir.resolve("config-file2.txt"))
                .exists()
                .content().isEqualTo("test data2");
    }

    @Test
    void writeFilesBeforeInit_shouldThrow() {
        ConfigurationDistributor configurationDistributor = new ConfigurationDistributor(generatedConfDir);
        // initConfLocation omitted

        assertThatThrownBy(() -> configurationDistributor.writeConfigurationFiles(List.of(CONFIGURATION_PART1)))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessage("Config location not initialized.");
    }
}
