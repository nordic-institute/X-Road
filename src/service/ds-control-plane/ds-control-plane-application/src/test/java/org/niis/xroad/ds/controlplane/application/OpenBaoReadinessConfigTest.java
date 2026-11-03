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

package org.niis.xroad.ds.controlplane.application;

import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.source.yaml.YamlConfigSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class OpenBaoReadinessConfigTest {

    private static final int COMMON_CONFIG_ORDINAL = 220;
    private static final int APPLICATION_CONFIG_ORDINAL = 255;

    @ParameterizedTest
    @ValueSource(strings = {"edc.vault.hashicorp.health.check.enabled", "quarkus.vault.health.enabled"})
    void openBaoHealthChecksStayOutOfReadinessWhateverTheSharedConfigSays(String key) throws IOException {
        var common = getClass().getClassLoader().getResource("xroad-common.yaml");
        var application = getClass().getClassLoader().getResource("application.yaml");
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new YamlConfigSource(common, COMMON_CONFIG_ORDINAL),
                        new YamlConfigSource(application, APPLICATION_CONFIG_ORDINAL))
                .build();

        assertThat(config.getOptionalValue(key, Boolean.class)).contains(false);
    }
}
