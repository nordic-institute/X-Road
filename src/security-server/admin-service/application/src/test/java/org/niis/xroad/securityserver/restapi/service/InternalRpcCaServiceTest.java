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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.util.CryptoUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.vault.NoopVaultKeyClient;
import org.niis.xroad.common.vault.VaultKeyClient;
import org.niis.xroad.securityserver.restapi.util.CertificateTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_RPC_CA_NOT_AVAILABLE;

@ExtendWith(MockitoExtension.class)
class InternalRpcCaServiceTest {

    private static final Pattern SINGLE_CERTIFICATE_BLOCK = Pattern.compile(
            "\\A-----BEGIN CERTIFICATE-----\\R([A-Za-z0-9+/=]+\\R)+-----END CERTIFICATE-----\\R?\\z");

    @Mock
    private VaultKeyClient vaultKeyClient;

    @Test
    void getInternalRpcCaShouldReturnTheCertificateReadFromTheSecretStore() {
        X509Certificate ca = CertificateTestUtils.getMockTopCaCertificate();
        when(vaultKeyClient.readInternalRpcCa()).thenReturn(Optional.of(ca));

        assertThat(new InternalRpcCaService(vaultKeyClient).getInternalRpcCa()).isEqualTo(ca);
    }

    @Test
    void getInternalRpcCaShouldFailWhenThereIsNoSecretStore() {
        var service = new InternalRpcCaService(new NoopVaultKeyClient());

        assertThatThrownBy(service::getInternalRpcCa)
                .isInstanceOfSatisfying(NotFoundException.class,
                        e -> assertThat(e.getErrorDeviation().code()).isEqualTo(INTERNAL_RPC_CA_NOT_AVAILABLE.code()));
    }

    @Test
    void downloadInternalRpcCaShouldReturnExactlyOnePemCertificateBlockAndNothingElse() {
        X509Certificate ca = CertificateTestUtils.getMockTopCaCertificate();
        when(vaultKeyClient.readInternalRpcCa()).thenReturn(Optional.of(ca));

        String pem = new String(new InternalRpcCaService(vaultKeyClient).downloadInternalRpcCa(), StandardCharsets.UTF_8);

        assertThat(pem).matches(SINGLE_CERTIFICATE_BLOCK);
        assertThat(pem).doesNotContain("PRIVATE KEY");
        assertThat(CryptoUtils.readCertificate(pem.getBytes(StandardCharsets.UTF_8))).isEqualTo(ca);
    }

    @Test
    void downloadInternalRpcCaTwiceShouldReturnIdenticalBytes() {
        when(vaultKeyClient.readInternalRpcCa()).thenReturn(Optional.of(CertificateTestUtils.getMockTopCaCertificate()));
        var service = new InternalRpcCaService(vaultKeyClient);

        assertThat(service.downloadInternalRpcCa()).isEqualTo(service.downloadInternalRpcCa());
    }

    @Test
    void downloadInternalRpcCaShouldFailWhenThereIsNoSecretStore() {
        var service = new InternalRpcCaService(new NoopVaultKeyClient());

        assertThatThrownBy(service::downloadInternalRpcCa).isInstanceOf(NotFoundException.class);
    }
}
