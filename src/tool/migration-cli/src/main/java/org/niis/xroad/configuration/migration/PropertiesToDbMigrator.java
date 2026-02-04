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

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

@Slf4j
public class PropertiesToDbMigrator extends BasePropertiesToDbMigrator {

    @Override
    Map<String, String> loadProperties(String filePath) {
        log.info("Loading properties from [{}].", filePath);

        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream(filePath)) {
            properties.load(fis);
        } catch (IOException e) {
            throw new MigrationException("Failed to read properties.");
        }

        return properties.stringPropertyNames()
                .stream()
                .collect(Collectors.toMap(k -> k, properties::getProperty));
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    public static void main(String[] args) {
        validateParams(args);
        if (args.length > 2) {
            new PropertiesToDbMigrator().migrate(args[0], args[1], args[2]);
        } else {
            new PropertiesToDbMigrator().migrate(args[0], args[1]);
        }
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private static void validateParams(String[] args) {
        if (args.length != 2 && args.length != 3) {
            logUsageAndThrow("Invalid number of arguments provided.");
        }
        LegacyConfigMigrationCLI.validateFilePath(args[0], "properties input file");
        LegacyConfigMigrationCLI.validateFilePath(args[1], "DB properties file");
    }

    private static void logUsageAndThrow(String message) {
        log.error("Usage: <input file> <db.properties file> [scope]");
        throw new IllegalArgumentException(message);
    }

}
