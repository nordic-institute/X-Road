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
package org.niis.xroad.migration.pgp;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.SubnodeConfiguration;
import org.apache.commons.configuration2.convert.DisabledListDelimiterHandler;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Reads message log configuration from X-Road INI configuration files.
 */
@Slf4j
public class MessageLogConfigReader {

    private static final String SECTION_MESSAGE_LOG = "message-log";
    private static final String KEY_ARCHIVE_ENCRYPTION_ENABLED = "archive-encryption-enabled";
    private static final String KEY_ARCHIVE_GROUPING = "archive-grouping";
    private static final String KEY_ARCHIVE_GPG_HOME_DIRECTORY = "archive-gpg-home-directory";
    private static final String KEY_ARCHIVE_DEFAULT_ENCRYPTION_KEY = "archive-default-encryption-key";
    private static final String KEY_ARCHIVE_ENCRYPTION_KEYS_CONFIG = "archive-encryption-keys-config";

    /**
     * Reads message log configuration from an INI file.
     *
     * @param configPath Path to INI configuration file (typically local.ini)
     * @return Parsed MessageLogConfig
     * @throws IOException If file cannot be read or parsed
     */
    public MessageLogConfig readConfig(Path configPath) throws IOException {
        log.info("Reading message log configuration from: {}", configPath);

        if (!Files.exists(configPath)) {
            throw new IOException("Configuration file not found: " + configPath);
        }

        try {
            INIConfiguration ini = loadIniFile(configPath);
            SubnodeConfiguration messageLogSection = ini.getSection(SECTION_MESSAGE_LOG);

            if (messageLogSection.isEmpty()) {
                log.warn("No [{}] section found in configuration file", SECTION_MESSAGE_LOG);
                return createDisabledConfig();
            }

            return parseMessageLogSection(messageLogSection);

        } catch (Exception e) {
            throw new IOException("Failed to parse configuration file: " + configPath, e);
        }
    }

    private INIConfiguration loadIniFile(Path path) throws IOException {
        INIConfiguration ini = new INIConfiguration();
        ini.setListDelimiterHandler(DisabledListDelimiterHandler.INSTANCE);

        String content = Files.readString(path);
        try (Reader reader = new StringReader(content)) {
            ini.read(reader);
        } catch (org.apache.commons.configuration2.ex.ConfigurationException e) {
            throw new IOException("Failed to read INI file: " + path, e);
        }

        return ini;
    }

    private MessageLogConfig parseMessageLogSection(SubnodeConfiguration section) throws IOException {
        boolean encryptionEnabled = section.getBoolean(KEY_ARCHIVE_ENCRYPTION_ENABLED, false);
        String grouping = section.getString(KEY_ARCHIVE_GROUPING, "none");
        String gpgHomeStr = section.getString(KEY_ARCHIVE_GPG_HOME_DIRECTORY, "/etc/xroad/gpghome");
        String defaultKey = section.getString(KEY_ARCHIVE_DEFAULT_ENCRYPTION_KEY);
        String keysConfigStr = section.getString(KEY_ARCHIVE_ENCRYPTION_KEYS_CONFIG);

        Path gpgHome = Paths.get(gpgHomeStr);
        Path keysConfigPath = keysConfigStr != null ? Paths.get(keysConfigStr) : null;

        // Load key mappings if config file is specified
        Map<String, Set<String>> keyMappings = new HashMap<>();
        if (keysConfigPath != null && Files.exists(keysConfigPath)) {
            keyMappings = readKeyMappings(keysConfigPath);
        }

        return MessageLogConfig.builder()
                .archiveEncryptionEnabled(encryptionEnabled)
                .archiveGrouping(grouping)
                .archiveGpgHomeDirectory(gpgHome)
                .archiveDefaultEncryptionKey(defaultKey)
                .archiveEncryptionKeysConfig(keysConfigPath)
                .encryptionKeyMappings(keyMappings)
                .build();
    }

    /**
     * Reads key mappings from encryption keys configuration file.
     * Format: INSTANCE/CLASS/CODE = KEY_ID
     * Note: A member can have multiple keys (one mapping per line).
     * Example:
     *   TEST/GOV/1234 = KEY1
     *   TEST/GOV/1234 = KEY2
     */
    public Map<String, Set<String>> readKeyMappings(Path mappingFile) throws IOException {
        log.info("Reading encryption key mappings from: {}", mappingFile);

        Map<String, Set<String>> mappings = new HashMap<>();

        for (String line : Files.readAllLines(mappingFile)) {
            line = line.trim();

            // Skip comments and empty lines
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            // Parse: KEY = VALUE
            int equalsIndex = line.indexOf('=');
            if (equalsIndex > 0) {
                String identifier = line.substring(0, equalsIndex).trim();
                String keyId = line.substring(equalsIndex + 1).trim();

                if (!identifier.isEmpty() && !keyId.isEmpty()) {
                    // Support multiple keys per member - accumulate into Set
                    mappings.computeIfAbsent(identifier, k -> new HashSet<>()).add(keyId);
                    log.debug("Mapped {} -> {}", identifier, keyId);
                }
            }
        }

        log.info("Loaded {} key mappings", mappings.size());
        return mappings;
    }

    private MessageLogConfig createDisabledConfig() {
        return MessageLogConfig.builder()
                .archiveEncryptionEnabled(false)
                .archiveGrouping("none")
                .archiveGpgHomeDirectory(Paths.get("/etc/xroad/gpghome"))
                .encryptionKeyMappings(Map.of())
                .build();
    }
}

