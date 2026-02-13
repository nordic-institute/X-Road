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
import org.niis.xroad.migration.pgp.MessageLogConfigReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Migrates message log encryption key mappings from INI file to database.
 *
 * <p>Input format (INI file):
 * <pre>
 * TEST/GOV/1234 = B2343D46FF3C40F6
 * TEST/GOV/1234 = 92DA25CD74A678B1
 * TEST/COM/5678 = D014E1D708695CB7
 * </pre>
 *
 * <p>Output format (database properties for Quarkus):
 * <pre>
 * xroad.common.messagelog.archive-grouping-keys."TEST/GOV/1234"[0] = B2343D46FF3C40F6
 * xroad.common.messagelog.archive-grouping-keys."TEST/GOV/1234"[1] = 92DA25CD74A678B1
 * xroad.common.messagelog.archive-grouping-keys."TEST/COM/5678"[0] = D014E1D708695CB7
 * </pre>
 */
@Slf4j
public class MessageLogIniToDbMigrator extends BasePropertiesToDbMigrator {

    private static final String PROPERTY_PREFIX = "xroad.common.messagelog.archive-grouping-keys";

    @Override
    Map<String, String> loadProperties(String filePath) {
        log.info("Loading message log key mappings from [{}]", filePath);

        try {
            MessageLogConfigReader reader = new MessageLogConfigReader();
            Map<String, Set<String>> keyMappings = reader.readKeyMappings(Path.of(filePath));

            return convertToQuarkusFormat(keyMappings);
        } catch (Exception e) {
            throw new MigrationException("Failed to load message log key mappings from " + filePath, e);
        }
    }

    /**
     * Converts key mappings to Quarkus-compatible indexed property format.
     *
     * @param keyMappings Map of member IDs to sets of key IDs
     * @return Flat map of property keys to values
     */
    private Map<String, String> convertToQuarkusFormat(Map<String, Set<String>> keyMappings) {
        Map<String, String> properties = new HashMap<>();

        keyMappings.forEach((memberId, keyIds) -> {
            // Convert Set to List for consistent indexing
            List<String> keyList = new ArrayList<>(keyIds);

            for (int i = 0; i < keyList.size(); i++) {
                String propertyKey = String.format("%s.\"%s\"[%d]", PROPERTY_PREFIX, memberId, i);
                properties.put(propertyKey, keyList.get(i));
            }
        });

        return properties;
    }

    public static void main(String[] args) {
        validateParams(args);
        new MessageLogIniToDbMigrator().migrate(args[0], args[1], "messagelog");
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private static void validateParams(String[] args) {
        if (args.length != 2) {
            logUsageAndThrow("Invalid number of arguments provided.");
        }

        LegacyConfigMigrationCLI.validateFilePath(args[0], "message log mapping INI file");

        // Check if the file exists and is not empty
        Path mappingFile = Path.of(args[0]);
        if (!Files.exists(mappingFile)) {
            logUsageAndThrow("Mapping file does not exist: " + args[0]);
        }

        LegacyConfigMigrationCLI.validateFilePath(args[1], "DB properties file");
    }

    private static void logUsageAndThrow(String message) {
        log.error("Usage: <messagelog-mapping.ini file> <db.properties file>");
        log.error("  Example: /etc/xroad/messagelog/archive-encryption-mapping.ini /etc/xroad/db.properties");
        throw new IllegalArgumentException(message);
    }
}

