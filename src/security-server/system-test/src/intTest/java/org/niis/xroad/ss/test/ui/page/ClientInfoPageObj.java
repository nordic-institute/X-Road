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
            return $x(format("//span[@data-test='cert-name' and text()='%s']", name));
        }

        public SelenideElement btnDisable() {
            return $x("//button[@data-test='disable-client-button']");
        }
    }

    public static class InternalServers {

        public SelenideElement btnExport() {
            return $x("//button[@data-test='export-button']");
        }

        public SelenideElement menuConnectionType() {
            return $x("//div[contains(@class, 'v-select__selection')]");

        }

        public SelenideElement linkTLSCertificate() {
            return $x("//table[contains(@class, 'server-certificates')]//span[contains(@class, 'certificate-link')]");
        }

        public SelenideElement inputTlsCertificate() {
            return $x("//input[@type='file']");
        }
    }

    public static class ClientInfoNavigation {
        public SelenideElement serviceClientsTab() {
            return $x("//a[contains(@class, 'v-tab')]/span[text()='Service clients']");
        }

        public SelenideElement servicesTab() {
            return $x("//a[contains(@class, 'v-tab')]/span[text()='Services']");
        }

        public SelenideElement internalServersTab() {
            return $x("//a[contains(@class, 'v-tab')]/span[text()='Internal servers']");
        }

        public SelenideElement localGroupsTab() {
            return $x("//a[contains(@class, 'v-tab')]/span[text()='Local groups']");
        }
    }


    public static class LocalGroups {
        public final Details details = new Details();

        public SelenideElement inputFilter() {
            return $x("//div[@data-test='local-group-search-input']");
        }

        public SelenideElement btnAddLocalGroup() {
            return $x("//button[@data-test='add-local-group-button']");
        }

        public SelenideElement tableHeader(String name) {
            return $x(format("//thead//span[text()='%s']", name));
        }

        public SelenideElement inputLocalGroupCode() {
            return $x("//div[@data-test='add-local-group-code-input']");
        }

        public SelenideElement inputLocalGroupDescription() {
            return $x("//div[@data-test='add-local-group-description-input']");
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
                return $x("//div[@data-test='local-group-edit-description-input']");
            }

            public SelenideElement btnClose() {
                return $x("//button[@data-test='local-group-close-button']");
            }
        }

        public static class AddMember {
            public SelenideElement inputInstance() {
                return $x("//div[@role ='button' and div/label[text() = 'Instance']]");
            }

            public SelenideElement inputMemberCode() {
                return $x("//div[@role ='button' and div/label[text() = 'Member class']]");
            }

            public SelenideElement btnSearch() {
                return $x("//div[@class = 'search-wrap']//button");
            }

            public SelenideElement btnAddSelected() {
                return $x("//button[span[text()='Add selected']]");
            }

            public SelenideElement checkboxSelectMember(String member) {
                return $x(format("//table[contains(@class,'members-table')]//tr[td[3][text()='%s']]"
                       + "//div[@data-test='add-local-group-member-checkbox']", member));
            }
        }
    }

    public static class Services {
        public final ServicesAddSubject addSubject = new ServicesAddSubject();
        public final ServicesParameters servicesParameters = new ServicesParameters();
        public final ServicesEndpoints endpoints = new ServicesEndpoints();
        public final ServicesEdit servicesEdit = new ServicesEdit();

        public SelenideElement btnAddWSDL() {
            return $x("//button[@data-test='add-wsdl-button']");
        }

        public SelenideElement btnAddREST() {
            return $x("//button[@data-test='add-rest-button']");
        }

        public SelenideElement btnRefresh() {
            return $x("//button[@data-test='refresh-button']");
        }

        public SelenideElement btnEndpoints() {
            return $x("//a[@data-test='endpoints']");
        }

        public SelenideElement radioRESTPath() {
            return $x("//div[@data-test='rest-radio-button']");
        }

        public SelenideElement radioOpenAPI() {
            return $x("//div[@data-test='openapi3-radio-button']");
        }

        public SelenideElement headerServiceDescription(String description) {
            return $x(format("//*[@data-test='service-description-header' and normalize-space(text())='%s']", description));
        }

        public SelenideElement headerServiceDescriptionExpand(String description) {
            return $x(format("//div[contains(@class,'exp-header') "
                    + "and div/div[@data-test='service-description-header' "
                    + "and normalize-space(text())='%s']]//div[concat(@class,'exp-header')]/button", description));
        }

        public SelenideElement headerServiceToggle(String description) {
            return $x(format("//div[div/div[@data-test='service-description-header' and normalize-space(text())='%s']]"
                    + "//div[@data-test='service-description-enable-disable']", description));
        }

        public SelenideElement linkServiceCode(String serviceCode) {
            return $x(format("//*[@data-test='service-link' and normalize-space(text())='%s']", serviceCode));
        }


        public SelenideElement tableServiceUrlOfServiceCode(String serviceCode) {
            return $x(format("//tr[td[@data-test='service-link' and normalize-space(text())='%s'] ]//td[@data-test='service-url']",
                    serviceCode));
        }

        public SelenideElement tableServiceTimeoutOfServiceCode(String serviceCode) {
            return $x(format("//tr[td[@data-test='service-link' and normalize-space(text())='%s'] ]//td[3]", serviceCode));
        }

        public SelenideElement inputDisableNotice() {
            return $x("//div[@data-test='disable-notice-text-field']");
        }

        public SelenideElement accessRightsTableRowOfId(String id) {
            return $x(format("//table[contains(@class,'group-members-table')]//td[text()='%s']", id));
        }

        public SelenideElement accessRightsTableRowRemoveOfId(String id) {
            return $x(format("//table[contains(@class,'group-members-table')]//tr[ td[text()='%s']]//button[@data-test='remove-subject']",
                    id));
        }
    }

    public static class ServicesEdit {
        public SelenideElement btnServiceDelete() {
            return $x("//button[@data-test='service-description-details-delete-button']");
        }

        public SelenideElement btnSaveEdit() {
            return $x("//button[@data-test='service-description-details-save-button']");
        }

        public SelenideElement textFieldUrl() {
            return $x("//div[@data-test='service-url-text-field']");
        }

        public SelenideElement textFieldServiceCode() {
            return $x("//div[@data-test='service-code-text-field']");
        }

        public SelenideElement btnContinueWarn() {
            return $x("//button[@data-test='service-url-change-button']");
        }

        public SelenideElement checkboxUrlApplyAll() {
            return $x("//div[@data-test='url-all']");
        }

        public SelenideElement checkboxTimeoutApplyAll() {
            return $x("//div[@data-test='timeout-all']");
        }

        public SelenideElement checkboxVerifySslApplyAll() {
            return $x("//div[@data-test='ssl-auth-all']");
        }

    }

    public static class ServicesParameters {
        public SelenideElement inputServiceUrl() {
            return $x("//div[@data-test='service-url-text-field']");
        }

        public SelenideElement inputServiceCode() {
            return $x("//div[@data-test='service-code-text-field']");
        }

        public SelenideElement inputServiceTimeout() {
            return $x("//div[@data-test='service-timeout-text-field']");
        }

        public SelenideElement inputVerifyTlsCert() {
            return $x("//div[@data-test='ssl-auth']");
        }

        public SelenideElement btnSaveEdit() {
            return $x("//button[@data-test='save-service-parameters']");
        }

        public SelenideElement btnAddSubjects() {
            return $x("//button[@data-test='show-add-subjects']");
        }

        public SelenideElement btnRemoveAllSubjects() {
            return $x("//button[@data-test='remove-subjects']");
        }

    }

    public static class ServicesAddSubject {
        private static final String PATH_BUTTON_CLEAR_INPUT = ".//i[contains(@class, 'mdi-close-circle')]";

        public SelenideElement inputName() {
            return $x("//div[@data-test='name-text-field']");
        }

        public SelenideElement buttonClearInputName() {
            return inputName().$x(PATH_BUTTON_CLEAR_INPUT);
        }

        public SelenideElement inputMemberCode() {
            return $x("//div[@data-test='member-code-text-field']");
        }

        public SelenideElement buttonClearInputMemberCode() {
            return inputMemberCode().$x(PATH_BUTTON_CLEAR_INPUT);
        }

        public SelenideElement inputSubsystemCode() {
            return $x("//div[@data-test='subsystem-code-text-field']");
        }

        public SelenideElement buttonClearInputSubsystemCode() {
            return inputSubsystemCode().$x(PATH_BUTTON_CLEAR_INPUT);
        }

        public SelenideElement btnSearch() {
            return $x("//button[@data-test='search-button']");
        }

        public ElementsCollection memberTableRows() {
            return $$x("//table[contains(@class,'members-table')]//tbody/tr");
        }

        public SelenideElement memberTableRowOfId(String id) {
            return $x(format("//table//tr[td[3][text()='%s']]", id));
        }

        public SelenideElement memberTableRowCheckboxOfId(String id) {
            return memberTableRowOfId(id).$x(".//div[@data-test='service-client-checkbox']");
        }

        public SelenideElement btnSave() {
            return $x("//button[@data-test='save']");
        }
    }

    public static class ServicesEndpoints {
        public SelenideElement btnAddEndpoint() {
            return $x("//button[@data-test='endpoint-add']");
        }

        public SelenideElement btnDeleteEndpoint() {
            return $x("//button[@data-test='delete-endpoint']");
        }

        public SelenideElement btnSave() {
            return $x("//button[span[text()='Save']]");
        }

        public SelenideElement inputPath() {
            return $x("//div[@data-test='endpoint-path']");
        }

        public SelenideElement dropdownHttpMethod() {
            return $x("//div[@data-test='endpoint-method']");
        }

        public SelenideElement endpointRow(String httpMethod, String path) {
            return $x(format("//tbody/tr[ td[1]/span[text()='%s'] and td[2][text()='%s']]", httpMethod, path));
        }

        public SelenideElement buttonEndpointRowEdit(String httpMethod, String path) {
            return endpointRow(httpMethod, path).$x(".//button[@data-test='endpoint-edit']");
        }
    }

    public static class ServiceClients {
        public final ServiceClientsAddSubject addSubject = new ServiceClientsAddSubject();
        public final ServiceClientsEdit edit = new ServiceClientsEdit();

        public SelenideElement btnAddSubject() {
            return $x("//button[@data-test='add-service-client']");
        }

        public SelenideElement inputMemberSearch() {
            return $x("//div[@data-test='search-service-client']");
        }

        public SelenideElement tableHeaderOfCol(int colNo) {
            return $x(format("//div[@data-test='service-clients-main-view-table']//thead/tr/th[%d]", colNo));
        }

        public SelenideElement tableMemberNameOfId(int rowNo, String id) {
            return $x(format("//div[@data-test='service-clients-main-view-table']//tr[%d][td[2]/div[normalize-space(text())='%s'] ]"
                    + "//td[1]/div[@data-test='open-access-rights']", rowNo, id));
        }

        public SelenideElement tableMemberNameOfId(String id) {
            return $x(format("//div[@data-test='service-clients-main-view-table']//tr[td[2]/div[normalize-space(text())='%s'] ]"
                    + "//td[1]/div[@data-test='open-access-rights']", id));
        }
    }

    public static class ServiceClientsEdit {
        public SelenideElement cellMemberName() {
            return $x("//table[@data-test='service-clients-table']/tr/td[1]");
        }

        public SelenideElement cellId() {
            return $x("//table[@data-test='service-clients-table']/tr/td[2]");
        }

        public SelenideElement tableAccessRightsOfServiceCode(String id) {
            return $x(format("//table[@data-test='service-client-access-rights-table']/tbody/tr[td[1][normalize-space(text())='%s'] ]",
                    id));
        }

        public SelenideElement tableAccessRightsEmptyMsg() {
            return $x("//p[normalize-space(text())='No access rights to this client']");
        }

        public SelenideElement btnRemoveByServiceCode(String serviceCode) {
            return tableAccessRightsOfServiceCode(serviceCode).$x(".//button[@data-test='access-right-remove']");
        }

        public SelenideElement btnRemoveAll() {
            return $x(".//button[@data-test='remove-all-access-rights']");
        }

        public SelenideElement btnAddService() {
            return $x(".//button[@data-test='add-subjects-dialog']");
        }
    }

    public static class ServiceClientsAddSubject {
        public SelenideElement inputMemberSearch() {
            return $x("//div[@data-test='search-service-client']");
        }

        public SelenideElement inputServiceSearch() {
            return $x("//div[@data-test='search-service-client-service']");
        }

        public ElementsCollection tableMemberRows() {
            return $$x("//table[contains(@class,'service-clients-table')]/tbody/tr");
        }

        public SelenideElement tableMemberRowRadioById(String id) {
            return $x(format("//table[contains(@class,'service-clients-table')]"
                    + "//tr[td[3][text()='%s']]//div[contains(@class,'checkbox-wrap')]", id));
        }

        public SelenideElement tableMemberRowRadioInputById(String id) {
            return $x(format("//table[contains(@class,'service-clients-table')]"
                    + "//tr[td[3][text()='%s']]//div[contains(@class,'checkbox-wrap')]//input", id));
        }

        public ElementsCollection tableServiceRows() {
            return $$x("//table//tr[@data-test='access-right-toggle']");
        }

        public SelenideElement tableServiceRowRadioById(String id) {
            return $x(format("//table//tr[@data-test='access-right-toggle' and td[2][text()='%s']]"
                    + "//div[@data-test='access-right-checkbox-input']", id));
        }

        public SelenideElement btnNext() {
            return $x("//button[@data-test='next-button']");
        }

        public SelenideElement btnFinish() {
            return $x("//button[@data-test='finish-button']");
        }

        public SelenideElement btnPrevious() {
            return $x("//button[@data-test='previous-button']");
        }

        public SelenideElement btnCancelWizardMemberPage() {
            return $x("(//button[@data-test='cancel-button'])[1]");
        }
    }
}
