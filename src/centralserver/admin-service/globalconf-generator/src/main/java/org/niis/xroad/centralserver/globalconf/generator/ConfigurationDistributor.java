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
