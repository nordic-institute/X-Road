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
package org.niis.xroad.common.vault.quarkus;

import io.quarkus.vault.VaultKVSecretEngine;
import io.quarkus.vault.client.VaultClientException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.vault.VaultClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuarkusVaultClientTest {

    private static final String PATH = "message-log/archival/pgp/secret-key";

    @Mock
    private VaultKVSecretEngine kvSecretEngine;

    @Test
    void aNotFoundSecretIsReportedAsMissing() {
        when(kvSecretEngine.readSecret(PATH)).thenThrow(notFound());

        var vaultClient = new QuarkusVaultClient(kvSecretEngine);

        assertThat(vaultClient.getMLogArchivalSigningSecretKey()).isEmpty();
    }

    @Test
    void aVaultInfraFailureIsNotReportedAsMissing() {
        when(kvSecretEngine.readSecret(PATH)).thenThrow(serverError());

        var vaultClient = new QuarkusVaultClient(kvSecretEngine);

        assertThatThrownBy(vaultClient::getMLogArchivalSigningSecretKey).isInstanceOf(VaultClientException.class);
    }

    @Test
    void getDsHttpsTlsCredentialsDistinguishesMissingSecretFromVaultOutage() {
        when(kvSecretEngine.readSecret(VaultClient.DS_HTTPS_TLS_CREDENTIALS_PATH)).thenThrow(serverError());

        var vaultClient = new QuarkusVaultClient(kvSecretEngine);

        assertThatThrownBy(vaultClient::getDsHttpsTlsCredentials)
                .isInstanceOf(VaultClientException.class)
                .isNotInstanceOf(XrdRuntimeException.class);
    }

    @Test
    void getDsHttpsTlsCredentialsMapsAMissingSecretToXrdMissingSecret() {
        when(kvSecretEngine.readSecret(VaultClient.DS_HTTPS_TLS_CREDENTIALS_PATH)).thenThrow(notFound());

        var vaultClient = new QuarkusVaultClient(kvSecretEngine);

        assertThatThrownBy(vaultClient::getDsHttpsTlsCredentials)
                .isInstanceOf(XrdRuntimeException.class)
                .matches(e -> ((XrdRuntimeException) e).isCausedBy(ErrorCode.MISSING_SECRET));
    }

    @Test
    void aSuccessfulReadIsReturned() {
        when(kvSecretEngine.readSecret(PATH)).thenReturn(Map.of("payload", "armored-key"));

        var vaultClient = new QuarkusVaultClient(kvSecretEngine);

        assertThat(vaultClient.getMLogArchivalSigningSecretKey()).contains("armored-key");
    }

    private static VaultClientException notFound() {
        return new VaultClientException("read", PATH, 404, "not found", null);
    }

    private static VaultClientException serverError() {
        return new VaultClientException("read", PATH, 500, "internal error", null);
    }
}
