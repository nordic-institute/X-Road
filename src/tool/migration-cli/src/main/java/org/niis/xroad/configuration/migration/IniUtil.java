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

package org.niis.xroad.configuration.migration;

import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.convert.DisabledListDelimiterHandler;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class IniUtil {

    public LoadedIniFile load(String filePath) {
        try {
            INIConfiguration ini = new INIConfiguration();
            ini.setListDelimiterHandler(DisabledListDelimiterHandler.INSTANCE);
            var configPath = Paths.get(filePath);
            var iniStringLines = Files.readAllLines(configPath);

            try (Reader r = new StringReader(String.join("\n", iniStringLines))) {
                ini.read(r);
                return new LoadedIniFile(configPath, iniStringLines, ini);
            }
        } catch (Exception e) {
            throw new MigrationException("Failed to load INI file %s".formatted(filePath), e);
        }
    }

    public Map<String, Object> loadToNestedMap(String filePath, String rootPrefix) {
        var ini = load(filePath);

        Map<String, Object> properties = new HashMap<>();
        for (String section : ini.parsedContent().getSections()) {
            for (Iterator<String> it = ini.parsedContent().getSection(section).getKeys(); it.hasNext(); ) {
                var sectionKey = it.next();
                var key = section + "." + sectionKey;
                var mappedKey = LegacyConfigPathMapping.map(key);
                var valueStr = ini.parsedContent().getSection(section).getString(sectionKey);

                insertNestedProperty(properties, mappedKey.split("\\."), resolveValue(valueStr));
            }
        }

        Map<String, Object> root = new HashMap<>();
        insertNestedProperty(root, rootPrefix.split("\\."), properties);
        return root;
    }

    public Map<String, String> loadToFlatMap(String filePath, String rootPrefix) {
        var ini = load(filePath);

        Map<String, String> properties = new HashMap<>();
        for (String section : ini.parsedContent().getSections()) {
            for (Iterator<String> it = ini.parsedContent().getSection(section).getKeys(); it.hasNext(); ) {
                var sectionKey = it.next();
                var key = String.join(".", rootPrefix, section, sectionKey);
                var mappedKey = LegacyConfigPathMapping.map(key);
                var valueStr = ini.parsedContent().getSection(section).getString(sectionKey);

                properties.put(mappedKey, valueStr);
            }
        }

        return properties;
    }

    @SuppressWarnings("unchecked")
    private void insertNestedProperty(Map<String, Object> rootMap, String[] keys, Object value) {
        Map<String, Object> current = rootMap;
        for (int i = 0; i < keys.length - 1; i++) {
            current = (Map<String, Object>) current.computeIfAbsent(keys[i], k -> new HashMap<>());
        }
        current.put(keys[keys.length - 1], value);
    }

    private Object resolveValue(String value) {
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        } else if (NumberUtils.isParsable(value)) {
            return NumberUtils.createNumber(value);
        }
        return value;
    }

    public record LoadedIniFile(Path path,
                                List<String> rawContentLines,
                                INIConfiguration parsedContent) {
    }

}
