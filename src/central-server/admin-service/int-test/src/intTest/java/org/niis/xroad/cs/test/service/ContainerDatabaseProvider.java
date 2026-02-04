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
package org.niis.xroad.cs.test.service;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.cs.test.IntTestContainerSetup;
import org.springframework.stereotype.Component;

import static org.niis.xroad.cs.test.IntTestContainerSetup.POSTGRES;

@Component
@RequiredArgsConstructor
public class ContainerDatabaseProvider {
    private static final String ROOT_PROPERTIES_PATH = "/etc/xroad.properties";
    private static final String DB_PROPERTIES_PATH = "/etc/xroad/db.properties";
    private final IntTestContainerSetup intTestSetup;

    private String jdbcUrl;
    private String adminUsername;
    private String adminPassword;
    private String username;
    private String password;

    public String getJdbcUrl() {
        if (jdbcUrl == null) {
            var postgresContainer = intTestSetup.getContainerMapping(POSTGRES, IntTestContainerSetup.Port.DB);

            jdbcUrl = "jdbc:postgresql://%s:%d/%s?currentSchema=centerui,public".formatted(
                    postgresContainer.host(),
                    postgresContainer.port(),
                    "centerui_production");
        }
        return jdbcUrl;
    }

    public String getAdminUsername() {
        if (adminUsername == null) {
            var result = intTestSetup.execInContainer(IntTestContainerSetup.CS,
                    "awk", "/centerui.database.admin_user/ {print $3}", ROOT_PROPERTIES_PATH);
            adminUsername = result.getStdout().trim();
        }
        return adminUsername;
    }

    public String getAdminPassword() {
        if (adminPassword == null) {
            var result = intTestSetup.execInContainer(IntTestContainerSetup.CS,
                    "awk", "/centerui.database.admin_password/ {print $3}", ROOT_PROPERTIES_PATH);
            adminPassword = result.getStdout().trim();
        }
        return adminPassword;
    }

    public String getUsername() {
        if (username == null) {
            var result = intTestSetup.execInContainer(IntTestContainerSetup.CS,
                    "awk", "/spring.datasource.username/ {print $3}", DB_PROPERTIES_PATH);
            username = result.getStdout().trim();
        }
        return username;
    }

    public String getPassword() {
        if (password == null) {
            var result = intTestSetup.execInContainer(IntTestContainerSetup.CS,
                    "awk", "/spring.datasource.password/ {print $3}", DB_PROPERTIES_PATH);
            password = result.getStdout().trim();
        }
        return password;
    }
}
