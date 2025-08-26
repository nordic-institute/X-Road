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
package org.niis.xroad.common.tls.quarkus.vault;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.util.CryptoUtils;

import io.quarkus.vault.VaultPKISecretEngine;
import io.quarkus.vault.VaultPKISecretEngineFactory;
import io.quarkus.vault.pki.DataFormat;
import io.quarkus.vault.pki.GenerateCertificateOptions;
import io.quarkus.vault.pki.PrivateKeyEncoding;
import org.niis.xroad.common.tls.vault.VaultKeyClient;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;

public class QuarkusVaultKeyClient implements VaultKeyClient {
    private final VaultPKISecretEngine pkiSecretEngine;
    private final Duration ttl;
    private final String issuanceRoleName;
    private final String commonName;
    private final List<String> altNames;
    private final List<String> ipSubjectAltNames;

    public QuarkusVaultKeyClient(VaultPKISecretEngineFactory pkiSecretEngineFactory, String secretStorePkiPath, Duration ttl,
                                 String issuanceRoleName, String commonName, List<String> altNames, List<String> ipSubjectAltNames) {
        this.pkiSecretEngine = pkiSecretEngineFactory.engine(secretStorePkiPath);
        this.ttl = ttl;
        this.issuanceRoleName = issuanceRoleName;
        this.commonName = commonName;
        this.altNames = altNames;
        this.ipSubjectAltNames = ipSubjectAltNames;
    }

    @Override
    public VaultKeyData provisionNewCerts() throws Exception {
        if (pkiSecretEngine == null) {
            throw new IllegalStateException("Vault PKI Secret Engine is not initialized. Check configuration.");
        }

        var request = buildVaultCertificateRequest();

        var vaultResponse = pkiSecretEngine.generateCertificate(issuanceRoleName, request);

        if (vaultResponse == null) {
            throw new CodedException(X_INTERNAL_ERROR, "Failed to get certificate from Vault. Response is null.");
        }

        if (vaultResponse.privateKey.getData() instanceof String privateKeyData) {
            var cert = vaultResponse.certificate.getCertificate();
            var privateKey = CryptoUtils.getPrivateKey(new ByteArrayInputStream(privateKeyData.getBytes(StandardCharsets.UTF_8)));
            var certTrustChain = vaultResponse.issuingCA.getCertificate();
            return new VaultKeyData(new X509Certificate[]{cert},
                    privateKey,
                    new X509Certificate[]{certTrustChain});
        } else {
            throw new CodedException(X_INTERNAL_ERROR, "Failed to get certificate from Vault. Data is not readable. Null? "
                    + (vaultResponse.privateKey.getData() == null));
        }
    }

    private GenerateCertificateOptions buildVaultCertificateRequest() {
        var request = new GenerateCertificateOptions();
        request.setTimeToLive("%ds".formatted(ttl.toSeconds()));
        request.setFormat(DataFormat.valueOf(CERTIFICATE_FORMAT.toUpperCase()));
        request.setPrivateKeyEncoding(PrivateKeyEncoding.valueOf(PKCS8_FORMAT.toUpperCase()));

        if (commonName != null) {
            request.setSubjectCommonName(commonName);
        }
        if (altNames != null) {
            request.setSubjectAlternativeNames(altNames);
        }
        if (ipSubjectAltNames != null) {
            request.setIpSubjectAlternativeNames(ipSubjectAltNames);
        }
        return request;
    }
}
