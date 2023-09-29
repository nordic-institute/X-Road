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

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Selenide.$$x;
import static com.codeborne.selenide.Selenide.$x;

@SuppressWarnings("InnerClassMayBeStatic")
public class SecurityServerAuthCertificatesPageObj {

    private final DeleteDialog deleteDialog = new DeleteDialog();

    public SelenideElement listRowOf(String certAuthorityName, String serialNumber, String subject) {
        var xpath = "//main[@data-test='security-server-authentication-certificates-view']//table/tbody/tr["
                + "contains(./td[1]/div/text(), '%s') "
                + "and ./td[2]/text()='%s' "
                + "and ./td[3]/text()='%s']";

        return $x(String.format(xpath, certAuthorityName, serialNumber, subject));
    }

    public SelenideElement linkForCaDetails(String certAuthorityName) {
        var xpath = "//main[@data-test='security-server-authentication-certificates-view']"
                + "//table/tbody/tr/td/div[(normalize-space(text()) = '%s')]";

        return $x(String.format(xpath, certAuthorityName.trim()));
    }

    public ElementsCollection authCertificateRows() {
        var xpath = "//main[@data-test='security-server-authentication-certificates-view']//table/tbody/tr";

        return $$x(xpath);
    }

    public SelenideElement certificatedDetailsView() {
        var xpath = "//main[@id='security-server-authentication-certificate']";

        return $x(xpath).find("div.certificate-details-wrapper");
    }

    public ElementsCollection columnHeaders() {
        var xpath = "//main[@data-test='security-server-authentication-certificates-view']//thead/tr/th/div/span";
        return $$x(xpath);
    }

    public SelenideElement columnHeader(int idx) {
        var xpath = "//main[@data-test='security-server-authentication-certificates-view']//thead/tr/th[%d]";
        return $x(String.format(xpath, idx));
    }

    public ElementsCollection columnValues(int idx) {
        var xpath = "//main[@data-test='security-server-authentication-certificates-view']//tbody";
        return $x(xpath).findAll(String.format("tr>td:nth-child(%d)", idx));
    }

    public SelenideElement deleteAuthenticationCertButton(int rowIdx) {
        var xpath = "//main[@data-test='security-server-authentication-certificates-view']"
                + "//table/tbody/tr[%d]//button[@data-test='delete-AC-button']";

        return $x(String.format(xpath, rowIdx));
    }

    public DeleteDialog getDeleteDialog() {
        return deleteDialog;
    }

    public class DeleteDialog {
        public SelenideElement deleteButton() {
            var xpath = "//button[@data-test='dialog-save-button']";

            return $x(xpath);
        }

        public SelenideElement cancelButton() {
            var xpath = "//button[@data-test='dialog-cancel-button']";

            return $x(xpath);
        }

        public SelenideElement inputSeverCode() {
            var xpath = "//div[@data-test='verify-server-code']";
            return $x(xpath);
        }
    }
}
