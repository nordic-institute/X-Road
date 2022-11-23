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

package org.niis.xroad.test.ui.glue.centralserver;

import com.codeborne.selenide.Condition;
import io.cucumber.java.en.Step;
import org.niis.xroad.test.ui.glue.BaseUiStepDefs;
import org.openqa.selenium.By;

import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static java.lang.String.format;
import static org.niis.xroad.test.ui.glue.constants.Constants.BTN_CLOSE_SNACKBAR;
import static org.niis.xroad.test.ui.glue.constants.Constants.BTN_DIALOG_CANCEL;
import static org.niis.xroad.test.ui.glue.constants.Constants.BTN_DIALOG_SAVE;
import static org.niis.xroad.test.ui.glue.constants.Constants.INPUT_FILE_UPLOAD;
import static org.niis.xroad.test.ui.glue.constants.Constants.SNACKBAR_SUCCESS;
import static org.niis.xroad.test.ui.utils.CertificateUtils.generateAuthCert;
import static org.niis.xroad.test.ui.utils.CertificateUtils.getAsFile;
import static org.openqa.selenium.By.xpath;

public class CentralServerIntermediateCasStepDefs extends BaseUiStepDefs {

    private static final By TAB_INTERMEDIATE_CAS =
            xpath("//a[contains(text(), \"Intermediate CAs\") and contains(@class, \"v-tab\")]");
    private static final By BTN_ADD_INTERMEDIATE_CA = xpath("//button[@data-test=\"add-intermediate-ca-button\"]");
    private static final String TABLE_INTERMEDIATE_CAS = "//div[@data-test=\"intermediate-cas-table\"]//table";
    private static final String TABLE_ROW_INTERMEDIATE_CAS = TABLE_INTERMEDIATE_CAS + "/tbody/tr/td/div[contains(text(), \"%s\")]";

    @Step("Intermediate CAs tab is selected")
    public void intermediateCasTabIsSelected() {
        $(TAB_INTERMEDIATE_CAS).click();
    }

    @Step("Intermediate CA with name {} is added")
    public void newIntermediateCaIsAdded(String name) throws Exception {
        $(BTN_ADD_INTERMEDIATE_CA).click();
        $(BTN_DIALOG_CANCEL).should(Condition.enabled);
        $(BTN_DIALOG_SAVE).shouldNotBe(Condition.enabled);

        final byte[] certificate = generateAuthCert(name);

        $(INPUT_FILE_UPLOAD).uploadFile(getAsFile(certificate));
        $(BTN_DIALOG_SAVE).click();

        $(SNACKBAR_SUCCESS).shouldBe(visible);
        $(BTN_CLOSE_SNACKBAR).click();
    }

    @Step("User opens intermediate CA with name {} details")
    public void userOpensIntermediateCaDetails(String name) {
        $(xpath(format(TABLE_ROW_INTERMEDIATE_CAS, name))).click();
    }
}
