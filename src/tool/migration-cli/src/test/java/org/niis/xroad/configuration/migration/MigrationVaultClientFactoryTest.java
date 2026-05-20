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

package org.niis.xroad.configuration.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.springframework.vault.VaultException;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.web.client.ResourceAccessException;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SystemStubsExtension.class)
class MigrationVaultClientFactoryTest {

    @SystemStub
    private final EnvironmentVariables envVars = new EnvironmentVariables();

    // ----- env-var validation tests -----

    @Test
    void testCreateThrowsWhenHostMissing() {
        envVars.set(MigrationVaultClient.ENV_PORT, "8200");
        envVars.set(MigrationVaultClient.ENV_SCHEME, "https");
        envVars.set(MigrationVaultClient.ENV_TOKEN, "test-token");
        // HOST not set

        assertThatThrownBy(MigrationVaultClient::createAndPreflight)
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining(MigrationVaultClient.ENV_HOST);
    }

    @Test
    void testCreateThrowsWhenPortMissing() {
        envVars.set(MigrationVaultClient.ENV_HOST, "127.0.0.1");
        envVars.set(MigrationVaultClient.ENV_SCHEME, "https");
        envVars.set(MigrationVaultClient.ENV_TOKEN, "test-token");

        assertThatThrownBy(MigrationVaultClient::createAndPreflight)
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining(MigrationVaultClient.ENV_PORT);
    }

    @Test
    void testCreateThrowsWhenSchemeMissing() {
        envVars.set(MigrationVaultClient.ENV_HOST, "127.0.0.1");
        envVars.set(MigrationVaultClient.ENV_PORT, "8200");
        envVars.set(MigrationVaultClient.ENV_TOKEN, "test-token");

        assertThatThrownBy(MigrationVaultClient::createAndPreflight)
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining(MigrationVaultClient.ENV_SCHEME);
    }

    @Test
    void testCreateThrowsWhenTokenMissing() {
        envVars.set(MigrationVaultClient.ENV_HOST, "127.0.0.1");
        envVars.set(MigrationVaultClient.ENV_PORT, "8200");
        envVars.set(MigrationVaultClient.ENV_SCHEME, "https");

        assertThatThrownBy(MigrationVaultClient::createAndPreflight)
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining(MigrationVaultClient.ENV_TOKEN);
    }

    @Test
    void testCreateThrowsWhenHostBlank() {
        envVars.set(MigrationVaultClient.ENV_HOST, "");
        envVars.set(MigrationVaultClient.ENV_PORT, "8200");
        envVars.set(MigrationVaultClient.ENV_SCHEME, "https");
        envVars.set(MigrationVaultClient.ENV_TOKEN, "test-token");

        assertThatThrownBy(MigrationVaultClient::createAndPreflight)
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining(MigrationVaultClient.ENV_HOST);
    }

    @Test
    void testCreateThrowsWhenPortNotNumeric() {
        envVars.set(MigrationVaultClient.ENV_HOST, "127.0.0.1");
        envVars.set(MigrationVaultClient.ENV_PORT, "not-a-number");
        envVars.set(MigrationVaultClient.ENV_SCHEME, "https");
        envVars.set(MigrationVaultClient.ENV_TOKEN, "test-token");

        assertThatThrownBy(MigrationVaultClient::createAndPreflight)
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining(MigrationVaultClient.ENV_PORT)
                .hasMessageContaining("not-a-number");
    }

    // ----- preflight failure-mode tests -----

    @Test
    void testPreflightVaultExceptionPropagatesAsXrdRuntimeException() {
        VaultTemplate vaultTemplate = mock(VaultTemplate.class);
        VaultException cause = new VaultException("403 Forbidden");
        when(vaultTemplate.read(MigrationVaultClient.PREFLIGHT_PATH)).thenThrow(cause);

        assertThatThrownBy(() -> MigrationVaultClient.preflight(vaultTemplate))
                .isInstanceOf(XrdRuntimeException.class)
                .hasCause(cause);
    }

    @Test
    void testPreflightRestClientExceptionPropagatesAsXrdRuntimeException() {
        VaultTemplate vaultTemplate = mock(VaultTemplate.class);
        ResourceAccessException cause = new ResourceAccessException("Connection refused");
        when(vaultTemplate.read(MigrationVaultClient.PREFLIGHT_PATH)).thenThrow(cause);

        assertThatThrownBy(() -> MigrationVaultClient.preflight(vaultTemplate))
                .isInstanceOf(XrdRuntimeException.class)
                .hasCause(cause);
    }

    @Test
    void testPreflightNullResultPropagates() {
        VaultTemplate vaultTemplate = mock(VaultTemplate.class);
        when(vaultTemplate.read(MigrationVaultClient.PREFLIGHT_PATH)).thenReturn(null);

        assertThatThrownBy(() -> MigrationVaultClient.preflight(vaultTemplate))
                .isInstanceOf(XrdRuntimeException.class);
    }

    @Test
    void testPreflightExceptionMessageDoesNotContainTokenValue() {
        // even when the underlying cause carries operator-attacker-controlled
        // text, the XrdRuntimeException must not echo the token value back. The token value is held in
        // TokenAuthentication and never reaches preflight's exception path; this test guards regression.
        String tokenValue = "super-secret-token-12345";
        envVars.set(MigrationVaultClient.ENV_HOST, "127.0.0.1");
        envVars.set(MigrationVaultClient.ENV_PORT, "8200");
        envVars.set(MigrationVaultClient.ENV_SCHEME, "https");
        envVars.set(MigrationVaultClient.ENV_TOKEN, tokenValue);

        VaultTemplate vaultTemplate = mock(VaultTemplate.class);
        when(vaultTemplate.read(MigrationVaultClient.PREFLIGHT_PATH))
                .thenThrow(new VaultException("permission denied"));

        // Use the package-private preflight directly — createAndPreflight would actually open a socket.
        try {
            MigrationVaultClient.preflight(vaultTemplate);
        } catch (XrdRuntimeException e) {
            assertThat(e.getMessage()).doesNotContain(tokenValue);
            String chain = chainMessages(e);
            assertThat(chain).doesNotContain(tokenValue);
            return;
        }
        throw new AssertionError("preflight did not throw");
    }

    private static String chainMessages(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (cur.getMessage() != null) {
                sb.append(cur.getMessage()).append("\n");
            }
        }
        return sb.toString();
    }

}
