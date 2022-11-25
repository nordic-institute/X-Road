/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.niis.xroad.test.ui.cs.glue;

import com.codeborne.selenide.Condition;
import io.cucumber.java.en.Step;
import org.niis.xroad.test.ui.cs.page.IntermediateCasPageObj;
import org.niis.xroad.test.ui.cs.page.TrustServicesPageObj;
import org.niis.xroad.test.ui.glue.BaseUiStepDefs;

import static com.codeborne.selenide.Condition.visible;
import static org.niis.xroad.test.ui.utils.CertificateUtils.generateAuthCert;
import static org.niis.xroad.test.ui.utils.CertificateUtils.getAsFile;

public class TrustServicesIntermediateCasStepDefs extends BaseUiStepDefs {
    private final TrustServicesPageObj trustServicesPageObj = new TrustServicesPageObj();
    private final IntermediateCasPageObj intermediateCasPageObj = new IntermediateCasPageObj();

    @Step("Intermediate CAs tab is selected")
    public void intermediateCasTabIsSelected() {
        trustServicesPageObj.certServiceDetails.tabIntermediateCas().click();
    }

    @Step("Intermediate CA with name {} is added")
    public void newIntermediateCaIsAdded(String name) throws Exception {
        intermediateCasPageObj.btnAdd().click();
        commonPageObj.dialog.btnCancel().should(Condition.enabled);
        commonPageObj.dialog.btnSave().shouldNotBe(Condition.enabled);

        final byte[] certificate = generateAuthCert(name);

        intermediateCasPageObj.inputAddCertFile().uploadFile(getAsFile(certificate));
        commonPageObj.dialog.btnSave().click();


        commonPageObj.snackBar.success().shouldBe(visible);
        commonPageObj.snackBar.btnClose().click();
    }

    @Step("User opens intermediate CA with name {} details")
    public void userOpensIntermediateCaDetails(String name) {
        intermediateCasPageObj.tableRowOf(name).click();
    }
}
