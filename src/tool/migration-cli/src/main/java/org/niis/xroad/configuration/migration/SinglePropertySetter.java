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

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class SinglePropertySetter extends BasePropertiesToDbMigrator {

    private final String propertyKey;
    private final String propertyValue;

    @Override
    Map<String, String> loadProperties(String filePath) {
        return Map.of(propertyKey, propertyValue);
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    public static void main(String[] args) {
        validateParams(args);
        new SinglePropertySetter(args[1], args[2])
                .migrate("cmdline", args[0], args.length > 3 ? args[3] : null);
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private static void validateParams(String[] args) {
        if (args.length != 3 && args.length != 4) {
            logUsageAndThrow("Invalid number of arguments provided.");
        }
        LegacyConfigMigrationCLI.validateFilePath(args[0], "DB properties file");
        if (StringUtils.isAnyBlank(args[1], args[2])) {
            logUsageAndThrow("Property key or value cannot be empty");
        }
    }

    private static void logUsageAndThrow(String message) {
        log.error("Usage: <db.properties file> <property key> <property value> [optional scope]");
        log.error("  Example: /etc/xroad/db.properties xroad.proxy.batch-signing-enabled true ");
        throw new IllegalArgumentException(message);
    }
}
