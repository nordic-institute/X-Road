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

import io.cucumber.java.en.Step;
import org.niis.xroad.ss.test.ui.page.CertificatePageObj;
import org.niis.xroad.ss.test.ui.page.ClientInfoPageObj;

import java.io.File;
import java.io.FileNotFoundException;

import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.selectorOptionOf;

public class ClientInternalServersStepDefs extends BaseUiStepDefs {
    private final CertificatePageObj certificatePageObj = new CertificatePageObj();
    private final ClientInfoPageObj clientInfoPageObj = new ClientInfoPageObj();


    @Step("Internal server connection type is set to {string}")
    public void setConnectionType(String connectionType) {
        clientInfoPageObj.internalServers.menuConnectionType().click();
        selectorOptionOf(connectionType).click();

        commonPageObj.snackBar.success().shouldBe(visible);
        commonPageObj.snackBar.btnClose().click();

        clientInfoPageObj.internalServers.menuConnectionType().shouldBe(text(connectionType));
    }

    @Step("Internal server connection type is {string}")
    public void validateConnectionType(String connectionType) {
        clientInfoPageObj.internalServers.menuConnectionType().shouldBe(text(connectionType));
    }

    @Step("Information System TLS certificate is uploaded")
    public void uploadTlsCert() {
        clientInfoPageObj.internalServers.inputTlsCertificate().uploadFromClasspath("files/cert.cer");

        clientInfoPageObj.internalServers.linkTLSCertificate()
                .shouldBe(visible);

    }
    @Step("Information System TLS certificate is deleted")
    public void deleteTlsCert() {
        clientInfoPageObj.internalServers.linkTLSCertificate()
                .shouldBe(visible)
                .click();

        certificatePageObj.btnDelete()
                .shouldBe(visible)
                .click();
        commonPageObj.dialog.btnSave().click();
    }

    @Step("Internal server certificate is exported")
    public void exportCert() throws FileNotFoundException {
        File file = clientInfoPageObj.internalServers.btnExport().download();
        assertTrue(file.exists());
    }
}
