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
package org.niis.xroad.cs.admin.globalconf.generator;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.AtomicSave;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.cs.admin.api.domain.ConfigurationSigningKey;
import org.niis.xroad.cs.admin.api.facade.SignerProxyFacade;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;

import static ee.ria.xroad.common.util.CryptoUtils.getSignatureAlgorithmId;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;

@Component
@RequiredArgsConstructor
@Slf4j
public class GlobalConfTLSCertificateGenerator {

    private final SignerProxyFacade signerProxyFacade;
    private final SystemParameterService systemParameterService;

    void updateGlobalConfTLSCertificates(
            ConfigurationSigningKey internalSigningKey,
            ConfigurationSigningKey externalSigningKey) throws Exception {
        Path internalConfTLSCertPath = Path.of(SystemProperties.getConfPath() + "ssl/internal-conf.crt");
        Path externalConfTLSCertPath = Path.of(SystemProperties.getConfPath() + "ssl/external-conf.crt");
        Path centralAdminServiceCertPath = Path.of(SystemProperties.getConfPath() + "ssl/center-admin-service.crt");
        X509Certificate certToSign = readCertificate(Files.readAllBytes(centralAdminServiceCertPath));
        PublicKey publicKeyToSign = certToSign.getPublicKey();
        updateGlobalConfTLSCertIfNeeded(internalSigningKey, internalConfTLSCertPath, publicKeyToSign);
        updateGlobalConfTLSCertIfNeeded(externalSigningKey, externalConfTLSCertPath, publicKeyToSign);
    }

    private void updateGlobalConfTLSCertIfNeeded(ConfigurationSigningKey signingKey, Path confTLSCertPath,
                                                 PublicKey publicKeyToSign) throws Exception {
        X509Certificate confTLSCertificate = readCertificate(Files.readAllBytes(confTLSCertPath));
        X509Certificate signingCertificate = readCertificate(signingKey.getCert());
        try {
            confTLSCertificate.verify(signingCertificate.getPublicKey(), "BC");
            confTLSCertificate.checkValidity();
        } catch (SignatureException | CertificateExpiredException | CertificateNotYetValidException e) {
            log.info("Renewing TLS cert for {}, reason: {}", confTLSCertPath, e.getMessage());
            signAndSaveGlobalConfTLSCertificate(signingKey, confTLSCertPath.toString(), publicKeyToSign);
        }
    }

    private void signAndSaveGlobalConfTLSCertificate(ConfigurationSigningKey signingKey, String tlsCertPath,
                                                     PublicKey publicKeyToSign) throws Exception {
        String signMechanismName = signerProxyFacade.getSignMechanism(signingKey.getKeyIdentifier());
        String signatureAlgorithmId = getSignatureAlgorithmId(systemParameterService.getConfSignDigestAlgoId(),
                signMechanismName);
        final byte[] signedCertificate = signerProxyFacade.signCertificate(signingKey.getKeyIdentifier(), signatureAlgorithmId,
                "CN=" + systemParameterService.getCentralServerAddress(), publicKeyToSign);
        AtomicSave.execute(tlsCertPath, "tmp_cert",
                out -> CryptoUtils.writeCertificateChainPem(signedCertificate, out));

    }
}
