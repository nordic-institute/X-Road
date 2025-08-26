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
package org.niis.xroad.common.tls.spring.vault;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.tls.vault.VaultKeyClient;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultCertificateRequest;
import org.springframework.vault.support.VaultCertificateResponse;

import java.io.ByteArrayInputStream;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;

@Slf4j
@RequiredArgsConstructor
public class SpringVaultKeyClient implements VaultKeyClient {
    private final VaultTemplate vaultTemplate;
    private final String secretStorePkiPath;
    private final Duration ttl;
    private final String issuanceRoleName;
    private final String commonName;
    private final List<String> altNames;
    private final List<String> ipSubjectAltNames;

    @Override
    public VaultKeyData provisionNewCerts() throws Exception {
        var request = buildVaultCertificateRequest();

        VaultCertificateResponse vaultResponse = vaultTemplate.opsForPki(secretStorePkiPath)
                .issueCertificate(issuanceRoleName, request);

        if (vaultResponse.getData() != null) {
            var data = vaultResponse.getData();
            var cert = CryptoUtils.readCertificates(new ByteArrayInputStream(data.getCertificate().getBytes()));
            var privateKey = CryptoUtils.getPrivateKey(new ByteArrayInputStream(data.getPrivateKey().getBytes()));
            var certTrustChain = CryptoUtils.readCertificates(new ByteArrayInputStream(data.getIssuingCaCertificate().getBytes()));

            return new VaultKeyData(
                    cert.toArray(new X509Certificate[0]),
                    privateKey,
                    certTrustChain.toArray(new X509Certificate[0])
            );
        } else {
            throw new CodedException(X_INTERNAL_ERROR, "Failed to get certificate from Vault. Data is null.");
        }
    }

    private VaultCertificateRequest buildVaultCertificateRequest() {
        var builder = VaultCertificateRequest.builder()
                .ttl(ttl)
                .format(CERTIFICATE_FORMAT)
                .privateKeyFormat(PKCS8_FORMAT);

        if (commonName != null) {
            builder.commonName(commonName);
        }
        if (altNames != null) {
            builder.altNames(altNames);
        }
        if (ipSubjectAltNames != null) {
            builder.ipSubjectAltNames(ipSubjectAltNames);
        }
        return builder.build();
    }
}
