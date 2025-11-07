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
package org.niis.xroad.ss.test.ui.glue;

import com.codeborne.selenide.Condition;
import io.cucumber.java.en.Step;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.util.io.pem.PemReader;
import org.niis.xroad.ss.test.ui.glue.mappers.ParameterMappers;
import org.niis.xroad.ss.test.ui.page.TlsKeyPageObj;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import static com.codeborne.selenide.Condition.disabled;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.vTextField;

public class TlsKeyStepDefs extends BaseUiStepDefs {

    private static final String TLS_CERTIFICATE_HASH = "TLS_CERTIFICATE_HASH";

    private static final Set<String> EXPECTED_TAR_ENTRIES = Set.of("./cert.cer", "./cert.pem");
    private static final String EXPECTED_TAR_NAME = "certs.tar.gz";

    private final TlsKeyPageObj tlsKeyPageObj = new TlsKeyPageObj();

    @Step("Generate TLS key button is {selenideValidation}")
    public void validateGenerateTlsKeyButton(ParameterMappers.SelenideValidation validation) {
        tlsKeyPageObj.buttonGenerateKey().shouldBe(validation.getSelenideCondition());
    }

    @Step("Export TLS certificate button is {selenideValidation}")
    public void validateExportTlsCertButton(ParameterMappers.SelenideValidation validation) {
        tlsKeyPageObj.buttonDownloadCert()
                .shouldBe(validation.getSelenideCondition());
    }

    @Step("TLS certificate is exported")
    public void tlsCertificateIsExported() {
        var cert = tlsKeyPageObj.buttonDownloadCert().download();
        putStepData(StepDataKey.DOWNLOADED_FILE, cert);
    }

    @Step("TLS certificate is successfully downloaded and contains expected contents")
    public void tlsCertificateIsSuccessfullyDownloaded() throws IOException, CertificateException {
        Optional<File> fileOpt = getStepData(StepDataKey.DOWNLOADED_FILE);
        File file = fileOpt.orElseThrow();
        assertThat(file)
                .exists()
                .isFile()
                .isNotEmpty()
                .hasName(EXPECTED_TAR_NAME);

        var certs = extractCerts(file);
        assertThat(certs.keySet()).isEqualTo(EXPECTED_TAR_ENTRIES);
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        for (String name : certs.keySet()) {
            try (InputStream in = new ByteArrayInputStream(certs.get(name))) {
                Certificate cert = certFactory.generateCertificate(in);
                assertThat(cert).isNotNull();
            }
        }
    }

    @Step("Generate key button is clicked")
    public void clickGenerateKeyButton() {
        tlsKeyPageObj.buttonGenerateKey().click();
    }

    @Step("New TLS key and certificate generation is confirmed")
    public void newTlsKeyAndCertificateGenerationIsConfirmed() {
        var tlsCertHash = tlsKeyPageObj.internalTlsCertificate().text();
        scenarioContext.putStepData(TLS_CERTIFICATE_HASH, tlsCertHash);
        tlsKeyPageObj.generateTlsKeyDialog.btnConfirm().click();
    }

    @Step("New TLS key and certificate are successfully generated")
    public void newTlsKeyAndCertificateAreSuccessfullyGenerated() {
        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();

        var previousCertificateHash = scenarioContext.getStepData(TLS_CERTIFICATE_HASH);
        tlsKeyPageObj.internalTlsCertificate().shouldNotHave(text(previousCertificateHash.toString()));
    }

    @Step("TLS CSR generation view is opened")
    public void tlsCsrGenerationViewIsOpened() {
        tlsKeyPageObj.buttonGenerateCsr().click();
    }

    @Step("Distinguished name {} is entered")
    public void distinguishedNameIsEntered(String distinguishedName) {
        vTextField(tlsKeyPageObj.generateTlsCsrView.distinguishedNameInput()).setValue(distinguishedName);
    }

    @Step("Generate CSR button is clicked")
    public void clickGenerateCsrButton() {
        var csr = tlsKeyPageObj.generateTlsCsrView.btnGenerateCsr().download();
        putStepData(StepDataKey.DOWNLOADED_FILE, csr);
    }

    @Step("TLS CSR is successfully downloaded and contains expected contents")
    public void tlsCsrIsSuccessfullyDownloaded() throws IOException {
        Optional<File> fileOpt = getStepData(StepDataKey.DOWNLOADED_FILE);
        File file = fileOpt.orElseThrow();
        assertThat(file)
                .exists()
                .isFile()
                .isNotEmpty()
                .hasExtension("p10");

        try (PemReader pemReader = new PemReader(new FileReader(file))) {
            byte[] csrBytes = pemReader.readPemObject().getContent();
            var csr = new PKCS10CertificationRequest(csrBytes);
            assertThat(csr).isNotNull();
        }
    }

    @Step("Generated TLS certificate is successfully imported")
    public void importTlsCertificate() {
        tlsKeyPageObj.btnUploadCertificate().shouldBe(enabled, visible).click();
        commonPageObj.dialog.btnCancel().shouldBe(enabled);
        commonPageObj.dialog.btnSave().shouldBe(disabled);

        Optional<File> cert = getStepData(StepDataKey.CERT_FILE);
        tlsKeyPageObj.inputTlsCertificateFile().uploadFile(cert.orElseThrow());

        commonPageObj.dialog.btnSave().shouldBe(enabled).click();

        commonPageObj.snackBar.success().shouldBe(Condition.visible);
        commonPageObj.snackBar.btnClose().click();
    }

    private Map<String, byte[]> extractCerts(File certsTar) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        try (var tis = new TarArchiveInputStream(new GZIPInputStream(new FileInputStream(certsTar)))) {
            TarArchiveEntry entry;
            while ((entry = tis.getNextTarEntry()) != null) {
                files.put(entry.getName(), tis.readAllBytes());
            }
        }
        return files;
    }
}
