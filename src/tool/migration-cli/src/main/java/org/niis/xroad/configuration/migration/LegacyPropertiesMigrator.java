/*
 * The MIT License
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
package org.niis.xroad.configuration.migration;

import lombok.extern.slf4j.Slf4j;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.niis.xroad.configuration.migration.ConfigurationYamlMigrator.PREFIX;

@Slf4j
public class LegacyPropertiesMigrator {


    boolean migrateProperties(String inputFilePath) throws IOException {
        var props = loadProperties(inputFilePath);

        var outputFilePath = Paths.get(inputFilePath + ".original");
        if (Files.exists(outputFilePath)) {
            log.warn("Backup file [{}] already exists, creating new backup with timestamp", outputFilePath);
            outputFilePath = Paths.get(inputFilePath + ".original" + "." + System.currentTimeMillis());
        }
        // Create backup
        Files.move(props.path(), outputFilePath);

        Map<String, Object> properties = new HashMap<>();
        props.parsedContent().forEach((key, value) -> {
            if (LegacyConfigPathMapping.shouldKeep(key.toString())) {
                var mappedKey = PREFIX + "." + LegacyConfigPathMapping.map(key.toString());
                properties.put(mappedKey, value);
            }
        });

        savePropertiesToFile(props, properties, inputFilePath);

        return true;
    }

    private LoadedPropertiesFile loadProperties(String filePath) throws IOException {
        var configPath = Paths.get(filePath);
        var lines = Files.readAllLines(configPath);
        var properties = new Properties();

        try (Reader reader = new StringReader(String.join("\n", lines))) {
            properties.load(reader);
        }

        return new LoadedPropertiesFile(configPath, lines, properties);
    }

    private void savePropertiesToFile(LoadedPropertiesFile loadedProps,
                                      Map<String, Object> properties,
                                      String outputPath) throws IOException {
        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write("# Generated at: " + ZonedDateTime.now() + "\n");
            writer.write("# Input source: " + loadedProps.path().toAbsolutePath() + "\n");
            writer.write("# Original configuration:\n");
            writer.write("###########################\n");
            for (String line : loadedProps.rawContentLines()) {
                writer.write("# " + line + "\n");
            }
            writer.write("###########################\n\n");

            // Write new properties in sorted order
            properties.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        try {
                            writer.write(entry.getKey() + "=" + entry.getValue() + "\n");
                        } catch (IOException e) {
                            throw new MigrationException("Failed to write props file", e);
                        }
                    });
        }
    }

    record LoadedPropertiesFile(Path path,
                                List<String> rawContentLines,
                                Properties parsedContent) {
    }

}
