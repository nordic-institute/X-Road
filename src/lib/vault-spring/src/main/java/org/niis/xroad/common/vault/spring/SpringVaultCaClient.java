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
package org.niis.xroad.common.vault.spring;

import ee.ria.xroad.common.util.CryptoUtils;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.vault.VaultCaClient;
import org.niis.xroad.common.vault.config.CertificateProvisioningProperties;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Optional;

import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;

@RequiredArgsConstructor
public class SpringVaultCaClient implements VaultCaClient {
    private final VaultTemplate vaultTemplate;
    private final CertificateProvisioningProperties properties;

    @Override
    public Optional<X509Certificate> readCaCertificate() {
        String pem = readCaCertificatePem();
        try {
            return Optional.of(CryptoUtils.readCertificate(pem.getBytes(StandardCharsets.UTF_8)));
        } catch (XrdRuntimeException e) {
            throw XrdRuntimeException.systemException(INTERNAL_ERROR, e, CA_READ_FAILED);
        }
    }

    private String readCaCertificatePem() {
        VaultResponse response;
        try {
            response = vaultTemplate.read(properties.secretStorePkiPath() + "/cert/ca");
        } catch (RuntimeException e) {
            throw XrdRuntimeException.systemInternalError(CA_READ_FAILED, e);
        }
        Object certificate = response == null || response.getData() == null ? null : response.getData().get("certificate");
        if (certificate instanceof String pem && !pem.isBlank()) {
            return pem;
        }
        throw XrdRuntimeException.systemInternalError(CA_READ_FAILED);
    }
}
