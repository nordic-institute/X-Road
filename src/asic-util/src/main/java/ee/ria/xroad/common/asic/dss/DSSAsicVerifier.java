/*
 * The MIT License
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
package ee.ria.xroad.common.asic.dss;

import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.util.CryptoUtils;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.TokenExtractionStrategy;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.FileDocument;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource;
import eu.europa.esig.dss.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.validation.executor.ValidationLevel;
import eu.europa.esig.dss.validation.reports.Reports;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.security.cert.CertificateEncodingException;

@Slf4j
public class DSSAsicVerifier {

    public Reports validate(String filePath, GlobalConfProvider globalConfProvider) {
        var document = new FileDocument(filePath);
        SignedDocumentValidator validator = getValidator(document, globalConfProvider);
        var reports = validator.validateDocument();
        reports.setValidateXml(true);
        return reports;
    }

    private SignedDocumentValidator getValidator(DSSDocument signedDocument, GlobalConfProvider globalConfProvider) {
        SignedDocumentValidator validator = SignedDocumentValidator.fromDocument(signedDocument);
        validator.setTokenExtractionStrategy(TokenExtractionStrategy.EXTRACT_ALL);
        validator.setEnableEtsiValidationReport(false);

        var certVerifier = new CommonCertificateVerifier();
        certVerifier.setDefaultDigestAlgorithm(DigestAlgorithm.forJavaName(CryptoUtils.DEFAULT_CERT_HASH_ALGORITHM_ID.name()));
        validator.setCertificateVerifier(certVerifier);
        validator.setValidationLevel(ValidationLevel.ARCHIVAL_DATA);

        certVerifier.setTrustedCertSources(loadGlobalConfData(globalConfProvider));
        certVerifier.setAIASource(null);

        return validator;
    }

    @SneakyThrows //TODO handle exception
    private CommonTrustedCertificateSource loadGlobalConfData(GlobalConfProvider globalConfProvider) {
        CommonTrustedCertificateSource trustedCertificateSource = new CommonTrustedCertificateSource();


        globalConfProvider.getTspCertificates().forEach(cert -> {
            try {
                var certToken = DSSUtils.loadCertificate(cert.getEncoded());
                trustedCertificateSource.addCertificate(certToken);
                log.debug("Loaded TSP cert: {}", cert.getSubjectDN());
            } catch (CertificateEncodingException e) {
                throw new RuntimeException(e); //TODO
            }

        });
        globalConfProvider.getAllCaCerts().forEach(cert -> {
            try {
                var certToken = DSSUtils.loadCertificate(cert.getEncoded());
                trustedCertificateSource.addCertificate(certToken);
                log.debug("Loaded CA cert: {}", cert.getSubjectDN());
            } catch (CertificateEncodingException e) {
                throw new RuntimeException(e); //TODO
            }

        });

        globalConfProvider.getOcspResponderCertificates().forEach(cert -> {
            try {
                var certToken = DSSUtils.loadCertificate(cert.getEncoded());
                trustedCertificateSource.addCertificate(certToken);
                log.debug("Loaded OCSP cert: {}", cert.getSubjectDN());
            } catch (CertificateEncodingException e) {
                throw new RuntimeException(e); //TODO
            }
        });


        return trustedCertificateSource;
    }
}
