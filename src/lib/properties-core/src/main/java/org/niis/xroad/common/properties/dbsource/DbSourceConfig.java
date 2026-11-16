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

package org.niis.xroad.common.properties.dbsource;

import lombok.Getter;

import static java.lang.System.getenv;
import static java.util.Optional.ofNullable;

/**
 * Connection details for reading {@code configuration_properties}, taken from the process environment.
 * A {@code DB_CONFIG_SOURCE_URL} is what enables the source. A process without it has no configuration
 * database and resolves from the layers below the database: the settings file where that layer is enabled
 * (op-monitor on a native host), then the packaged defaults. A {@code DB_CONFIG_SOURCE_URL} that is set but
 * has no base URL (empty, whitespace-only, or only a query part) is a configuration error, never "no
 * database".
 */
@Getter
public class DbSourceConfig {

    private static final String ENV_VAR_DB_URL = "DB_CONFIG_SOURCE_URL";
    private static final String ENV_VAR_DB_USERNAME = "DB_CONFIG_SOURCE_USERNAME";
    private static final String ENV_VAR_TABLE_NAME = "DB_CONFIG_SOURCE_TABLE_NAME";
    private static final String ENV_VAR_DB_PASS = "DB_CONFIG_SOURCE_PASSWORD";
    private static final String ENV_VAR_DB_SCHEMA = "DB_CONFIG_SOURCE_SCHEMA";

    private static final String DEFAULT_TABLE_NAME = "configuration_properties";

    private String appName;
    private String url;
    private String username;
    private char[] password;
    private String tableName;
    private String schema;
    private String nodeName;

    public static DbSourceConfig loadValues(String appName) {
        return loadValues(appName, NodeNameProvider.shared());
    }

    /**
     * @param appName          application name
     * @param nodeNameProvider supplies the node whose rows are read
     * @return the connection details; {@link #getUrl()} is {@code null} when no configuration database is configured
     * @throws IllegalStateException when {@code DB_CONFIG_SOURCE_URL} is set but has no base URL
     */
    public static DbSourceConfig loadValues(String appName, NodeNameProvider nodeNameProvider) {
        DbSourceConfig config = new DbSourceConfig();
        config.appName = appName;
        config.nodeName = nodeNameProvider.nodeName().orElse(null);
        config.url = getenv(ENV_VAR_DB_URL);
        if (config.url != null && !hasBaseUrl(config.url)) {
            throw new IllegalStateException(("Configuration database source: %s is set but has no base URL; "
                    + "unset it or set a JDBC URL").formatted(ENV_VAR_DB_URL));
        }
        config.username = getenv(ENV_VAR_DB_USERNAME);
        config.password = ofNullable(getenv(ENV_VAR_DB_PASS))
                .map(String::toCharArray)
                .orElse(null);

        config.tableName = ofNullable(getenv(ENV_VAR_TABLE_NAME)).orElse(DEFAULT_TABLE_NAME);

        ofNullable(getenv(ENV_VAR_DB_SCHEMA)).ifPresent(s -> config.schema = s);

        return config;
    }

    private static boolean hasBaseUrl(String url) {
        var queryStart = url.indexOf('?');
        return !(queryStart < 0 ? url : url.substring(0, queryStart)).isBlank();
    }

}
