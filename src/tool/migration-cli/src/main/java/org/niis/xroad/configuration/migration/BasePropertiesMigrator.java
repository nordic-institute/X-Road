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
import java.util.Scanner;

@Slf4j
public abstract class BasePropertiesMigrator {

    abstract Map<String, String> loadProperties(String filePath);

    public void migrate(String filePath, String dbPropertiesPath) {
        migrate(filePath, dbPropertiesPath, null);
    }

    public void migrate(String filePath, String dbPropertiesPath, String scope) {
        Map<String, String> properties = loadProperties(filePath);

        if (log.isDebugEnabled()) {
            log.debug("Loaded properties from file {}:", filePath);
            properties.forEach((k, v) -> log.debug("{}={}", k, v));
        }

        if (confirmProceed(properties)) {
            saveToDb(properties, dbPropertiesPath, scope);
            log.info("Total values migrated to DB: {}", properties.size());
        } else {
            log.info("Skipping migration.");
        }
    }

    boolean confirmProceed(Map<String, String> properties) {
        System.out.println("The following properties will be migrated to database (if value exists it will be OVERRIDDEN):");
        properties.forEach((k, v) -> System.out.printf(" - %s%n", k));

        Scanner scanner = new Scanner(System.in);
        System.out.print("Proceed with migration? [y/N] ");
        String input = scanner.nextLine().trim();
        return "y".equalsIgnoreCase(input) || "yes".equalsIgnoreCase(input);
    }

    void saveToDb(Map<String, String> properties, String dbPropertiesPath, String scope) {
        try (DbRepository dbRepo = new DbRepository(dbPropertiesPath)) {
            properties.forEach((key, value) -> {
                log.debug("Saving property {}={}, with scope [{}]", key, value, scope == null ? "" : scope);
                dbRepo.saveProperty(String.valueOf(key), String.valueOf(value), scope);
            });
        }
    }

}
