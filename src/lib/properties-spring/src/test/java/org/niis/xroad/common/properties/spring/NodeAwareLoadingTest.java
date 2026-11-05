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
package org.niis.xroad.common.properties.spring;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.AdminServiceConfigKeys;
import org.niis.xroad.common.properties.dbsource.NodeConfigTestTable;
import org.niis.xroad.common.properties.dbsource.NodeConfigTestTable.Row;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The post-processor, {@link SpringConditionConfig} and the application {@code XRoadConfig} bean all load the
 * stored settings through the same node provider, so they resolve the same value for a key that has a node row.
 */
@ExtendWith(SystemStubsExtension.class)
class NodeAwareLoadingTest {

    private static final String KEY = AdminServiceConfigKeys.REQUEST_SIZE_LIMIT_BINARY_UPLOAD.key();
    private static final String SOURCE_NAME = "xroad-framework-source";

    private static Map<String, String> databaseEnvironment;

    @SystemStub
    private final EnvironmentVariables variables = new EnvironmentVariables();

    @BeforeAll
    static void createTable() throws SQLException {
        databaseEnvironment = NodeConfigTestTable.create("springnodeaware", List.of(
                new Row(KEY, "20MB", null),
                new Row(KEY, "30MB", "node_1"),
                new Row(KEY, "40MB", "node_2")));
    }

    @BeforeEach
    void pointAtTheDatabase() {
        databaseEnvironment.forEach(variables::set);
    }

    @Test
    void optedInAppResolvesTheNodeValueInAllThreeLoadPoints() {
        var environment = environment(true);

        assertThat(publishedValue(environment)).isEqualTo("30MB");
        assertThat(conditionValue(environment)).isEqualTo("30MB");
        assertThat(beanValue(environment)).isEqualTo("30MB");
    }

    @Test
    void appThatDidNotOptInGetsTheSharedValueInAllThreeLoadPoints() {
        var environment = environment(false);

        assertThat(publishedValue(environment)).isEqualTo("20MB");
        assertThat(conditionValue(environment)).isEqualTo("20MB");
        assertThat(beanValue(environment)).isEqualTo("20MB");
    }

    private static StandardEnvironment environment(boolean optedIn) {
        var properties = new HashMap<String, Object>();
        properties.put("spring.application.name", "admin-service");
        properties.put("xroad.admin-service.ha-node-name", "node_1");
        if (optedIn) {
            properties.put(SpringNodeNameProvider.NODE_AWARE_PROPERTY, "true");
        }
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        return environment;
    }

    private static String publishedValue(StandardEnvironment environment) {
        new XRoadFrameworkPropertySourceEnvironmentPostProcessor()
                .postProcessEnvironment(environment, new SpringApplication());
        var value = environment.getPropertySources().get(SOURCE_NAME).getProperty(KEY);
        environment.getPropertySources().remove(SOURCE_NAME);
        return String.valueOf(value);
    }

    private static String conditionValue(StandardEnvironment environment) {
        return SpringConditionConfig.resolve(environment, AdminServiceConfigKeys.instance())
                .value(AdminServiceConfigKeys.REQUEST_SIZE_LIMIT_BINARY_UPLOAD);
    }

    private static String beanValue(StandardEnvironment environment) {
        return XRoadConfigBuilder.create()
                .register(AdminServiceConfigKeys.instance())
                .dbOverrides("admin-service", SpringNodeNameProvider.forEnvironment(environment))
                .build()
                .value(AdminServiceConfigKeys.REQUEST_SIZE_LIMIT_BINARY_UPLOAD);
    }
}
