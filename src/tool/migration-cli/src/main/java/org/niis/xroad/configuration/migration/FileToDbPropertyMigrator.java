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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Used to migrate the whole file contents as a single property value.
 */
@Slf4j
@RequiredArgsConstructor
public class FileToDbPropertyMigrator extends BasePropertiesToDbMigrator {

    private final String propertyKey;

    @Override
    Map<String, String> loadProperties(String filePath) {
        log.info("Loading file [{}].", filePath);
        try {
            return Map.of(propertyKey, Files.readString(Paths.get(filePath)));

        } catch (IOException e) {
            throw new MigrationException("Failed to read input file", e);
        }
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    public static void main(String[] args) {
        validateParams(args);
        new FileToDbPropertyMigrator(args[2]).migrate(args[0], args[1],
                args.length > 3 ? args[3] : null);
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private static void validateParams(String[] args) {
        if (args.length != 3 && args.length != 4) {
            logUsageAndThrow("Invalid number of arguments provided.");
        }
        LegacyConfigMigrationCLI.validateFilePath(args[0], "Input file");
        LegacyConfigMigrationCLI.validateFilePath(args[1], "DB properties file");
        if (StringUtils.isBlank(args[2])) {
            logUsageAndThrow("Property key cannot be empty");
        }
    }

    private static void logUsageAndThrow(String message) {
        log.error("Usage: <input file> <db.properties file> <property key> [scope]");
        throw new IllegalArgumentException(message);
    }

}
