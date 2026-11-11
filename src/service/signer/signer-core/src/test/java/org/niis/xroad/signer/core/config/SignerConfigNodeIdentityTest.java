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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.core.node.NodeIdentity;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.properties.SystemProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(SystemStubsExtension.class)
class SignerConfigNodeIdentityTest {

    @SystemStub
    private final EnvironmentVariables variables = new EnvironmentVariables();

    @SystemStub
    private final SystemProperties properties = new SystemProperties();

    private final SignerConfig signerConfig = new SignerConfig();

    @Test
    void shouldReadNodeNameFromEnvironmentVariable() {
        variables.set("XROAD_NODE_NAME", "node_a");

        assertThat(signerConfig.nodeIdentity()).isEqualTo(new NodeIdentity("node_a", true));
    }

    @Test
    void shouldResolveDefaultNodeNameWhenEnvironmentVariableIsUnset() {
        assertThat(signerConfig.nodeIdentity()).isEqualTo(new NodeIdentity("node_0", false));
    }

    @Test
    void shouldIgnoreStaleConfigurationKeyWhenEnvironmentVariableIsUnset() {
        properties.set("xroad.admin-service.ha-node-name", "node_stale");

        assertThat(signerConfig.nodeIdentity()).isEqualTo(new NodeIdentity("node_0", false));
    }

    @Test
    void shouldFailStartupNamingTheRuleWhenTheEnvironmentVariableHoldsAnInvalidName() {
        variables.set("XROAD_NODE_NAME", "_bad");

        assertThatThrownBy(signerConfig::nodeIdentity)
                .isInstanceOfSatisfying(XrdRuntimeException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_PROPERTY_VALUE.code()))
                .hasMessageContaining("XROAD_NODE_NAME")
                .hasMessageContaining("^[A-Za-z0-9][A-Za-z0-9_-]{0,62}$");
    }
}
