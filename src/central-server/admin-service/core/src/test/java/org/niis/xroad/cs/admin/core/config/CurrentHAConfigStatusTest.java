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
package org.niis.xroad.cs.admin.core.config;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.vault.spring.NodeIdentityConfig;
import org.niis.xroad.cs.admin.api.dto.HAConfigStatus;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentHAConfigStatusTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NodeIdentityConfig.class, CurrentHAConfigStatus.class);

    @Test
    void shouldBeUnconfiguredWithDefaultNameWhenNodeNameIsBlank() {
        contextRunner.run(context -> {
            var status = context.getBean(HAConfigStatus.class);

            assertThat(status.getCurrentHaNodeName()).isEqualTo("node_0");
            assertThat(status.isHaConfigured()).isFalse();
        });
    }

    @Test
    void shouldBeUnconfiguredWhenNodeNameIsOnlyWhitespace() {
        contextRunner.withPropertyValues("xroad.admin-service.ha-node-name= ").run(context ->
                assertThat(context.getBean(HAConfigStatus.class).isHaConfigured()).isFalse());
    }

    @Test
    void shouldBeConfiguredWithTheGivenNodeName() {
        contextRunner.withPropertyValues("xroad.admin-service.ha-node-name=node_a").run(context -> {
            var status = context.getBean(HAConfigStatus.class);

            assertThat(status.getCurrentHaNodeName()).isEqualTo("node_a");
            assertThat(status.isHaConfigured()).isTrue();
        });
    }

    @Test
    void shouldKeepAnExplicitDefaultNameConfigured() {
        contextRunner.withPropertyValues("xroad.admin-service.ha-node-name=node_0").run(context -> {
            var status = context.getBean(HAConfigStatus.class);

            assertThat(status.getCurrentHaNodeName()).isEqualTo("node_0");
            assertThat(status.isHaConfigured()).isTrue();
        });
    }
}
