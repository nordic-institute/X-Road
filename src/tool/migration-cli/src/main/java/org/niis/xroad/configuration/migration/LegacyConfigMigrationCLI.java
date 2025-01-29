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
import org.apache.commons.configuration2.ex.ConfigurationException;

import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LegacyConfigMigrationCLI {

    public static void main(String[] args) {
        try {
            validateArgs(args);
            var input = args[0];
            var output = args[1];

            log.info("Migrating configuration from {} to {}..", input, output);
            var migrator = new ConfigurationMigrator();

            if (migrator.migrate(input, output)) {
                log.info("Migration successful");
            } else {
                throw new MigrationException("INI to YAML migration has failed. See logs for more details.");
            }
        } catch (NoSuchFileException e) {
            throw new MigrationException("Configuration file does not exist", e);
        } catch (ConfigurationException | IOException e) {
            throw new MigrationException("Error while loading configuration", e);
        }
    }


    private static void validateArgs(String[] args) {
        if (args.length != 2) {
            log.error("Usage: <input file> <output file>");
            throw new IllegalArgumentException("Invalid number of arguments. Usage: <input file> <output file>");
        }
        if (!isValidPath(args[0])) {
            throw new IllegalArgumentException("Invalid input file path");
        }
        if (!isValidPath(args[1])) {
            throw new IllegalArgumentException("Invalid output file path");
        }
        if (!new File(args[0]).exists()) {
            throw new IllegalArgumentException("Input file does not exist");
        }
    }

    private static boolean isValidPath(String path) {
        try {
            var resolvedPath = Paths.get(path);
            return !resolvedPath.toFile().isDirectory();
        } catch (Exception e) {
            return false;
        }
    }
}
