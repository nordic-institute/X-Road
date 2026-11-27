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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(SystemStubsExtension.class)
class DbSourceConfigTest {

    @SystemStub
    private final EnvironmentVariables variables = new EnvironmentVariables();

    @Test
    void missingUrlMeansNoConfigurationDatabase() {
        variables.set("DB_CONFIG_SOURCE_USERNAME", "someone");

        assertThat(DbSourceConfig.loadValues("test-app").getUrl()).isNull();
    }

    @Test
    void validUrlIsKeptAsConfigured() {
        variables.set("DB_CONFIG_SOURCE_URL", "jdbc:postgresql://localhost:5432/serverconf?currentSchema=ss");
        variables.set("DB_CONFIG_SOURCE_USERNAME", "someone");

        var config = DbSourceConfig.loadValues("test-app");

        assertThat(config.getUrl()).isEqualTo("jdbc:postgresql://localhost:5432/serverconf?currentSchema=ss");
        assertThat(config.getUsername()).isEqualTo("someone");
        assertThat(config.getAppName()).isEqualTo("test-app");
        assertThat(config.getTableName()).isEqualTo("configuration_properties");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "?currentSchema=serverconf", "  ?currentSchema=serverconf",
            "?user=someone&password=hunter2", "?"})
    void urlWithoutBaseUrlFailsNamingTheSourceAndTheVariableWithoutEchoingTheValue(String url) {
        variables.set("DB_CONFIG_SOURCE_URL", url);

        assertThatThrownBy(() -> DbSourceConfig.loadValues("test-app"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Configuration database source")
                .hasMessageContaining("DB_CONFIG_SOURCE_URL")
                .hasMessageNotContaining("currentSchema")
                .hasMessageNotContaining("hunter2");
    }
}
