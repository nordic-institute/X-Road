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
package org.niis.xroad.ss.test.ui.glue;

import ee.ria.xroad.common.util.CryptoUtils;

import io.cucumber.java.en.Step;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.ss.test.ui.container.EnvSetup;
import org.niis.xroad.ss.test.ui.container.service.TestTokenService;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;
import java.nio.file.Paths;

@Slf4j
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class SignerStepDefs extends BaseUiStepDefs {

    private static final String KEYSTORE_BASE_PATH = "src/intTest/resources/files/keystores";
    private static final String CERTS_PATH = KEYSTORE_BASE_PATH + "/certs";

    @Autowired
    private TestTokenService testTokenService;

    @SneakyThrows
    @SuppressWarnings("checkstyle:MagicNumber")
    @Step("signer service is restarted")
    public void signerServiceIsRestarted() {
        envSetup.restartContainer(EnvSetup.SIGNER);
    }

    @Step("HSM tokens are deleted")
    public void deleteHsmTokens() {
        var count = testTokenService.deleteHsmTokens();

        testReportService.attachText("Deleted HSM tokens count", String.valueOf(count));
    }

    @Step("All Signer keys are deleted")
    public void deleteAllSignerKeys() {
        var count = testTokenService.deleteAllKeys();

        testReportService.attachText("Deleted keys count", String.valueOf(count));
    }

    @Step("softtoken device is created with id {string} and friendly name {string}")
    public void createSoftTokenDevice(String id, String friendlyName) {
        testTokenService.createSoftTokenDevice(id, friendlyName);
    }

    @SneakyThrows
    @Step("authentication key {string} named {string} is added to softtoken")
    public void addAuthKey(String externalId, String friendlyName) {
        byte[] keystore = Files.readAllBytes(Paths.get(KEYSTORE_BASE_PATH, externalId + ".p12"));
        testTokenService.addSoftwareKey(externalId, friendlyName, "AUTHENTICATION", keystore);
    }

    @SneakyThrows
    @Step("signing key {string} named {string} is added to softtoken")
    public void addSigningKey(String externalId, String friendlyName) {
        byte[] keystore = Files.readAllBytes(Paths.get(KEYSTORE_BASE_PATH, externalId + ".p12"));
        testTokenService.addSoftwareKey(externalId, externalId, "SIGNING", keystore);
    }

    @SneakyThrows
    @Step("authentication certificate {string} is added for key {string}")
    public void addAuthCertificate(String certExternalId, String keyExternalId) {
        addCertificate(certExternalId, null, keyExternalId);
    }

    @SneakyThrows
    @Step("signing certificate {string} is added for member {string} under key {string}")
    public void addCertificate(String certExternalId, String memberId, String keyExternalId) {
        var cert = CryptoUtils.readCertificate(Files.newInputStream(Paths.get(CERTS_PATH, certExternalId + ".pem")));
        testTokenService.addCertificate(certExternalId, keyExternalId, cert.getEncoded(), memberId);
    }

    @Step("Predefined inactive signer token is inserted")
    public void addInactiveSignerToken() {
        testTokenService.addInactiveSignerToken();
        envSetup.restartContainer(EnvSetup.SIGNER);
    }
}
