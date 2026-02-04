/*
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
public class EnvironmentValidator {
    private static final Path OLD_STYLE_LOCAL_INI_PATH = Paths.get("/etc/xroad/local.ini");
    private static final Path LOCAL_INI_PATH = Paths.get("/etc/xroad/conf.d/local.ini");
    private static final Path DATABASE_PROPERTIES_PATH = Paths.get("/etc/xroad/database.properties");

    public void run() {

        var checks = List.<Runnable>of(this::checkForLocalIni, this::checkForDbProperties);

        for (var check : checks) {
            log.info("Running check {} of {}", checks.indexOf(check) + 1, checks.size());
            log.info("-------------------------------------------------");
            check.run();
            log.info("-------------------------------------------------");
        }

        log.info("Checks finished.");
    }

    private void checkForLocalIni() {
        log.info("Checking for if config file {} exists...", OLD_STYLE_LOCAL_INI_PATH);
        if (Files.exists(OLD_STYLE_LOCAL_INI_PATH)) {
            log.warn("Please verify all configurations in {} and move those that are still relevant to {}",
                    OLD_STYLE_LOCAL_INI_PATH, LOCAL_INI_PATH);
            log.warn("Note that {} was only used for reading 'center.ha-node-name' config when backup "
                            + "or restore of Central Server was executed",
                    OLD_STYLE_LOCAL_INI_PATH);
        }
    }

    private void checkForDbProperties() {
        log.info("Checking for database config file {} exists...", DATABASE_PROPERTIES_PATH);
        if (!Files.exists(DATABASE_PROPERTIES_PATH)) {
            log.error("Database configuration file {} does not exist.", DATABASE_PROPERTIES_PATH);
            log.error("Please create it and configure database connection properties "
                    + "OR move existing database configuration file to match required path: {} "
                    + "or else application will not start.", DATABASE_PROPERTIES_PATH);
        }
    }

}
