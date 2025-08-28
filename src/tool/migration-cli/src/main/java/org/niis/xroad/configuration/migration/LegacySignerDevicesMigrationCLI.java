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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LegacySignerDevicesMigrationCLI {

    public static void main(String[] args) {
        validateArgs(args);
        try {
            var input = args[0];
            var output = args[1];
            var migrator = new ConfigurationYamlMigrator("xroad.signer.modules");
            log.info("Migrating Signer devices configuration from {} to {}..", input, output);
            if (migrator.migrate(input, output)) {
                log.info("Migration successful");
            }

        } catch (Exception e) {
            throw new MigrationException("Migration failed", e);
        }
    }

    private static void validateArgs(String[] args) {
        if (args.length != 2) {
            logUsageAndThrow("Required arguments not provided");
        }
        if (!new File(args[0]).exists()) {
            throw new IllegalArgumentException("Input file does not exist: " + args[0]);
        }
        if (new File(args[1]).exists()) {
            throw new IllegalArgumentException("Output file already exists: " + args[1]);
        }
    }

    private static void logUsageAndThrow(String message) {
        log.error("Usage: <input-devices.ini-file> <output-file>");
        throw new IllegalArgumentException(message);
    }

}
