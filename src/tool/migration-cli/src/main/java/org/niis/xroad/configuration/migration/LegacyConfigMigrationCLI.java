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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Paths;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LegacyConfigMigrationCLI {

    public static void main(String[] args) {
        validateArgs(args);
        try {
            var input = args[0];

            if (input.endsWith(".properties")) {
                var migrator = new LegacyPropertiesMigrator();
                log.info("Migrating properties from {}..", input);
                if (migrator.migrateProperties(input)) {
                    log.info("Properties migration successful");
                }
            } else {
                var output = args[1];
                var migrator = new ConfigurationYamlMigrator();
                log.info("Migrating INI from {} to {}..", input, output);
                if (migrator.migrate(input, output)) {
                    log.info("INI migration successful");
                }
            }
        } catch (Exception e) {
            throw new MigrationException("Migration failed", e);
        }
    }


    private static void validateArgs(String[] args) {
        // Check if no arguments provided
        if (args.length == 0) {
            logUsageAndThrow("No arguments provided");
        }

        // Get input file
        String inputFile = args[0];
        boolean isPropertiesFile = inputFile.endsWith(".properties");

        // Validate number of arguments based on file type
        if (isPropertiesFile && args.length != 1) {
            logUsageAndThrow("Properties file migration requires only input file");
        }
        if (!isPropertiesFile && args.length != 2) {
            logUsageAndThrow("INI file migration requires both input and output files");
        }

        // Validate input file
        validateFilePath(inputFile, "input");
        if (!new File(inputFile).exists()) {
            throw new IllegalArgumentException("Input file does not exist: " + inputFile);
        }

        // Validate output file if provided
        if (args.length == 2) {
            validateFilePath(args[1], "output");
        }
    }

    public static void validateFilePath(String path, String fileType) {
        try {
            var resolvedPath = Paths.get(path);
            if (resolvedPath.toFile().isDirectory()) {
                throw new IllegalArgumentException(
                        String.format("Invalid %s file path: %s is a directory", fileType, path));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    String.format("Invalid %s file path: %s", fileType, path), e);
        }
    }

    private static void logUsageAndThrow(String message) {
        log.error("Usage: <input file> [output file]");
        throw new IllegalArgumentException(message);
    }

}
