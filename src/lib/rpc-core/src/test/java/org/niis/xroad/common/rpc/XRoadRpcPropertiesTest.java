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
package org.niis.xroad.common.rpc;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class XRoadRpcPropertiesTest {

    @Test
    void unsetIdentitySourceResolvesToOpenbao() {
        assertThat(properties(Map.of()).identitySource()).isEqualTo(RpcProperties.IdentitySource.OPENBAO);
    }

    @Test
    void identitySourceFileResolvesInAnyCase() {
        assertThat(properties(Map.of("xroad.common-rpc.identity-source", "file")).identitySource())
                .isEqualTo(RpcProperties.IdentitySource.FILE);
        assertThat(properties(Map.of("xroad.common-rpc.identity-source", "FILE")).identitySource())
                .isEqualTo(RpcProperties.IdentitySource.FILE);
    }

    @Test
    void fileIdentityPathsAreReadFromTheirKeys() {
        var properties = properties(Map.of(
                "xroad.common-rpc.file-identity.private-key-path", "/id/server.key",
                "xroad.common-rpc.file-identity.certificate-path", "/id/server.crt",
                "xroad.common-rpc.file-identity.trusted-ca-path", "/id/trusted-ca.crt"));

        assertThat(properties.fileIdentity()).isEqualTo(new RpcProperties.FileIdentity(
                Path.of("/id/server.key"), Path.of("/id/server.crt"), Path.of("/id/trusted-ca.crt")));
    }

    private static XRoadRpcProperties properties(Map<String, String> overrides) {
        return new XRoadRpcProperties(XRoadConfigBuilder.create()
                .register(CommonRpcConfigKeys.instance())
                .overrides(overrides)
                .build());
    }
}
