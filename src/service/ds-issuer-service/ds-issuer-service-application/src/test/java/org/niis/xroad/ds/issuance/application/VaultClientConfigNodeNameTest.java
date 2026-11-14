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
package org.niis.xroad.ds.issuance.application;

import io.quarkus.vault.VaultKVSecretEngine;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.NodeProperties;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class VaultClientConfigNodeNameTest {

    private final VaultKVSecretEngine kvSecretEngine = mock(VaultKVSecretEngine.class);

    @Test
    void aConfiguredNodeNameScopesTheClientToThatNode() throws Exception {
        storePinWith(new EnvironmentVariables(NodeProperties.NODE_NAME_ENV_VARIABLE, "node_b"));

        verify(kvSecretEngine).writeSecret("signer/token-pins/node_b/tok", Map.of("pin", "1234"));
    }

    @Test
    void aBlankNodeNameScopesTheClientToNodeZero() throws Exception {
        storePinWith(new EnvironmentVariables(NodeProperties.NODE_NAME_ENV_VARIABLE, " "));

        verify(kvSecretEngine).writeSecret("signer/token-pins/node_0/tok", Map.of("pin", "1234"));
    }

    @Test
    void noNodeNameScopesTheClientToNodeZero() throws Exception {
        storePinWith(new EnvironmentVariables().remove(NodeProperties.NODE_NAME_ENV_VARIABLE));

        verify(kvSecretEngine).writeSecret("signer/token-pins/node_0/tok", Map.of("pin", "1234"));
    }

    private void storePinWith(EnvironmentVariables environment) throws Exception {
        environment.execute(() ->
                new VaultClientConfig().vaultClient(kvSecretEngine).setTokenPin("tok", "1234".toCharArray()));
    }
}
