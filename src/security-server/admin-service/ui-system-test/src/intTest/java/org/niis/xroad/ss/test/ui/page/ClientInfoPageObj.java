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

public class ClientInfoPageObj {
    public final ClientInfoNavigation navigation = new ClientInfoNavigation();
    public final Details details = new Details();
    public final LocalGroups localGroups = new LocalGroups();
    public final InternalServers internalServers = new InternalServers();
    public final Services services = new Services();

    public static class Details {
        public SelenideElement rowMemberName() {
            return $x("//tr[td[contains(text(),'Member Name')]]//td[2]");
        }

        public SelenideElement rowMemberClass() {
            return $x("//tr[td[contains(text(),'Member Class')]]//td[2]");
        }

        public SelenideElement rowMemberCode() {
            return $x("//tr[td[contains(text(),'Member Code')]]//td[2]");
        }

        public SelenideElement rowCertName() {
            return $x("//span[contains(@class,'cert-name')]");
        }

        public SelenideElement certificateByName(String name) {
            return $x(format("//table[contains(@class,'details-certificates')]//tr//span[@class ='cert-name' and text() ='%s']", name));
        }
    }

    public static class InternalServers {
        public SelenideElement btnAdd() {
            return $x("//button[.//*[contains(text(), 'Add')]]");
        }

        public SelenideElement btnExport() {
            return $x("//button[.//*[contains(text(), 'Export')]]");
        }

        public SelenideElement menuConnectionType() {
            return $x("//div[contains(@class, 'v-select__selection')]");
        }

        public SelenideElement selectDropdownOption(String option) {
            var xpath = "//div[@role='listbox']//div[@role='option' and contains(./descendant-or-self::*/text(),'%s')]";
            return $x(String.format(xpath, option));
        }

        public SelenideElement linkTLSCertificate() {
            return $x("//table[contains(@class, 'server-certificates')]//span[contains(@class, 'certificate-link')]");
        }

        public SelenideElement inputTlsCertificate() {
            return $x("//input[@type='file']");
        }
    }

    public static class ClientInfoNavigation {
        public SelenideElement detailsTab() {
            return $x("//div[contains(@class, 'v-tabs-bar__content')]"
                    + "//a[contains(@class, 'v-tab') and contains(text(), 'Details')]");
        }

        public SelenideElement serviceClientsTab() {
            return $x("//div[contains(@class, 'v-tabs-bar__content')]"
                    + "//a[contains(@class, 'v-tab') and contains(text(), 'Service clients')]");
        }

        public SelenideElement servicesTab() {
            return $x("//div[contains(@class, 'v-tabs-bar__content')]"
                    + "//a[contains(@class, 'v-tab') and contains(text(), 'Services')]");
        }

        public SelenideElement internalServersTab() {
            return $x("//div[contains(@class, 'v-tabs-bar__content')]"
                    + "//a[contains(@class, 'v-tab') and contains(text(), 'Internal servers')]");
        }

        public SelenideElement localGroupsTab() {
            return $x("//div[contains(@class, 'v-tabs-bar__content')]"
                    + "//a[contains(@class, 'v-tab') and contains(text(), 'Local groups')]");
        }
    }


    public static class LocalGroups {
        public final Details details = new Details();

        public SelenideElement inputFilter() {
            return $x("//div[contains(@class,'search-input')]//input");
        }

        public SelenideElement btnAddLocalGroup() {
            return $x("//button[@data-test='add-local-group-button']");
        }

        public SelenideElement tableHeader(String name) {
            return $x(format("//thead//th/span[text()='%s']", name));
        }

        public SelenideElement inputLocalGroupCode() {
            return $x("//input[@data-test='add-local-group-code-input']");
        }

        public SelenideElement inputLocalGroupDescription() {
            return $x("//input[@data-test='add-local-group-description-input']");
        }

        public SelenideElement groupByCode(String code) {
            return $x(format("//*[contains(@data-test, 'local-groups-table')]//*[contains(@class,'group-code') and contains(text(),'%s')]",
                    code));
        }

        public SelenideElement groupByPos(int pos) {
            return $x(format("//div[@data-test='local-groups-table']//tr[%d]//*[contains(@class,'group-code')]",
                    pos));
        }

        public ElementsCollection groups() {
            return $$x("//div[@data-test='local-groups-table']//tr//*[contains(@class,'group-code')]");
        }

        public static class Details {
            public final AddMember addMember = new AddMember();

            public SelenideElement btnAddMembers() {
                return $x("//button[@data-test='add-members-button']");
            }

            public SelenideElement btnRemoveAll() {
                return $x("//button[@data-test='remove-all-members-button']");
            }

            public SelenideElement btnDelete() {
                return $x("//button[@data-test='delete-local-group-button']");
            }

            public SelenideElement memberByCode(String code) {
                return $x(format("//table[contains(@class, 'group-members-table')]//tr[td[2][text()='%s']]",
                        code));
            }

            public SelenideElement btnRemoveMemberByCode(String code) {
                return memberByCode(code).$x(".//button[ span[text()= 'Remove']]");
            }

            public ElementsCollection btnRemove() {
                return $$x("//button[.//*[text() = 'Remove']]");
            }

            public SelenideElement inputLocalGroupDescription() {
                return $x("//input[@data-test='local-group-edit-description-input']");
            }

            public SelenideElement btnClose() {
                return $x("//button[.//*[contains(text(), 'Close')]]");
            }
        }

        public static class AddMember {
            public SelenideElement inputInstance() {
                return $x("//div[@role ='button' and div/label[text() = 'Instance']]");
            }

            public SelenideElement inputMemberCode() {
                return $x("//div[@role ='button' and div/label[text() = 'Member class']]");
            }

            public SelenideElement selectDropdownOption(String option) {
                var xpath = "//div[@role='listbox']//div[@role='option' and contains(./descendant-or-self::*/text(),'%s')]";
                return $x(String.format(xpath, option));
            }

            public SelenideElement btnSearch() {
                return $x("//div[@class = 'search-wrap']//button");
            }

            public SelenideElement btnAddSelected() {
                return $x("//button[span[text()='Add selected']]");
            }

            public SelenideElement checkboxSelectMember(String member) {
                return $x(format("//table[contains(@class,'members-table')]//tr[ td[3][text()='%s']]//td[1]", member));
            }
        }
    }

    public static class Services {
        public SelenideElement messageServiceURLBoxError() {
            return $x("//div[contains(@class, 'v-messages__message')]");
        }

        public SelenideElement btnAddWSDL() {
            return $x("//button[@data-test='add-wsdl-button']");
        }

        public SelenideElement btnAddREST() {
            return $x("//button[@data-test='add-rest-button']");
        }

        public SelenideElement inputFilterServices() {
            return $x("//input[@data-test='search-service']");
        }

        public SelenideElement inputAddDialogTitle() {
            return $x("//input[@data-test='dialog-title']");
        }

        public SelenideElement inputNewServiceUrl() {
            return $x("//input[contains(@name, 'serviceUrl')]");
        }

        public SelenideElement inputNewServiceCode() {
            return $x("//input[contains(@name, 'serviceCode')]");
        }

        public SelenideElement messageServiceUrl() {
            return $x("//div[contains(@class, 'v-messages__message')]");
        }

        public SelenideElement messageServiceCode() {
            return $x("//div[contains(@class, 'v-input') and .//input[@name='serviceCode']]//div[contains(@class, 'v-messages__message')]");
        }

        public SelenideElement btnConfirmAddService() {
            return $x("//button[@data-test='dialog-save-button']");
        }

        public SelenideElement btnCancelAddService() {
            return $x("//button[@data-test='dialog-cancel-button']");
        }

        public SelenideElement radioButtonRESTPath() {
            return $x("//input[@name='REST']");
        }

        public SelenideElement radioButtonRESTPathClickArea() {
            return $x("//input[@name='REST']/following-sibling::div");
        }

        public SelenideElement radioButtonOpenAPI() {
            return $x("//input[@name='OPENAPI3']");
        }

        public SelenideElement radioButtonOpenAPIClickArea() {
            return $x("//input[@name='OPENAPI3']/following-sibling::div");
        }

        public SelenideElement headerServiceDescription() {
            return $x("//*[@data-test='service-description-header']");
        }

        public SelenideElement btnServiceExpand() {
            return $x("//*[@data-test='service-description-accordion']//button");
        }

        public SelenideElement btnRefresh() {
            return $x("//button[@data-test='refresh-button']");
        }

        public SelenideElement textRefreshTimestamp() {
            return $x("//*[contains(@class, 'refresh-time')]");
        }

        public SelenideElement btnServiceDetailsDelete() {
            return $x("//button[.//*[contains(text(), 'Delete')]]");
        }

        public SelenideElement btnServiceDetailsSave() {
            return $x("//button[.//*[contains(text(), 'Save')]]");
        }

        public SelenideElement btnServiceDetailsCancel() {
            return $x("//button[.//*[contains(text(), 'Cancel')]]");
        }

        public SelenideElement toggleServiceEnable() {
            return $x("//*[contains(@class, 'v-input--selection-controls__ripple')]");
        }

        public SelenideElement btnConfirmDisable() {
            return $x("//button[@data-test='dialog-save-button']");
        }

        public SelenideElement btnCancelDisable() {
            return $x("//button[@data-test='dialog-cancel-button']");
        }

        public SelenideElement inputDisableNotice() {
            return $x("//div[contains(@class, 'dlg-edit-row') and .//*[contains(@class, 'dlg-row-title')]]//input");
        }

        public SelenideElement cellOperationUrl() {
            return $x("//td[@data-test='service-url']");
        }
    }
}
