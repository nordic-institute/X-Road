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

package org.niis.xroad.common.properties.config.impl;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.DeploymentMode;
import org.niis.xroad.common.properties.config.Scope;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class XRoadConfigBuilderTest {

    private final Scope scope = Scope.of("xroad.test");
    private final ConfigKey<String> withContainerDefault = scope.string("with-container")
            .withDefaultValue("native").withContainerDefaultValue("container").build();
    private final ConfigKey<String> nativeOnly = scope.string("native-only")
            .withDefaultValue("native-only").build();
    private final ConfigKeyProvider provider = () -> scope;

    @Test
    void nativeModeResolvesRegularDefault() {
        var config = XRoadConfigBuilder.create().register(provider).build();

        assertThat(config.get(withContainerDefault).value()).isEqualTo("native");
        assertThat(config.get(nativeOnly).value()).isEqualTo("native-only");
    }

    @Test
    void containerModeResolvesContainerDefaultWhenDeclared() {
        var config = XRoadConfigBuilder.create().register(provider)
                .deploymentMode(DeploymentMode.CONTAINERIZED)
                .build();

        assertThat(config.get(withContainerDefault).value()).isEqualTo("container");
    }

    @Test
    void containerModeFallsBackToRegularDefaultWhenNoContainerDefault() {
        var config = XRoadConfigBuilder.create().register(provider)
                .deploymentMode(DeploymentMode.CONTAINERIZED)
                .build();

        assertThat(config.get(nativeOnly).value()).isEqualTo("native-only");
    }

    @Test
    void dbOverrideWinsOverContainerDefault() {
        var config = XRoadConfigBuilder.create().register(provider)
                .deploymentMode(DeploymentMode.CONTAINERIZED)
                .overrides(Map.of("xroad.test.with-container", "overridden"))
                .build();

        assertThat(config.get(withContainerDefault).value()).isEqualTo("overridden");
    }
}
