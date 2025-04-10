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
package org.niis.xroad.common.rpc.quarkus;

import ee.ria.xroad.common.CodedException;

import io.grpc.util.CertificateUtils;
import io.quarkus.vault.VaultPKISecretEngine;
import io.quarkus.vault.VaultPKISecretEngineFactory;
import io.quarkus.vault.pki.DataFormat;
import io.quarkus.vault.pki.GenerateCertificateOptions;
import io.quarkus.vault.pki.PrivateKeyEncoding;
import org.niis.xroad.common.properties.CommonRpcProperties;
import org.niis.xroad.common.rpc.vault.VaultKeyClient;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;

public class QuarkusVaultKeyClient implements VaultKeyClient {
    private final CommonRpcProperties.CertificateProvisionProperties certificateProvisionProperties;
    private final VaultPKISecretEngine pkiSecretEngine;

    public QuarkusVaultKeyClient(CommonRpcProperties.CertificateProvisionProperties certificateProvisionProperties,
                                 VaultPKISecretEngineFactory pkiSecretEngineFactory) {
        this.certificateProvisionProperties = certificateProvisionProperties;
        this.pkiSecretEngine = pkiSecretEngineFactory.engine(certificateProvisionProperties.secretStorePkiPath());

    }

    @Override
    public VaultKeyData provisionNewCerts() throws Exception {
        if (pkiSecretEngine == null) {
            throw new IllegalStateException("Vault PKI Secret Engine is not initialized. Check configuration.");
        }

        var request = buildVaultCertificateRequest();

        var vaultResponse = pkiSecretEngine.generateCertificate(certificateProvisionProperties.issuanceRoleName(), request);

        if (vaultResponse == null) {
            throw new CodedException(X_INTERNAL_ERROR, "Failed to get certificate from Vault. Response is null.");
        }

        if (vaultResponse.privateKey.getData() instanceof String privateKeyData) {
            var cert = vaultResponse.certificate.getCertificate();
            var privateKey = CertificateUtils.getPrivateKey(new ByteArrayInputStream(privateKeyData.getBytes(StandardCharsets.UTF_8)));
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
        request.setTimeToLive("%dm".formatted(certificateProvisionProperties.ttlMinutes()));
        request.setFormat(DataFormat.valueOf(CERTIFICATE_FORMAT.toUpperCase()));
        request.setPrivateKeyEncoding(PrivateKeyEncoding.valueOf(PKCS8_FORMAT.toUpperCase()));

        if (certificateProvisionProperties.commonName() != null) {
            request.setSubjectCommonName(certificateProvisionProperties.commonName());
        }
        if (certificateProvisionProperties.altNames() != null) {
            request.setSubjectAlternativeNames(certificateProvisionProperties.altNames());
        }
        if (certificateProvisionProperties.ipSubjectAltNames() != null) {
            request.setIpSubjectAlternativeNames(certificateProvisionProperties.ipSubjectAltNames());
        }
        return request;
    }
}
