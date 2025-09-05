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

import java.util.Map;

@Slf4j
public class IniToDbMigrator {
    private static final String DEFAULT_DB_PROPERTIES_PATH = "/etc/xroad/db.properties";

    private final IniUtil iniUtil = new IniUtil();

    void migrate(String iniFilePath, String dbPropertiesPath) {
        log.info("Loading INI properties from [{}]", iniFilePath);
        Map<String, String> properties = iniUtil.loadToFlatMap(iniFilePath, "xroad");

        if (log.isDebugEnabled()) {
            log.debug("Loaded properties from file {}", iniFilePath);
            properties.forEach((k, v) -> log.debug("{}={}", k, v));
        }

        try (DbPropertiesRepository dbRepo = new DbPropertiesRepository(dbPropertiesPath)) {
            properties.forEach((key, value) -> {
                log.debug("Processing property {}={}", key, value);
                dbRepo.saveProperty(key, value);
            });
        }

        log.info("{} properties migrated to DB", properties.size());
    }

    public static void main(String[] args) {
        validateParams(args);

        IniToDbMigrator migrator = new IniToDbMigrator();
        String dbPropertiesPath = args.length == 2 ? args[1] : DEFAULT_DB_PROPERTIES_PATH;
        migrator.migrate(args[0], dbPropertiesPath);
    }

    private static void validateParams(String[] args) {
        if (args.length != 1 && args.length != 2) {
            logUsageAndThrow("Invalid number of arguments provided.");
        }
        LegacyConfigMigrationCLI.validateFilePath(args[0], "INI input file");
        if (args.length == 2) {
            LegacyConfigMigrationCLI.validateFilePath(args[1], "DB properties file");
        }
    }

    private static void logUsageAndThrow(String message) {
        log.error("Usage: <input file> [db.properties file]");
        throw new IllegalArgumentException(message);
    }
}
