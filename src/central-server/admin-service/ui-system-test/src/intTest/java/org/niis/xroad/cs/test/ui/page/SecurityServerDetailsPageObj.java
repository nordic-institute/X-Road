/*
 * The MIT License
 * <p>
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

package org.niis.xroad.cs.test.ui.page;

import com.codeborne.selenide.SelenideElement;
import org.niis.xroad.common.test.ui.page.component.Dialog;

import static com.codeborne.selenide.Selenide.$x;

@SuppressWarnings("InnerClassMayBeStatic")
public class SecurityServerDetailsPageObj {

    private final EditServerAddressDialog editServerAddressDialog = new EditServerAddressDialog();
    private final DeleteSecurityServerDialog deleteSecurityServerDialog = new DeleteSecurityServerDialog();

    public SelenideElement ownerName() {
        var xpath = "//tr[@data-test='security-server-owner-name']/td[@data-test='row-value']";
        return $x(xpath);
    }

    public SelenideElement ownerClass() {
        var xpath = "//tr[@data-test='security-server-owner-class']/td[@data-test='row-value']";
        return $x(xpath);
    }

    public SelenideElement ownerCode() {
        var xpath = "//tr[@data-test='security-server-owner-code']/td[@data-test='row-value']";
        return $x(xpath);
    }

    public SelenideElement serverCode() {
        var xpath = "//tr[@data-test='security-server-server-code']/td[@data-test='row-value']";
        return $x(xpath);
    }

    public SelenideElement serverAddress() {
        var xpath = "//tr[@data-test='security-server-address']/td[@data-test='row-value']";
        return $x(xpath);
    }

    public SelenideElement serverRegistered() {
        var xpath = "//tr[@data-test='security-server-registered']/td[@data-test='row-value']/span";
        return $x(xpath);
    }

    public SelenideElement btnChangeAddress() {
        var xpath = "//tr[@data-test='security-server-address']//button[.//span/text()='Edit']";
        return $x(xpath);
    }

    public SelenideElement btnDeleteSecurityServer() {
        var xpath = "//button[@data-test='btn-delete-security-server']";
        return $x(xpath);
    }

    public EditServerAddressDialog editAddressDialog() {
        return editServerAddressDialog;
    }

    public DeleteSecurityServerDialog deleteSecurityServerDialog() {
        return deleteSecurityServerDialog;
    }

    public class EditServerAddressDialog extends Dialog {

        public SelenideElement inputAddress() {
            var xpath = "//div[@data-test='security-server-address-edit-field']";
            return $x(xpath);
        }
    }

    public class DeleteSecurityServerDialog extends Dialog {

        public SelenideElement inputSeverCode() {
            var xpath = "//div[@data-test='verify-server-code']";
            return $x(xpath);
        }
    }
}
