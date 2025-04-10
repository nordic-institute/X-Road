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
package org.niis.xroad.common.rpc.spring;

import ee.ria.xroad.common.CodedException;

import io.grpc.util.CertificateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.properties.CommonRpcProperties;
import org.niis.xroad.common.rpc.vault.VaultKeyClient;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultCertificateRequest;
import org.springframework.vault.support.VaultCertificateResponse;

import java.io.ByteArrayInputStream;
import java.time.Duration;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;

@Slf4j
@RequiredArgsConstructor
public class SpringVaultKeyClient implements VaultKeyClient {
    private final CommonRpcProperties.CertificateProvisionProperties certificateProvisionProperties;
    private final VaultTemplate vaultTemplate;

    @Override
    public VaultKeyData provisionNewCerts() throws Exception {
        var request = buildVaultCertificateRequest();

        VaultCertificateResponse vaultResponse = vaultTemplate.opsForPki(certificateProvisionProperties.secretStorePkiPath())
                .issueCertificate(certificateProvisionProperties.issuanceRoleName(), request);

        if (vaultResponse.getData() != null) {
            var data = vaultResponse.getData();
            var cert = CertificateUtils.getX509Certificates(new ByteArrayInputStream(data.getCertificate().getBytes()));
            var privateKey = CertificateUtils.getPrivateKey(new ByteArrayInputStream(data.getPrivateKey().getBytes()));
            var certTrustChain = CertificateUtils.getX509Certificates(new ByteArrayInputStream(data.getIssuingCaCertificate().getBytes()));

            return new VaultKeyData(
                    cert,
                    privateKey,
                    certTrustChain
            );
        } else {
            throw new CodedException(X_INTERNAL_ERROR, "Failed to get certificate from Vault. Data is null.");
        }
    }

    private VaultCertificateRequest buildVaultCertificateRequest() {
        var builder = VaultCertificateRequest.builder()
                .ttl(Duration.ofMinutes(certificateProvisionProperties.ttlMinutes()))
                .format(CERTIFICATE_FORMAT)
                .privateKeyFormat(PKCS8_FORMAT);

        if (certificateProvisionProperties.commonName() != null) {
            builder.commonName(certificateProvisionProperties.commonName());
        }
        if (certificateProvisionProperties.altNames() != null) {
            builder.altNames(certificateProvisionProperties.altNames());
        }
        if (certificateProvisionProperties.ipSubjectAltNames() != null) {
            builder.ipSubjectAltNames(certificateProvisionProperties.ipSubjectAltNames());
        }
        return builder.build();
    }
}
