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

import com.codeborne.selenide.CollectionCondition;
import io.cucumber.java.en.Step;
import org.niis.xroad.ss.test.ui.glue.mappers.ParameterMappers;
import org.niis.xroad.ss.test.ui.page.SystemParametersPageObj;

import static com.codeborne.selenide.Condition.disabled;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.visible;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.vTextField;

public class SystemParametersStepDefs extends BaseUiStepDefs {
    private final SystemParametersPageObj systemParametersPageObj = new SystemParametersPageObj();

    @Step("Configuration Anchor buttons are {selenideValidation}")
    public void validateSettings(ParameterMappers.SelenideValidation validation) {
        systemParametersPageObj.configurationAnchor.btnDownload().shouldBe(validation.getSelenideCondition());
        systemParametersPageObj.configurationAnchor.btnUpload().shouldBe(validation.getSelenideCondition());
    }

    @Step("Add Timestamping services dialog is opened")
    public void openTimestampingDialog() {
        systemParametersPageObj.btnAddTimestampingService().shouldBe(visible).click();
        systemParametersPageObj.dialogAddTimestampingService.radioGroupTimestampingServices().shouldBe(visible);
        systemParametersPageObj.dialogAddTimestampingService.btnAdd().shouldBe(disabled);
    }

    @Step("Add Timestamping services dialog is closed")
    public void closeTimestampingDialog() {
        systemParametersPageObj.dialogAddTimestampingService.radioGroupTimestampingServices().shouldBe(visible);
        systemParametersPageObj.dialogAddTimestampingService.btnCancel().click();
        systemParametersPageObj.dialogAddTimestampingService.radioGroupTimestampingServices().shouldNotBe(visible);
    }

    @Step("First timestamping option is selected")
    public void selectTimestampingOption() {
        systemParametersPageObj.dialogAddTimestampingService.radioGroupTimestampingServicesSelection(0).click();
        systemParametersPageObj.dialogAddTimestampingService.btnAdd().shouldBe(enabled).click();
    }

    @Step("Timestamping services table has {} entries")
    public void validateTimestampingTable(int size) {
        systemParametersPageObj.tableTimestampingServices().shouldBe(visible);
        systemParametersPageObj.tableTimestampingServicesRows().shouldBe(CollectionCondition.size(size));
    }

    @Step("Timestamping services table row {} has service {string} and url {string}")
    public void validateTimestampingRow(int row, String service, String url) {
        systemParametersPageObj.tableTimestampingServiceNameByRow(row, service).shouldBe(visible);
        systemParametersPageObj.tableTimestampingServiceUrlByRow(row, url).shouldBe(visible);
    }

    @Step("Timestamping service on row {} is deleted")
    public void deleteTimestampingRow(int index) {
        systemParametersPageObj.btnDeleteTimestampingServicesByRow(index).click();
        commonPageObj.dialog.btnSave().click();
    }

    @Step("Security Server address is displayed")
    public void securityServerAddressIsDisplayed() {
        systemParametersPageObj.tableServerAddress().shouldBe(visible);
    }

    @Step("Security Server address edit button is enabled")
    public void securityServerAddressEditButtonIsEnabled() {
        systemParametersPageObj.btnEditServerAddress().shouldBe(enabled);
    }

    @Step("Security Server address edit button is clicked")
    public void securityServerAddressEditButtonIsClicked() {
        systemParametersPageObj.btnEditServerAddress().click();
    }

    @Step("new Security Server address {string} is submitted")
    public void newSecurityServerAddressIsSubmitted(String address) {
        commonPageObj.dialog.btnSave().shouldBe(disabled);

        vTextField(systemParametersPageObj.dialogEditServerAddress.addressField())
                .setValue(address);

        commonPageObj.dialog.btnSave().shouldBe(enabled);
        commonPageObj.dialog.btnSave().click();
    }

}
