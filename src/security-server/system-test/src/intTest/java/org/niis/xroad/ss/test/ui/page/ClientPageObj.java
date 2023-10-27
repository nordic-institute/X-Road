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
package org.niis.xroad.ss.test.ui.page;

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Selenide.$$x;
import static com.codeborne.selenide.Selenide.$x;
import static java.lang.String.format;

public class ClientPageObj {
    public final Subsystem subsystem = new Subsystem();
    public final AddClientDetails addClientDetails = new AddClientDetails();
    public final AddClientToken addClientToken = new AddClientToken();
    public final AddClientSignKey addClientSignKey = new AddClientSignKey();
    public final AddClientCsrDetails addClientCsrDetails = new AddClientCsrDetails();
    public final AddClientGenerateCsr addClientGenerateCsr = new AddClientGenerateCsr();
    public final AddClientFinish addClientFinish = new AddClientFinish();

    public SelenideElement btnSearch() {
        return $x("//*[contains(@class, 'mdi-magnify')]");
    }

    public SelenideElement inputSearch() {
        return $x("//*[@data-test='search-input']");
    }

    public SelenideElement btnAddClient() {
        return $x("//button[@data-test='add-client-button']");
    }

    public SelenideElement linkClientDetailsOfName(String name) {
        return $x(format("//tbody//span[contains(text(),'%s')]", name));
    }

    public SelenideElement tableRowWithNameAndStatus(String name, String status) {
        return $x(format("//tbody/tr[ td[1]/span[text()='%s'] and td[3]//*[text()='%s'] ]", name, status));
    }

    public SelenideElement groupByPos(int pos) {
        return $x(format("//tbody//tr[%d]//td[1]/span", pos));
    }

    public ElementsCollection groups() {
        return $$x("//tbody//tr//td[1]/span");
    }

    public SelenideElement tableHeader(String name) {
        return $x(format("//thead//span[text()='%s']", name));
    }

    public SelenideElement btnAddSubsystem(String name) {
        return $x(format("//tr[contains(., '%s')]//button[contains(., 'Add subsystem')]", name));
    }

    public static class Subsystem {
        public SelenideElement btnSelect() {
            return $x("//button[@data-test='select-subsystem-button']");
        }

        public SelenideElement btnSelectDialogSave() {
            return $x("//button[@data-test='select-client-save-button']");
        }

        public SelenideElement radioSubsystemById(String id) {
            return $x(format("//tbody//tr[td[3][contains(text(),'%s')] ]//div[@class ='v-selection-control__input']", id));
        }

        public SelenideElement memberNameValue() {
            return $x("//div[@data-test='selected-member-name']");
        }

        public SelenideElement memberClassValue() {
            return $x("//div[@data-test='selected-member-class']");
        }

        public SelenideElement memberCodeValue() {
            return $x("//div[@data-test='selected-member-code']");
        }

        public SelenideElement inputSubsystem() {
            return $x("//div[@data-test='subsystem-code-input']");
        }

        public SelenideElement inputRegisterSubsystem() {
            return $x("//div[@data-test='register-subsystem-checkbox']");
        }

        public SelenideElement btnSubmit() {
            return $x("//button[@data-test='submit-add-subsystem-button']");
        }

        public SelenideElement btnCancel() {
            return $x("//button[@data-test='cancel-button']");
        }

    }

    public static class AddClientDetails {
        public SelenideElement btnSelectClient() {
            return $x("//button[@data-test='select-client-button']");
        }

        public SelenideElement radioClientById(String id) {
            return $x(format("//tbody//tr[td[3][contains(text(),'%s')] ]//div[@class='v-selection-control__input']", id));
        }

        public SelenideElement btnAddSelected() {
            return $x("//button[@data-test='select-client-save-button']");
        }

        public SelenideElement selectMemberClass() {
            return $x("//div[@data-test='member-class-input']//div[@class='v-select__selection']");
        }

        public SelenideElement inputMemberCode() {
            return $x("//div[@data-test='member-code-input']");
        }

        public SelenideElement inputSubsystemCode() {
            return $x("//div[@data-test='subsystem-code-input']");
        }

        public SelenideElement btnNext() {
            return $x("//button[@data-test='next-button']");
        }

    }


    public static class AddClientToken {

        public SelenideElement radioByTokenName(String name) {
            return $x(format("//div[.//label[text()='Token softToken-0'] and @data-test='token-radio-button']", name));
        }

        public SelenideElement btnNext() {
            return $x("(//button[@data-test='next-button'])[2]");
        }

        public SelenideElement cancelButton() {
            return $x("(//button[@data-test='cancel-button'])[2]");
        }
    }

    public static class AddClientSignKey {
        public SelenideElement inputLabel() {
            return $x("//div[@data-test='key-label-input']");
        }

        public SelenideElement btnNext() {
            return $x("(//button[@data-test='next-button'])[3]");
        }
    }

    public static class AddClientCsrDetails {
        public SelenideElement csrService() {
            return $x("//div[@data-test='csr-certification-service-select']");
        }


        public SelenideElement csrFormat() {
            return $x("//div[@data-test='csr-format-select']");
        }

        public SelenideElement btnNext() {
            return $x("(//button[@data-test='save-button'])[1]");
        }
    }

    public static class AddClientGenerateCsr {
        public SelenideElement inputOrganizationName() {
            return $x("//div[@data-test='dynamic-csr-input_O']");
        }

        public SelenideElement btnNext() {
            return $x("(//button[@data-test='save-button'])[2]");
        }
    }

    public static class AddClientFinish {
        public SelenideElement submitButton() {
            return $x("//button[@data-test='submit-button']");
        }
    }
}
