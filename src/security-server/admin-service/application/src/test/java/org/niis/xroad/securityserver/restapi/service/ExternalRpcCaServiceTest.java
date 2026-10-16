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
import org.niis.xroad.common.vault.NoopVaultCaClient;
import org.niis.xroad.common.vault.VaultCaClient;
import org.niis.xroad.securityserver.restapi.util.CertificateTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.EXTERNAL_RPC_CA_NOT_AVAILABLE;

@ExtendWith(MockitoExtension.class)
class ExternalRpcCaServiceTest {

    private static final Pattern SINGLE_CERTIFICATE_BLOCK = Pattern.compile(
            "\\A-----BEGIN CERTIFICATE-----\\R([A-Za-z0-9+/=]+\\R)+-----END CERTIFICATE-----\\R?\\z");

    @Mock
    private VaultCaClient rpcCaClient;

    @Test
    void getExternalRpcCaShouldReturnTheCertificateReadFromTheSecretStore() {
        X509Certificate ca = CertificateTestUtils.getMockTopCaCertificate();
        when(rpcCaClient.readCaCertificate()).thenReturn(Optional.of(ca));

        assertThat(new ExternalRpcCaService(rpcCaClient).getExternalRpcCa()).isEqualTo(ca);
    }

    @Test
    void getExternalRpcCaShouldFailWhenThereIsNoSecretStore() {
        var service = new ExternalRpcCaService(new NoopVaultCaClient());

        assertThatThrownBy(service::getExternalRpcCa)
                .isInstanceOfSatisfying(NotFoundException.class,
                        e -> assertThat(e.getErrorDeviation().code()).isEqualTo(EXTERNAL_RPC_CA_NOT_AVAILABLE.code()));
    }

    @Test
    void downloadExternalRpcCaShouldReturnExactlyOnePemCertificateBlockAndNothingElse() {
        X509Certificate ca = CertificateTestUtils.getMockTopCaCertificate();
        when(rpcCaClient.readCaCertificate()).thenReturn(Optional.of(ca));

        String pem = new String(new ExternalRpcCaService(rpcCaClient).downloadExternalRpcCa(), StandardCharsets.UTF_8);

        assertThat(pem).matches(SINGLE_CERTIFICATE_BLOCK);
        assertThat(pem).doesNotContain("PRIVATE KEY");
        assertThat(CryptoUtils.readCertificate(pem.getBytes(StandardCharsets.UTF_8))).isEqualTo(ca);
    }

    @Test
    void downloadExternalRpcCaTwiceShouldReturnIdenticalBytes() {
        when(rpcCaClient.readCaCertificate()).thenReturn(Optional.of(CertificateTestUtils.getMockTopCaCertificate()));
        var service = new ExternalRpcCaService(rpcCaClient);

        assertThat(service.downloadExternalRpcCa()).isEqualTo(service.downloadExternalRpcCa());
    }

    @Test
    void downloadExternalRpcCaShouldFailWhenThereIsNoSecretStore() {
        var service = new ExternalRpcCaService(new NoopVaultCaClient());

        assertThatThrownBy(service::downloadExternalRpcCa).isInstanceOf(NotFoundException.class);
    }
}
