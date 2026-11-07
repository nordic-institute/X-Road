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
package org.niis.xroad.signer.core.config;

import io.smallrye.config.EnvConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.core.node.NodeIdentity;
import org.niis.xroad.common.properties.dbsource.NodeName;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignerConfigNodeIdentityTest {

    private final SignerConfig signerConfig = new SignerConfig();

    @Test
    void shouldReadNodeNameFromEnvVarWhenNoLocalYamlIsLoaded() {
        var config = new SmallRyeConfigBuilder()
                .withSources(new EnvConfigSource(Map.of("XROAD_ADMIN_SERVICE_HA_NODE_NAME", "node_a"), 300))
                .build();

        assertThat(resolve(config)).isEqualTo(new NodeIdentity("node_a", true));
    }

    @Test
    void shouldResolveDefaultNodeNameWhenNothingIsConfigured() {
        var config = new SmallRyeConfigBuilder().build();

        assertThat(resolve(config)).isEqualTo(new NodeIdentity("node_0", false));
    }

    @Test
    void shouldFailStartupNamingTheRuleWhenTheEnvVarHoldsAnInvalidName() {
        var config = new SmallRyeConfigBuilder()
                .withSources(new EnvConfigSource(Map.of("XROAD_ADMIN_SERVICE_HA_NODE_NAME", "_bad"), 300))
                .build();

        assertThatThrownBy(() -> resolve(config))
                .isInstanceOfSatisfying(XrdRuntimeException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_PROPERTY_VALUE.code()))
                .hasMessageContaining("^[A-Za-z0-9][A-Za-z0-9_-]{0,62}$");
    }

    private NodeIdentity resolve(SmallRyeConfig config) {
        Optional<String> configured = config.getOptionalValue(NodeName.PROPERTY_KEY, String.class);
        return signerConfig.nodeIdentity(configured);
    }
}
