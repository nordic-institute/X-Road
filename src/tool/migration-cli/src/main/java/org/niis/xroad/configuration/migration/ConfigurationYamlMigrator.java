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
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.convert.DisabledListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.math.NumberUtils;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Slf4j
class ConfigurationYamlMigrator {
    static final String PREFIX = "xroad";

    boolean migrate(String inputFilePath, String outputFilePath) throws IOException, ConfigurationException {
        var ini = load(inputFilePath);

        Map<String, Object> properties = new HashMap<>();
        for (String section : ini.parsedContent().getSections()) {
            for (Iterator<String> it = ini.parsedContent().getSection(section).getKeys(); it.hasNext(); ) {
                var sectionKey = it.next();
                var key = section + "." + sectionKey;
                var mappedKey = LegacyConfigPathMapping.map(key);
                var valueStr = ini.parsedContent().getSection(section).getString(sectionKey);

                insertNestedProperty(properties, mappedKey.split("\\."), valueStr);
            }
        }

        Map<String, Object> root = new HashMap<>();
        root.put(PREFIX, properties);

        saveYamlToFile(ini, root, outputFilePath);
        return true;
    }

    @SuppressWarnings("unchecked")
    private void insertNestedProperty(Map<String, Object> rootMap, String[] keys, String value) {
        Map<String, Object> current = rootMap;
        for (int i = 0; i < keys.length - 1; i++) {
            current = (Map<String, Object>) current.computeIfAbsent(keys[i], k -> new HashMap<>());
        }
        current.put(keys[keys.length - 1], resolveValue(value));
    }

    private Object resolveValue(String value) {
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        } else if (NumberUtils.isParsable(value)) {
            return NumberUtils.createNumber(value);
        } else {
            return value;
        }
    }

    private LoadedIniFile load(String filePath) throws IOException, ConfigurationException {
        INIConfiguration ini = new INIConfiguration();
        ini.setListDelimiterHandler(DisabledListDelimiterHandler.INSTANCE);
        var configPath = Paths.get(filePath);
        var iniStringLines = Files.readAllLines(configPath);

        try (Reader r = new StringReader(String.join("\n", iniStringLines))) {
            ini.read(r);

            return new LoadedIniFile(configPath, iniStringLines, ini);
        }
    }

    public static void saveYamlToFile(LoadedIniFile loadedIniFile,
                                      final Map<String, Object> properties,
                                      String outputPath) throws IOException {
        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setProcessComments(true);
        final Yaml yaml = new Yaml(options);

        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write("# Generated at: " + ZonedDateTime.now() + "\n");
            writer.write("# Input source: " + loadedIniFile.path.toAbsolutePath() + "\n");
            writer.write("# Original configuration:\n");
            writer.write("###########################\n");
            for (String line : loadedIniFile.rawContentLines()) {
                writer.write("# " + line + "\n");
            }

            writer.write("###########################\n\n");
            yaml.dump(properties, writer);
        }
    }


    record LoadedIniFile(Path path,
                         List<String> rawContentLines,
                         INIConfiguration parsedContent) {
    }
}
