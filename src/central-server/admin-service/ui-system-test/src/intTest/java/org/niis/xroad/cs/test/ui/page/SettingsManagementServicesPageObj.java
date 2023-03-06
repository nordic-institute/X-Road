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
package org.niis.xroad.cs.test.ui.page;

import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Selenide.$x;

@SuppressWarnings("InnerClassMayBeStatic")
public class SettingsManagementServicesPageObj {

    public final EditManagementSubsystemDialog editManagementSubsystemDialog = new EditManagementSubsystemDialog();

    public SelenideElement serviceProviderIdentifier() {
        return $x("//td[@data-test='management-service-provider-identifier-field']");
    }

    public SelenideElement serviceProviderName() {
        return $x("//td[@data-test='management-service-provider-name-field']");
    }

    public SelenideElement securityServer() {
        return $x("//td[@data-test='management-security-server-field']");
    }

    public SelenideElement wsdlAddress() {
        return $x("//td[@data-test='management-wsdl-address-field']");
    }

    public SelenideElement centralServerAddress() {
        return $x("//td[@data-test='management-central-server-address-field']");
    }

    public SelenideElement ownerGroupCode() {
        return $x("//td[@data-test='management-owner-group-code-field']");
    }

    public SelenideElement wsdlAddressCopyButton() {
        return $x("//button[@data-test='management-wsdl-address-copy-btn']");
    }

    public SelenideElement centralServerAddressCopyButton() {
        return $x("//button[@data-test='management-central-server-address-copy-btn']");
    }

    public SelenideElement editManagementSubsystemButton() {
        return $x("//button[@data-test='edit-management-subsystem']");
    }

    public class EditManagementSubsystemDialog {

        public SelenideElement search() {
            return $x("//input[@data-test='management-subsystem-search-field']");
        }

        public SelenideElement checkboxOf(String subsystem) {
            var xpath = "//div[text()='%s']/../preceding-sibling::td/div[@data-test='management-subsystem-checkbox']";
            return $x(String.format(xpath, subsystem));
        }

        public SelenideElement selectButton() {
            return $x("//button[@data-test='management-subsystem-select-button']");
        }
    }



}
