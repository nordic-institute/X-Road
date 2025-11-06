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
import org.niis.xroad.common.test.ui.page.component.Dialog;
import org.niis.xroad.common.test.ui.utils.VuetifyHelper.Select;

import static com.codeborne.selenide.Selenide.$$x;
import static com.codeborne.selenide.Selenide.$x;
import static java.lang.String.format;
import static org.niis.xroad.common.test.ui.utils.VuetifyHelper.vSelect;

public class ClientInfoPageObj {
    private static final String GROUP_TRS = "//div[contains(@data-test, 'local-groups-table')]//tbody//tr";
    private static final String GROUP_TR_BY_CODE = GROUP_TRS + "[td[1][//span[contains(., '%s')]]]";
    private static final String GROUP_TR_BY_INDEX = GROUP_TRS + "[%d]";

    public final ClientInfoNavigation navigation = new ClientInfoNavigation();
    public final Details details = new Details();
    public final LocalGroups localGroups = new LocalGroups();
    public final InternalServers internalServers = new InternalServers();
    public final Services services = new Services();
    public final ServiceWarningDialog warningDialog = new ServiceWarningDialog();
    public final RenameClientDialog renameClientDialog = new RenameClientDialog();

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
            return $x("//div[@data-test='cert-name']/span");
        }

        public SelenideElement certificateByName(String name) {
            return $x(format("//div[@data-test='cert-name' and span/text()='%s']", name));
        }

        public SelenideElement btnDisable() {
            return $x("//button[@data-test='disable-client-button']");
        }

        public SelenideElement btnEdit() {
            return $x("//button[@data-test='rename-client-button']");
        }

        public SelenideElement renameStatusText() {
            return $x("//div[@data-test='rename-status']//div[contains(@class,'v-chip__content')]");
        }
    }

    public static class InternalServers {

        public SelenideElement btnExport() {
            return $x("//button[@data-test='export-button']");
        }

        public SelenideElement menuConnectionType() {
            return $x("//div[contains(@class, 'v-select__selection')]");

        }

        private SelenideElement tlsCertificateTable() {
            return $x("//div[@data-test='tls-certificate-table']//table");
        }

        public SelenideElement linkTLSCertificate() {
            return tlsCertificateTable().$x(".//div[@data-test='tls-certificate-link']");
        }

        public SelenideElement tlsCertificateSubjectDistinguishedName() {
            return tlsCertificateTable().$x(".//td[@data-test='tls-certificate-subject-distinguished-name']");
        }

        public SelenideElement tlsCertificateNotBefore() {
            return tlsCertificateTable().$x(".//span[@data-test='tls-certificate-not-before']");
        }

        public SelenideElement tlsCertificateNotAfter() {
            return tlsCertificateTable().$x(".//span[@data-test='tls-certificate-not-after']");
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
            return $x(GROUP_TR_BY_CODE.formatted(code) + "/td[1]");
        }

        public SelenideElement groupByPos(int pos) {
            return $x(GROUP_TR_BY_INDEX.formatted(pos) + "/td[1]/div/span");
        }

        public ElementsCollection groups() {
            return $$x(GROUP_TRS + "/td[1]/div/span");
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
                return $x(format("//div[@data-test='group-members-table']//tbody/tr[./td/text()='%s']",
                        code));
            }

            public SelenideElement btnRemoveMemberByCode(String code) {
                return memberByCode(code).$x(".//button[ //span[text()= 'Remove']]");
            }

            public ElementsCollection btnRemove() {
                return $$x("//button[.//*[text() = 'Remove']]");
            }

            public SelenideElement inputLocalGroupDescription() {
                return $x("//div[@data-test='local-group-edit-description-input']");
            }

        }

        public static class AddMember extends Dialog {

            public Select selectMemberInstance() {
                return vSelect($x("//div[@data-test='select-member-instance']"));
            }

            public Select selectMemberClass() {
                return vSelect($x("//div[@data-test='select-member-class']"));
            }

            public SelenideElement btnSearch() {
                return $x("//button[.//span[text() = 'Search']]");
            }


            public SelenideElement checkboxSelectMember(String member) {
                return $x(format("//tr[td[3][text()='%s']]"
                        + "//div[@data-test='add-local-group-member-checkbox']", member));
            }
        }
    }

    public static class Services {
        public final ServicesAddSubjectDialog addSubjectDialog = new ServicesAddSubjectDialog();
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
            return $x(format("//div[@data-test='service-description-accordion' "
                    + "and .//span[@data-test='service-description-header-url' and normalize-space(.)='%s']]", description));
        }

        public SelenideElement headerServiceDescriptionEdit(String description) {
            return headerServiceDescription(description).$x(".//i[@data-test='service-description-header-edit']");
        }

        public SelenideElement headerServiceDescriptionExpand(String description) {
            return headerServiceDescription(description).$x(".//button[.//i[contains(@class,'chevron_right')]]");
        }

        public SelenideElement headerServiceToggle(String description) {
            return headerServiceDescription(description).$x(".//button[@data-test='service-description-enable-disable']");
        }

        public SelenideElement serviceRow(String serviceCode) {
            return $x(format("//div[@data-test='services-table']//tbody/tr[./td[1][.//span[contains(., '%s')]]]", serviceCode));
        }

        public SelenideElement linkServiceCode(String serviceCode) {
            return serviceRow(serviceCode).$x("./td[1]/div");
        }


        public SelenideElement tableServiceUrlOfServiceCode(String serviceCode) {
            return serviceRow(serviceCode).$x("./td[2]");
        }

        public SelenideElement tableServiceTimeoutOfServiceCode(String serviceCode) {
            return serviceRow(serviceCode).$x("./td[3]");
        }

        public SelenideElement inputDisableNotice() {
            return $x("//div[@data-test='disable-notice-text-field']");
        }

        public SelenideElement accessRightsTableRowOfId(String id) {
            return $x(format("//div[@data-test='access-rights-subjects']//tbody/tr[td[2][text()='%s']]", id));
        }

        public SelenideElement accessRightsTableRowRemoveOfId(String id) {
            return accessRightsTableRowOfId(id).$x(".//button[@data-test='remove-subject']");
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

    public static class ServicesAddSubjectDialog extends Dialog {

        public ServicesAddSubjectDialog() {
            super(".//span[@data-test='dialog-title' and text() = 'Add Subjects']");
        }

        private static final String PATH_BUTTON_CLEAR_INPUT = ".//i[contains(@class, 'close_small')]";

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
            return self().$$x(".//table/tbody/tr[td[2]]");
        }

        public SelenideElement memberTableRowOfId(String id) {
            return $x(format("//table//tr[td[3][text()='%s']]", id));
        }

        public SelenideElement memberTableRowCheckboxOfId(String id) {
            return memberTableRowOfId(id).$x(".//div[@data-test='service-client-checkbox']");
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
            return $x("//button[.//span[text()='Save']]");
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
                    + "//td[1]/div/span", rowNo, id));
        }

        public SelenideElement tableMemberNameOfId(String id) {
            return $x(format("//div[@data-test='service-clients-main-view-table']//tr[td[2]/div[normalize-space(text())='%s'] ]"
                    + "//td[1]/div", id));
        }
    }

    public static class ServiceClientsEdit {
        public SelenideElement cellMemberName() {
            return $x("//div[@data-test='service-clients-table']//tbody/tr/td[1]");
        }

        public SelenideElement cellId() {
            return $x("//div[@data-test='service-clients-table']//tbody/tr/td[2]");
        }

        public SelenideElement tableAccessRightsOfServiceCode(String id) {
            return $x(format("//div[@data-test='service-client-access-rights-table']//tbody/tr[td[1]/div/span[text()='%s'] ]",
                    id));
        }

        public SelenideElement tableAccessRightsEmptyMsg() {
            return $x("//td[normalize-space(text())='No access rights to this client']");
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

        public SelenideElement membersTable() {
            return $x("//div[@data-test='service-clients-table']//tbody");
        }

        public ElementsCollection tableMemberRows() {
            return membersTable().$$x("./tr");
        }

        public SelenideElement tableMemberRowRadioById(String id) {
            return membersTable().$x(format("./tr[td[3][text()='%s']]/td[1]/div", id));
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

    public static class ServiceWarningDialog extends Dialog {

        public SelenideElement btnContinue() {
            return btnConfirm();
        }
    }

    public static class RenameClientDialog extends Dialog {

        public SelenideElement inputName() {
            return $x("//div[@data-test='subsystem-name-input']");
        }
    }
}
