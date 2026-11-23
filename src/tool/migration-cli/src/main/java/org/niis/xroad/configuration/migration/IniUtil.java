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

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.convert.DisabledListDelimiterHandler;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
public class IniUtil {

    private static final String OP_MONITOR_SECTION = "op-monitor";
    private static final String HOST_KEY = "host";
    private static final String LOCAL_INI = "local.ini";
    static final String OVERRIDE_INI_PREFIX = "override-";
    private static final String INI_SUFFIX = ".ini";

    private final OpMonitorHostRule opMonitorHostRule;

    public IniUtil() {
        this(OpMonitorHostRule.LocalAddressResolver.system());
    }

    IniUtil(OpMonitorHostRule.LocalAddressResolver localAddressResolver) {
        this.opMonitorHostRule = new OpMonitorHostRule(localAddressResolver);
    }

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
        var keysInChain = collectOperatorChainKeys(filePath);

        Map<String, Object> properties = new HashMap<>();
        for (String section : ini.parsedContent().getSections()) {
            for (Iterator<String> it = ini.parsedContent().getSection(section).getKeys(); it.hasNext(); ) {
                var sectionKey = it.next();
                var key = section + "." + sectionKey;
                if (LegacyConfigPathMapping.shouldKeep(key) && !LegacyConfigPathMapping.isSuperseded(key, keysInChain)) {
                    var mappedKeys = LegacyConfigPathMapping.map(key);
                    var valueStr = ini.parsedContent().getSection(section).getString(sectionKey);
                    mappedKeys.forEach(mappedKey ->
                            insertNestedProperty(properties, mappedKey.split("\\."), resolveValue(valueStr))
                    );
                    LegacyConfigValueRules.derive(key, valueStr).forEach((derivedKey, derivedValue) ->
                            insertNestedProperty(properties, derivedKey.split("\\."), derivedValue));
                }
            }
        }
        deriveOpMonitorHostRows(filePath).rows().forEach((key, value) ->
                insertNestedProperty(properties, key.split("\\."), value));

        Map<String, Object> root = new HashMap<>();
        insertNestedProperty(root, rootPrefix.split("\\."), properties);
        return root;
    }

    public Map<String, String> loadAndMigrateSignerDevices(String filePath, String rootPrefix) {
        var ini = load(filePath);

        Map<String, String> properties = new HashMap<>();
        for (String section : ini.parsedContent().getSections()) {
            for (Iterator<String> it = ini.parsedContent().getSection(section).getKeys(); it.hasNext(); ) {
                var sectionKey = it.next();
                var key = String.join(".", section, sectionKey.replace("_", "-"));
                var valueStr = ini.parsedContent().getSection(section).getString(sectionKey);
                properties.put(String.join(".", rootPrefix, key), valueStr);
            }
        }
        return properties;
    }

    public Map<String, String> loadToFlatMap(String filePath, String rootPrefix) {
        var ini = load(filePath);
        var keysInChain = collectOperatorChainKeys(filePath);

        Map<String, String> properties = new HashMap<>();
        for (String section : ini.parsedContent().getSections()) {
            for (Iterator<String> it = ini.parsedContent().getSection(section).getKeys(); it.hasNext(); ) {
                var sectionKey = it.next();
                var key = String.join(".", section, sectionKey);
                if (LegacyConfigPathMapping.shouldKeep(key) && !LegacyConfigPathMapping.isSuperseded(key, keysInChain)) {
                    var mappedKeys = LegacyConfigPathMapping.map(key);
                    var valueStr = ini.parsedContent().getSection(section).getString(sectionKey);
                    mappedKeys.forEach(mappedKey ->
                            properties.put(String.join(".", rootPrefix, mappedKey), valueStr)
                    );
                    LegacyConfigValueRules.derive(key, valueStr).forEach((derivedKey, derivedValue) ->
                            properties.put(String.join(".", rootPrefix, derivedKey), String.valueOf(derivedValue)));
                }
            }
        }
        var opMonitorHost = deriveOpMonitorHostRows(filePath);
        opMonitorHost.rows().forEach((key, value) ->
                properties.put(String.join(".", rootPrefix, key), value));
        if (opMonitorHost.remote()) {
            skipOpMonitorDaemonProperties(properties, rootPrefix);
        }

        return properties;
    }

    private Set<String> collectOperatorChainKeys(String filePath) {
        Set<String> keys = new HashSet<>();
        for (Path file : operatorIniChain(Paths.get(filePath))) {
            keys.addAll(collectKeys(load(file.toString()).parsedContent()));
        }
        return keys;
    }

    private static Set<String> collectKeys(INIConfiguration ini) {
        Set<String> keys = new HashSet<>();
        for (String section : ini.getSections()) {
            for (Iterator<String> it = ini.getSection(section).getKeys(); it.hasNext(); ) {
                keys.add(section + "." + it.next());
            }
        }
        return keys;
    }

    private static void skipOpMonitorDaemonProperties(Map<String, String> properties, String rootPrefix) {
        var daemonPrefix = String.join(".", rootPrefix, OP_MONITOR_SECTION) + ".";
        var skipped = properties.keySet().stream()
                .filter(key -> key.startsWith(daemonPrefix))
                .sorted()
                .toList();
        if (!skipped.isEmpty()) {
            properties.keySet().removeAll(skipped);
            log.info("The op-monitor host is a remote host. Not migrated, set on the external host: {}",
                    String.join(", ", skipped));
        }
    }

    /**
     * The host value used is the one that wins when the operator files are applied in order:
     * {@code override-*.ini} alphabetically, then {@code local.ini}. Whether the host is remote is decided for
     * every file of the chain. The rows and the warning are produced only for a file that sets
     * {@code [op-monitor] host}.
     */
    private OpMonitorHostRule.Result deriveOpMonitorHostRows(String filePath) {
        String effectiveHost = null;
        for (Path file : operatorIniChain(Paths.get(filePath))) {
            var host = readOpMonitorHost(load(file.toString()));
            if (host != null) {
                effectiveHost = host;
            }
        }
        if (effectiveHost == null) {
            return new OpMonitorHostRule.Result(Map.of(), null, false);
        }
        var result = opMonitorHostRule.derive(effectiveHost);
        if (readOpMonitorHost(load(filePath)) == null) {
            return new OpMonitorHostRule.Result(Map.of(), null, result.remote());
        }
        if (result.warning() != null) {
            log.warn(result.warning());
        }
        return result;
    }

    private static String readOpMonitorHost(LoadedIniFile ini) {
        if (!ini.parsedContent().getSections().contains(OP_MONITOR_SECTION)) {
            return null;
        }
        return ini.parsedContent().getSection(OP_MONITOR_SECTION).getString(HOST_KEY);
    }

    static boolean isOverrideIniName(String fileName) {
        return fileName.startsWith(OVERRIDE_INI_PREFIX) && fileName.endsWith(INI_SUFFIX);
    }

    private static List<Path> operatorIniChain(Path file) {
        var name = file.getFileName().toString();
        if (!isOverrideIniName(name) && !LOCAL_INI.equals(name)) {
            return List.of(file);
        }
        var directory = file.toAbsolutePath().getParent();
        try (Stream<Path> siblings = Files.list(directory)) {
            var chain = new ArrayList<>(siblings
                    .filter(p -> isOverrideIniName(p.getFileName().toString()))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList());
            chain.add(directory.resolve(LOCAL_INI));
            return chain.stream().filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            throw new MigrationException("Failed to list INI files in %s".formatted(directory), e);
        }
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
