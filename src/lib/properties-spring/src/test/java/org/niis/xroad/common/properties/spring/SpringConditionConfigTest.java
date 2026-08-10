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

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.config.Category;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.FrameworkPublishedConfig;
import org.niis.xroad.common.properties.config.Prefix;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring-side DSL wiring, in place of the deleted {@code DbPropertySourceEnvironmentPostProcessor}:
 * condition evaluation resolves DB overrides and packaged defaults, and deliberately ignores
 * {@code xroad.*} values sitting in the Spring {@code Environment} (yaml / env / system properties),
 * so a bean-registration decision cannot disagree with the running application.
 *
 * <p>No config database is configured here ({@code DB_CONFIG_SOURCE_URL} unset), so resolution falls
 * through to the declared defaults.
 */
class SpringConditionConfigTest {

    @Test
    void resolvesDeclaredNativeDefault() {
        var prefix = Prefix.of(Category.PROXY_UI_API, "xroad.condition-test-native");
        var key = prefix.integer("interval").withDefaultValue(60).build();
        ConfigKeyProvider provider = ConfigKeyProvider.forPrefix(prefix);

        var config = SpringConditionConfig.resolve(new MockEnvironment(), provider);

        assertThat(config.value(key)).isEqualTo(60);
    }

    @Test
    void prefersContainerDefaultUnderTheContainerizedProfile() {
        var prefix = Prefix.of(Category.PROXY_UI_API, "xroad.condition-test-container");
        var key = prefix.string("listen-address")
                .withDefaultValue("127.0.0.1")
                .withContainerDefaultValue("0.0.0.0")
                .build();
        ConfigKeyProvider provider = ConfigKeyProvider.forPrefix(prefix);
        var environment = new MockEnvironment();
        environment.setActiveProfiles("containerized");

        var config = SpringConditionConfig.resolve(environment, provider);

        assertThat(config.value(key)).isEqualTo("0.0.0.0");
    }

    @Test
    void publishesNothingToTheFrameworkWithoutAConfiguredDatabase() {
        var prefix = Prefix.of(Category.PROXY_UI_API, "xroad.condition-test-framework");
        prefix.integer("request-size-limit").withDefaultValue(10).publishedToFramework().build();
        ConfigKeyProvider provider = ConfigKeyProvider.forPrefix(prefix);

        assertThat(FrameworkPublishedConfig.storedOverrides("proxy-ui-api", List.of(provider)))
                .as("declared defaults must never be published — the framework already has them")
                .isEmpty();
    }

    @Test
    void ignoresXroadValuesPresentInTheSpringEnvironment() {
        var prefix = Prefix.of(Category.PROXY_UI_API, "xroad.condition-test-ignored");
        var key = prefix.bool("rate-limit-enabled").withDefaultValue(false).build();
        ConfigKeyProvider provider = ConfigKeyProvider.forPrefix(prefix);
        var environment = new MockEnvironment()
                .withProperty("xroad.condition-test-ignored.rate-limit-enabled", "true");

        var config = SpringConditionConfig.resolve(environment, provider);

        assertThat(config.value(key))
                .as("a yaml/env value must not flip a condition the application itself resolves from the DSL")
                .isFalse();
    }
}
