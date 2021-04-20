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

const clientsTab = require('./tabs/clientsTab');
const keysTab = require('./tabs/keysTab');
const diagnosticsTab = require('./tabs/diagnosticsTab');
const settingsTab = require('./tabs/settingsTab');
const addSubjectsPopup = require('./common/addSubjectsPopup');

var navigateCommands = {
  openClientsTab: function () {
    this.click('@clientsTab');
    return this;
  },
  openKeysTab: function () {
    this.click('@keysTab');
    return this;
  },
  openDiagnosticsTab: function () {
    this.click('@diagnosticsTab');
    return this;
  },
  openSettingsTab: function () {
    this.click('@settingsTab');
    return this;
  },
  logout: function () {
    this.click('@userMenuButton');
    this.pause(1000);
    this.click('@userMenuitemLogout');
    return this;
  },
  acceptLogout: function () {
    this.click('@logoutOKButton');
    return this;
  },
  closeSnackbar: function () {
    this.click('@snackBarCloseButton');
    return this;
  },
  closeAlertMessage: function () {
    this.click('@alertCloseButton');
    return this;
  },
  closeSessionExpiredPopup: function () {
    this.click('@sessionExpiredPopupOkButton');
    return this;
  },
  verifyCurrentUser: function (user) {
    this.api.assert.containsText(this.elements.userMenuButton, user);

    return this;
  },
};

var clientInfoCommands = {
  openDetailsTab: function () {
    this.click('@detailsTab');
    return this;
  },
  openServiceClientsTab: function () {
    this.click('@serviceClientsTab');
    return this;
  },
  openServicesTab: function () {
    this.click('@servicesTab');
    return this;
  },
  openInternalServersTab: function () {
    this.click('@internalServersTab');
    return this;
  },
  openLocalGroupsTab: function () {
    this.click('@localGroupsTab');
    return this;
  },
};

var clientDetailsCommands = {
  openSignCertificateInfo: function () {
    this.click('@clientSignCertificate');
    return this;
  },
};

var certificatePopupCommands = {
  close: function () {
    this.click('@certificateInfoCloseButton');
    return this;
  },
  deleteCert: function () {
    this.api.keys(this.api.Keys.PAGEUP);
    this.click('@deleteButton');
    return this;
  },
};

var clientInternalServersCommands = {
  addCert: function (certfile) {
    this.api.setValue(
      '//input[@type="file"]',
      require('path').resolve(__dirname + certfile),
    );
    return this;
  },
  openAddCertDialog: function () {
    this.click('@addButton');
    return this;
  },
  exportCert: function () {
    this.click('@exportButton');
    this.api.pause(1000);
    this.api.keys(this.api.Keys.ENTER);
    return this;
  },
  openTLSCert: function () {
    this.click('@tlsCertificate');
    return this;
  },
  selectConnectionType: function (type) {
    this.click('@connectionTypeMenu');

    this.api.pause(1000);
    // The picker menu is attached to the main app dom tree, not the dialog
    this.api.click(
      '//div[@role="listbox"]//div[@role="option" and contains(./descendant-or-self::*/text(),"' +
        type +
        '")]',
    );

    return this;
  },
};

var localGroupPopupCommands = {
  changeCode: function (code) {
    this.clearValue2('@localGroupCode');
    this.setValue('@localGroupCode', code);
    return this;
  },
  changeDescription: function (description) {
    this.clearValue2('@localGroupDescription');
    this.setValue('@localGroupDescription', description);
    return this;
  },
  deleteThisGroup: function () {
    this.click('@localGroupDeleteButton');
    return this;
  },
  openAddMembers: function () {
    this.click('@localGroupAddMembersButton');
    return this;
  },
  searchMembers: function () {
    this.click('@localGroupSearchWrap');
    this.click('@localGroupSearchButton');
    return this;
  },
  addSelectedMembers: function () {
    this.click('@localGroupAddSelectedButton');
    return this;
  },
  cancelAddMembersDialog: function () {
    this.click('@localGroupCancelAddButton');
    return this;
  },
  selectNewTestComMember: function () {
    this.click('@localGroupTestComCheckbox');
    return this;
  },
  selectMember: function (member) {
    this.api.click(
      `//tr[.//*[contains(text(), "${member}")]]//*[contains(@class, "v-input--selection-controls__ripple")]`,
    );
    return this;
  },
  clickRemoveAll: function () {
    this.click('@localGroupRemoveAllButton');
    return this;
  },
  clickRemoveTestComMember: function () {
    this.click('@localGroupTestComRemoveButton');
    return this;
  },
  confirmMemberRemove: function () {
    this.click('@localGroupRemoveYesButton');
    return this;
  },
  cancelMemberRemove: function () {
    this.click('@localGroupRemoveCancelButton');
    return this;
  },
  confirmDelete: function () {
    this.click('@localGroupRemoveYesButton');
    return this;
  },
  cancelDelete: function () {
    this.click('@localGroupRemoveCancelButton');
    return this;
  },
  clickDescriptionLabel: function () {
    this.click('@localGroupDescriptionLabel');
    return this;
  },
  close: function () {
    this.click('@localGroupPopupCloseButton');
    return this;
  },
};

var servicesWarningPopupCommands = {
  accept: function () {
    this.click('@warningContinueButton');
    return this;
  },
  cancel: function () {
    this.click('@warningCancelButton');
    return this;
  },
};

var clientServicesCommands = {
  filterBy: function (filter) {
    this.clearValue2('@filterServices');
    this.setValue('@filterServices', filter);
    return this;
  },
  openAddWSDL: function () {
    this.click('@addWSDLButton');
    return this;
  },
  openAddREST: function () {
    this.click('@addRESTButton');
    return this;
  },
  confirmAddDialog: function () {
    this.click('@confirmAddServiceButton');
    return this;
  },
  cancelAddDialog: function () {
    this.click('@cancelAddServiceButton');
    return this;
  },
  enterServiceUrl: function (url) {
    this.clearValue2('@newServiceUrl');
    this.setValue('@newServiceUrl', url);
    return this;
  },
  enterServiceCode: function (code) {
    this.clearValue2('@newServiceCode');
    this.setValue('@newServiceCode', code);
    return this;
  },
  selectRESTPath: function () {
    this.click('@RESTPathRadioButtonClickArea');
    return this;
  },
  selectOpenApi: function () {
    this.click('@OpenApiRadioButtonClickArea');
    return this;
  },
  openServiceDetails: function () {
    this.click('@serviceDescription');
    return this;
  },
  expandServiceDetails: function () {
    this.click('@serviceExpandButton');
    return this;
  },
  refreshServiceData: function () {
    this.click('@refreshButton');
    return this;
  },
  toggleEnabled: function () {
    this.click('@serviceEnableToggle');
    return this;
  },
  enterDisableNotice: function (notice) {
    this.clearValue2('@disableNotice');
    this.setValue('@disableNotice', notice);
    return this;
  },
  confirmDisable: function () {
    this.click('@confirmDisableButton');
    return this;
  },
  cancelDisable: function () {
    this.click('@cancelDisableButton');
    return this;
  },
  openOperation: function (op) {
    this.api.click(
      this.selector +
        '//td[@data-test="service-link" and contains(text(),"' +
        op +
        '")]',
    );
    return this;
  },
};

var clientLocalGroupsCommands = {
  openAddLocalGroupDialog: function () {
    this.click('@addGroupButton');
    return this;
  },
  filterBy: function (filter) {
    this.clearValue2('@filterInput');
    this.setValue('@filterInput', filter);
    return this;
  },
  openAddDialog: function () {
    this.click('@addGroupButton');
    return this;
  },
  confirmAddDialog: function () {
    this.click('@confirmAddButton');
    return this;
  },
  cancelAddDialog: function () {
    this.click('@cancelAddButton');
    return this;
  },
  enterCode: function (code) {
    this.clearValue2('@groupCode');
    this.setValue('@groupCode', code);
    return this;
  },
  enterDescription: function (description) {
    this.clearValue2('@groupDescription');
    this.setValue('@groupDescription', description);
    return this;
  },
  openAbbDetails: function () {
    this.click('@groupCodeCellAbb');
    return this;
  },
  openBacDetails: function () {
    this.click('@groupCodeCellBac');
    return this;
  },
  openCbbDetails: function () {
    this.click('@groupCodeCellCbb');
    return this;
  },
  verifyGroupListRow: function (row, code) {
    this.api.waitForElementVisible(
      '(//table[contains(@class, "details-certificates")]/tr)[' +
        row +
        ']//span[contains(text(),"' +
        code +
        '")]',
    );
    return this;
  },
  openDetails: function (code) {
    this.api.click(
      this.selector +
        '//table[contains(@class, "details-certificates")]//span[contains(text(),"' +
        code +
        '")]',
    );
    return this;
  },
};

var serviceDetailsCommands = {
  closeServiceDetails: function () {
    this.click('@serviceDetailsCloseButton');
    return this;
  },
  deleteService: function () {
    this.click('@deleteServiceButton');
    return this;
  },
  enterServiceUrl: function (url) {
    this.clearValue2('@serviceURL');
    this.setValue('@serviceURL', url);
    return this;
  },
  enterServiceCode: function (code) {
    this.clearValue2('@serviceCode');
    this.setValue('@serviceCode', code);
    return this;
  },
  confirmDelete: function () {
    this.click('@confirmDeleteButton');
    return this;
  },
  cancelDelete: function () {
    this.click('@cancelDeleteButton');
    return this;
  },
  confirmDialog: function () {
    this.click('@confirmDialogButton');
    return this;
  },
  cancelDialog: function () {
    this.click('@cancelDialogButton');
    return this;
  },
};

var wsdlOperationCommands = {
  openEndpointsTab: function () {
    this.click('@endpointsTab');
    return this;
  },
  saveParameters: function () {
    this.click('@saveButton');
    return this;
  },
  close: function () {
    this.click('@closeButton');
    return this;
  },
  toggleUrlApplyToAll: function () {
    this.click('@urlApplyToAllCheckbox');
    return this;
  },
  toggleTimeoutApplyToAll: function () {
    this.click('@timeoutApplyToAllCheckbox');
    return this;
  },
  toggleVerifyCertApplyToAll: function () {
    this.click('@verifyCertApplyToAllCheckbox');
    return this;
  },
  enterUrl: function (url) {
    this.clearValue2('@serviceURL');
    this.setValue('@serviceURL', url);
    return this;
  },
  enterTimeout: function (timeout) {
    this.clearValue2('@timeout');
    this.setValue('@timeout', timeout);
    return this;
  },
  toggleCertVerification: function () {
    this.click('@sslAuthClickarea');
    return this;
  },
  openAddAccessRights: function () {
    this.click('@addSubjectsButton');
    return this;
  },
  removeAllAccessRights: function () {
    this.click('@removeAllButton');
    return this;
  },
  removeAccessRight: function (subject) {
    this.api.click(
      this.selector +
        '//table[contains(@class, "group-members-table")]//tr[.//td[contains(text(), "' +
        subject +
        '")]]//button[@data-test="remove-subject"]',
    );
    return this;
  },
};

var sslCheckFailDialogCommands = {
  continue: function () {
    this.click('@continueButton');
    return this;
  },
  cancel: function () {
    this.click('@cancelButton');
    return this;
  },
};

var restEndpointCommands = {
  openAddDialog: function () {
    this.click('@addButton');
    return this;
  },
  openParametersTab: function () {
    this.click('@parametersTab');
    return this;
  },
  verifyEndpointRow: function (row, method, path) {
    //this.api.waitForElementVisible('(//table[.//*[contains(text(),"HTTP Request Method")]]/tbody/tr)['+row+']//td[contains(./descendant-or-self::*/text(),"'+method+'") and ..//td[contains(./descendant-or-self::*/text(),"'+path+'")]]');
    //TODO: Sorting is not currently functional, so check only that the row exists
    this.api.waitForElementVisible(
      '//tbody/tr//td[contains(./descendant-or-self::*/text(),"' +
        method +
        '") and ..//td[contains(./descendant-or-self::*/text(),"' +
        path +
        '")]]',
    );
    return this;
  },
  openEndpointAccessRights: function (method, path) {
    this.api.click(
      `//td[contains(@class, "wrap-right-tight") and preceding-sibling::td/text() = "${path}" and preceding-sibling::td/span/text() = "${method}"]//button[@data-test="endpoint-edit-accessrights"]`,
    );
    return this;
  },
  openEndpoint: function (method, path) {
    this.api.click(
      '//tr[.//*[contains(text(),"' +
        method +
        '")] and .//*[contains(text(),"' +
        path +
        '")]]//button[@data-test="endpoint-edit"]',
    );
    return this;
  },
  close: function () {
    this.click('@closeButton');
    return this;
  },
};

var addEndpointCommands = {
  enterPath: function (path) {
    this.clearValue2('@requestPath');
    this.setValue('@requestPath', path);
    return this;
  },
  selectRequestMethod: function (method) {
    this.click('@methodDropdown');

    this.api.pause(1000);
    // The picker menu is attached to the main app dom tree, not the dialog
    this.api.click(
      '//div[@role="listbox"]//div[@role="option" and contains(./descendant-or-self::*/text(),"' +
        method +
        '")]',
    );

    return this;
  },
  clickMethodMenu: function () {
    this.click('@methodDropdown');
    return this;
  },
  addSelected: function () {
    this.click('@addButton');
    return this;
  },
  cancel: function () {
    this.click('@cancelButton');
    return this;
  },
  deleteEndpoint: function () {
    this.click('@deleteButton');
    return this;
  },
  verifyMethodExists: function (method) {
    this.api.waitForElementVisible(
      '//div[@role="listbox"]//div[@role="option" and contains(./descendant-or-self::*/text(),"' +
        method +
        '")]',
    );
    return this;
  },
  confirmDelete: function () {
    this.click('@deleteYesButton');
    return this;
  },
  cancelDelete: function () {
    this.click('@deleteCancelButton');
    return this;
  },
};

var confirmationDialogCommands = {
  confirm: function () {
    this.api.keys(this.api.Keys.TAB);
    this.api.keys(this.api.Keys.TAB);
    this.api.keys(this.api.Keys.SPACE);
    //this.click('@yesButton');
    return this;
  },
  cancel: function () {
    this.api.keys(this.api.Keys.TAB);
    this.api.keys(this.api.Keys.SPACE);
    //this.click('@cancelButton');
    return this;
  },
};

module.exports = {
  url: process.env.VUE_DEV_SERVER_URL,
  commands: [navigateCommands],
  elements: {
    clientsTab: {
      selector: '//a[@data-test="clients"]',
      locateStrategy: 'xpath',
    },
    keysTab: {
      selector: '//a[@data-test="keys"]',
      locateStrategy: 'xpath',
    },
    diagnosticsTab: {
      selector: '//a[@data-test="diagnostics"]',
      locateStrategy: 'xpath',
    },
    settingsTab: {
      selector: '//a[@data-test="settings"]',
      locateStrategy: 'xpath',
    },
    userMenuButton: {
      selector: '//button[@data-test="username-button"]',
      locateStrategy: 'xpath',
    },
    userMenuitemLogout: {
      selector: '//*[@data-test="logout-list-tile"]',
      locateStrategy: 'xpath',
    },
    logoutOKButton: {
      selector:
        '//div[contains(@class, "v-dialog")]//button[.//*[contains(text(), "Ok")]]',
      locateStrategy: 'xpath',
    },
    snackBarCloseButton: {
      selector: '//button[@data-test="close-snackbar"]',
      locateStrategy: 'xpath',
    },
    snackBarMessage: {
      selector: '//div[contains(@class, "v-snack__content")]',
      locateStrategy: 'xpath',
    },
    alertCloseButton: {
      selector: '//button[@data-test="close-snackbar"]',
      locateStrategy: 'xpath',
    },
    alertMessage: {
      selector:
        '//div[@data-test="contextual-alert"]//div[contains(@class, "row-wrapper")]/div',
      locateStrategy: 'xpath',
    },
    sessionExpiredPopupOkButton: {
      selector: '//button[@data-test="session-expired-ok-button"]',
      locateStrategy: 'xpath',
    },
  },
  sections: {
    clientsTab: clientsTab,
    keysTab: keysTab,
    diagnosticsTab: diagnosticsTab,
    settingsTab: settingsTab,
    clientInfo: {
      selector:
        '//div[contains(@class, "v-main__wrap") and .//*[@data-test="clients" and @aria-selected="true"]]',
      locateStrategy: 'xpath',
      commands: [clientInfoCommands],
      elements: {
        detailsTab: {
          selector:
            '//div[contains(@class, "v-tabs-bar__content")]//a[contains(@class, "v-tab") and contains(text(), "Details")]',
          locateStrategy: 'xpath',
        },
        serviceClientsTab: {
          selector:
            '//div[contains(@class, "v-tabs-bar__content")]//a[contains(@class, "v-tab") and contains(text(), "Service clients")]',
          locateStrategy: 'xpath',
        },
        servicesTab: {
          selector:
            '//div[contains(@class, "v-tabs-bar__content")]//a[contains(@class, "v-tab") and contains(text(), "Services")]',
          locateStrategy: 'xpath',
        },
        internalServersTab: {
          selector:
            '//div[contains(@class, "v-tabs-bar__content")]//a[contains(@class, "v-tab") and contains(text(), "Internal servers")]',
          locateStrategy: 'xpath',
        },
        localGroupsTab: {
          selector:
            '//div[contains(@class, "v-tabs-bar__content")]//a[contains(@class, "v-tab") and contains(text(), "Local groups")]',
          locateStrategy: 'xpath',
        },
      },
      sections: {
        details: {
          selector:
            '//div[contains(@class, "base-full-width") and .//*[contains(@class, "v-tab--active") and contains(text(),"Details")]]//div[contains(@class, "xrd-view-common")]',
          locateStrategy: 'xpath',
          commands: [clientDetailsCommands],
          elements: {
            clientSignCertificate: {
              selector: 'span.cert-name',
              locateStrategy: 'css selector',
            },
          },
        },
        internalServers: {
          selector:
            '//div[contains(@class, "base-full-width") and .//*[contains(@class, "v-tab--active") and contains(text(),"Internal servers")]]//div[contains(@class, "xrd-view-common")]',
          locateStrategy: 'xpath',
          commands: [clientInternalServersCommands],
          elements: {
            addButton: {
              selector: '//button[.//*[contains(text(), "Add")]]',
              locateStrategy: 'xpath',
            },
            exportButton: {
              selector: '//button[.//*[contains(text(), "Export")]]',
              locateStrategy: 'xpath',
            },
            connectionTypeMenu: {
              selector: '//div[contains(@class, "v-select__selection")]',
              locateStrategy: 'xpath',
            },
            tlsCertificate: {
              selector:
                '//table[contains(@class, "server-certificates")]//span[contains(@class, "certificate-link")]',
              locateStrategy: 'xpath',
            },
          },
        },
        localGroups: {
          selector:
            '//div[contains(@class, "base-full-width") and .//*[contains(@class, "v-tab--active") and contains(text(),"Local groups")]]//div[contains(@class, "xrd-view-common")]',
          locateStrategy: 'xpath',
          commands: [clientLocalGroupsCommands],
          elements: {
            filterInput: {
              selector: '//input',
              locateStrategy: 'xpath',
            },
            addGroupButton: {
              selector: '//button[@data-test="add-local-group-button"]',
              locateStrategy: 'xpath',
            },
            confirmAddButton: {
              selector: '//button[@data-test="dialog-save-button"]',
              locateStrategy: 'xpath',
            },
            cancelAddButton: {
              selector: '//button[@data-test="dialog-cancel-button"]',
              locateStrategy: 'xpath',
            },
            groupCode: {
              selector: '//input[@data-test="add-local-group-code-input"]',
              locateStrategy: 'xpath',
            },
            groupDescription: {
              selector:
                '//input[@data-test="add-local-group-description-input"]',
              locateStrategy: 'xpath',
            },
            groupCodeCellAbb: {
              selector:
                '//table[contains(@class, "details-certificates")]//span[contains(text(),"abb")]',
              locateStrategy: 'xpath',
            },
            groupCodeCellBac: {
              selector:
                '//table[contains(@class, "details-certificates")]//span[contains(text(),"bac")]',
              locateStrategy: 'xpath',
            },
            groupCodeCellCbb: {
              selector:
                '//table[contains(@class, "details-certificates")]//span[contains(text(),"cbb")]',
              locateStrategy: 'xpath',
            },
            abbDetails: {
              selector: '//span[contains(text(),"abb")]',
              locateStrategy: 'xpath',
            },
            bacDetails: {
              selector: '//span[contains(text(),"bac")]',
              locateStrategy: 'xpath',
            },
            cbbDetails: {
              selector: '//span[contains(text(),"cbb")]',
              locateStrategy: 'xpath',
            },
          },
        },
        services: {
          selector:
            '//div[contains(@class, "base-full-width") and .//*[contains(@class, "v-tab--active") and contains(text(),"Services")]]//div[contains(@class, "xrd-view-common")]',
          locateStrategy: 'xpath',
          commands: [clientServicesCommands],
          elements: {
            addWSDLButton: {
              selector: '//button[@data-test="add-wsdl-button"]',
              locateStrategy: 'xpath',
            },
            addRESTButton: {
              selector: '//button[@data-test="add-rest-button"]',
              locateStrategy: 'xpath',
            },
            filterServices: {
              selector: '//input[@data-test="search-service"]',
              locateStrategy: 'xpath',
            },
            addDialogTitle: {
              selector: '//input[@data-test="dialog-title"]',
              locateStrategy: 'xpath',
            },
            newServiceUrl: {
              selector: '//input[contains(@name, "serviceUrl")]',
              locateStrategy: 'xpath',
            },
            newServiceCode: {
              selector: '//input[contains(@name, "serviceCode")]',
              locateStrategy: 'xpath',
            },
            serviceUrlMessage: {
              selector: '//div[contains(@class, "v-messages__message")]',
              locateStrategy: 'xpath',
            },
            serviceCodeMessage: {
              selector:
                '//div[contains(@class, "v-input") and .//input[@name="serviceCode"]]//div[contains(@class, "v-messages__message")]',
              locateStrategy: 'xpath',
            },
            confirmAddServiceButton: {
              selector: '//button[@data-test="dialog-save-button"]',
              locateStrategy: 'xpath',
            },
            cancelAddServiceButton: {
              selector: '//button[@data-test="dialog-cancel-button"]',
              locateStrategy: 'xpath',
            },
            RESTPathRadioButton: {
              selector: '//input[@name="REST"]',
              locateStrategy: 'xpath',
            },
            RESTPathRadioButtonClickArea: {
              selector: '//input[@name="REST"]/following-sibling::div',
              locateStrategy: 'xpath',
            },
            OpenApiRadioButton: {
              selector: '//input[@name="OPENAPI3"]',
              locateStrategy: 'xpath',
            },
            OpenApiRadioButtonClickArea: {
              selector: '//input[@name="OPENAPI3"]/following-sibling::div',
              locateStrategy: 'xpath',
            },
            serviceDescription: {
              selector: '//*[@data-test="service-description-header"]',
              locateStrategy: 'xpath',
            },
            serviceExpandButton: {
              selector:
                '//*[@data-test="service-description-accordion"]//button',
              locateStrategy: 'xpath',
            },
            refreshButton: {
              selector: '//button[@data-test="refresh-button"]',
              locateStrategy: 'xpath',
            },
            refreshTimestamp: {
              selector: '//*[contains(@class, "refresh-time")]',
              locateStrategy: 'xpath',
            },
            serviceDetailsDeleteButton: {
              selector: '//button[.//*[contains(text(), "Delete")]]',
              locateStrategy: 'xpath',
            },
            serviceDetailsSaveButton: {
              selector: '//button[.//*[contains(text(), "Save")]]',
              locateStrategy: 'xpath',
            },
            serviceDetailsCancelButton: {
              selector: '//button[.//*[contains(text(), "Cancel")]]',
              locateStrategy: 'xpath',
            },
            serviceEnableToggle: {
              selector:
                '//*[contains(@class, "v-input--selection-controls__ripple")]',
              locateStrategy: 'xpath',
            },
            confirmDisableButton: {
              selector: '//button[@data-test="dialog-save-button"]',
              locateStrategy: 'xpath',
            },
            cancelDisableButton: {
              selector: '//button[@data-test="dialog-cancel-button"]',
              locateStrategy: 'xpath',
            },
            disableNotice: {
              selector:
                '//div[contains(@class, "dlg-edit-row") and .//*[contains(@class, "dlg-row-title")]]//input',
              locateStrategy: 'xpath',
            },
            operationUrl: {
              selector: '//td[@data-test="service-url"]',
              locateStrategy: 'xpath',
            },
          },
        },
      },
    },
    certificatePopup: {
      selector: '//div[contains(@class, "certificate-details-wrapper")]',
      locateStrategy: 'xpath',
      commands: [certificatePopupCommands],
      elements: {
        certificateInfoCloseButton: {
          selector: '//*[@data-test="close-x"]',
          locateStrategy: 'xpath',
        },
        deleteButton: {
          selector: '//button[.//*[contains(text(), "Delete")]]',
          locateStrategy: 'xpath',
        },
      },
    },
    certificateDetails: {
      selector: '//*[@data-test="certificate-details-dialog"]',
      locateStrategy: 'xpath',
      commands: [certificatePopupCommands],
      elements: {
        certificateInfoCloseButton: {
          selector: '//*[@data-test="close-x"]',
          locateStrategy: 'xpath',
        },
        deleteButton: {
          selector: '//button[.//*[contains(text(), "Delete")]]',
          locateStrategy: 'xpath',
        },
      },
    },
    localGroupPopup: {
      selector:
        '//div[contains(@class, "xrd-tab-max-width") and .//div[contains(@class, "cert-hash") and @data-test="local-group-title"]]',
      locateStrategy: 'xpath',
      commands: [localGroupPopupCommands],
      elements: {
        groupIdentifier: {
          selector: '//span[contains(@class, "identifier-wrap")]',
          locateStrategy: 'xpath',
        },
        localGroupAddMembersButton: {
          selector: '//button[@data-test="add-members-button"]',
          locateStrategy: 'xpath',
        },
        localGroupRemoveAllButton: {
          selector: '//button[@data-test="remove-all-members-button"]',
          locateStrategy: 'xpath',
        },
        localGroupDeleteButton: {
          selector: '//button[@data-test="delete-local-group-button"]',
          locateStrategy: 'xpath',
        },
        localGroupAddSelectedButton: {
          selector: '//button[.//*[contains(text(), "Add selected")]]',
          locateStrategy: 'xpath',
        },
        localGroupSearchButton: {
          selector: '//button[.//*[contains(text(), "Search")]]',
          locateStrategy: 'xpath',
        },
        localGroupCancelAddButton: {
          selector: '//button[.//*[contains(text(), "Cancel")]]',
          locateStrategy: 'xpath',
        },
        localGroupTestComCheckbox: {
          selector:
            '//tr[.//*[contains(text(), "TestCom")]]//*[contains(@class, "v-input--selection-controls__ripple")]',
          locateStrategy: 'xpath',
        },
        localGroupRemoveMemberButton: {
          selector: '//button[.//*[contains(text(), "Add group")]]',
          locateStrategy: 'xpath',
        },
        localGroupSearchWrap: {
          selector: '//div[contains(@class, "search-wrap")]',
          locateStrategy: 'xpath',
        },
        localGroupRemoveYesButton: {
          selector: '//button[@data-test="dialog-save-button"]',
          locateStrategy: 'xpath',
        },
        localGroupRemoveCancelButton: {
          selector: '//button[@data-test="dialog-cancel-button"]',
          locateStrategy: 'xpath',
        },
        localGroupTestComRemoveButton: {
          selector:
            '//tr[.//*[contains(text(), "TestCom")]]//button[.//*[contains(text(), "Remove")]]',
          locateStrategy: 'xpath',
        },
        localGroupTestGovRemoveButton: {
          selector:
            '//tr[.//*[contains(text(), "TestGov")]]//button[.//*[contains(text(), "Remove")]]',
          locateStrategy: 'xpath',
        },
        localGroupTestOrgRemoveButton: {
          selector:
            '//tr[.//*[contains(text(), "TestOrg")]]//button[.//*[contains(text(), "Remove")]]',
          locateStrategy: 'xpath',
        },
        localGroupDescriptionLabel: {
          selector: '//div[@data-test="ocal-group-edit-description-label"]',
          locateStrategy: 'xpath',
        },
        localGroupDescription: {
          selector: '//div[contains(@class, "description-input")]//input',
          locateStrategy: 'xpath',
        },
        localGroupPopupCloseButton: {
          selector: '//button[.//*[contains(text(), "Close")]]',
          locateStrategy: 'xpath',
        },
      },
    },
    servicesWarningPopup: {
      selector:
        '//div[contains(@class, "v-dialog") and .//*[contains(@class, "headline")]]',
      locateStrategy: 'xpath',
      commands: [servicesWarningPopupCommands],
      elements: {
        warningContinueButton: {
          selector: '//button[.//*[contains(text(), "Continue")]]',
          locateStrategy: 'xpath',
        },
        warningCancelButton: {
          selector: '//button[.//*[contains(text(), "Cancel")]]',
          locateStrategy: 'xpath',
        },
      },
    },
    serviceDetails: {
      selector: '//div[@data-test="service-description-details-dialog"]',
      locateStrategy: 'xpath',
      commands: [serviceDetailsCommands],
      elements: {
        serviceDetailsCloseButton: {
          selector:
            '//*[contains(@class, "cert-dialog-header")]//*[contains(@id, "close-x")]',
          locateStrategy: 'xpath',
        },
        deleteServiceButton: {
          selector:
            '//button[@data-test="service-description-details-delete-button"]',
          locateStrategy: 'xpath',
        },
        confirmDeleteButton: {
          selector: '//button[@data-test="dialog-save-button"]',
          locateStrategy: 'xpath',
        },
        cancelDeleteButton: {
          selector: '//button[@data-test="dialog-cancel-button"]',
          locateStrategy: 'xpath',
        },
        serviceURL: {
          selector: '//*[contains(@class, "url-input")]//input',
          locateStrategy: 'xpath',
        },
        URLMessage: {
          selector:
            '//*[contains(@class, "validation-provider")]//*[contains(@class, "v-messages__message")]',
          locateStrategy: 'xpath',
        },
        confirmDialogButton: {
          selector:
            '//button[@data-test="service-description-details-save-button"]',
          locateStrategy: 'xpath',
        },
        cancelDialogButton: {
          selector:
            '//button[@data-test="service-description-details-cancel-button"]',
          locateStrategy: 'xpath',
        },
      },
    },
    restServiceDetails: {
      selector: '//div[@data-test="service-description-details-dialog"]',
      locateStrategy: 'xpath',
      commands: [serviceDetailsCommands],
      elements: {
        serviceDetailsCloseButton: {
          selector:
            '//*[contains(@class, "cert-dialog-header")]//*[contains(@id, "close-x")]',
          locateStrategy: 'xpath',
        },
        deleteServiceButton: {
          selector:
            '//button[@data-test="service-description-details-delete-button"]',
          locateStrategy: 'xpath',
        },
        confirmDeleteButton: {
          selector: '//button[@data-test="dialog-save-button"]',
          locateStrategy: 'xpath',
        },
        cancelDeleteButton: {
          selector: '//button[@data-test="dialog-cancel-button"]',
          locateStrategy: 'xpath',
        },
        serviceURL: {
          selector: '//*[contains(@class, "url-input")]//input',
          locateStrategy: 'xpath',
        },
        serviceCode: {
          selector: '//*[contains(@class, "code-input")]//input',
          locateStrategy: 'xpath',
        },
        serviceType: {
          selector:
            '//div[@data-test="service-description-details-url-type-value"]',
          locateStrategy: 'xpath',
        },
        URLMessage: {
          selector:
            '//*[contains(@class, "validation-provider")]//*[contains(@class, "v-messages__message")]',
          locateStrategy: 'xpath',
        },
        codeMessage: {
          selector:
            '//*[contains(@class, "code-input")]//*[contains(@class, "v-messages__message")]',
          locateStrategy: 'xpath',
        },
        confirmDialogButton: {
          selector:
            '//button[@data-test="service-description-details-save-button"]',
          locateStrategy: 'xpath',
        },
        cancelDialogButton: {
          selector:
            '//button[@data-test="service-description-details-cancel-button"]',
          locateStrategy: 'xpath',
        },
      },
    },
    openApiServiceDetails: {
      selector: '//div[@data-test="service-description-details-dialog"]',
      locateStrategy: 'xpath',
      commands: [serviceDetailsCommands],
      elements: {
        serviceDetailsCloseButton: {
          selector:
            '//*[contains(@class, "cert-dialog-header")]//*[contains(@id, "close-x")]',
          locateStrategy: 'xpath',
        },
        deleteServiceButton: {
          selector:
            '//button[@data-test="service-description-details-delete-button"]',
          locateStrategy: 'xpath',
        },
        confirmDeleteButton: {
          selector: '//button[@data-test="dialog-save-button"]',
          locateStrategy: 'xpath',
        },
        cancelDeleteButton: {
          selector: '//button[@data-test="dialog-cancel-button"]',
          locateStrategy: 'xpath',
        },
        serviceURL: {
          selector: '//*[contains(@class, "url-input")]//input',
          locateStrategy: 'xpath',
        },
        serviceCode: {
          selector:
            '//*[contains(@class, "code-input")]//input[@name="code_field"]',
          locateStrategy: 'xpath',
        },
        serviceType: {
          selector:
            '//div[@data-test="service-description-details-url-type-value"]',
          locateStrategy: 'xpath',
        },
        URLMessage: {
          selector:
            '//*[contains(@class, "validation-provider")]//*[contains(@class, "v-messages__message")]',
          locateStrategy: 'xpath',
        },
        codeMessage: {
          selector:
            '//*[contains(@class, "code-input")]//*[contains(@class, "v-messages__message")]',
          locateStrategy: 'xpath',
        },
        confirmDialogButton: {
          selector:
            '//button[@data-test="service-description-details-save-button"]',
          locateStrategy: 'xpath',
        },
        cancelDialogButton: {
          selector:
            '//button[@data-test="service-description-details-cancel-button"]',
          locateStrategy: 'xpath',
        },
      },
    },
    wsdlOperationDetails: {
      selector:
        '//div[contains(@class, "base-full-width") and .//div[contains(@class, "apply-to-all-text")]]',
      locateStrategy: 'xpath',
      commands: [wsdlOperationCommands],
      elements: {
        parametersTab: {
          selector: '//*[@data-test="parameters"]',
          locateStrategy: 'xpath',
        },
        endpointsTab: {
          selector: '//*[@data-test="endpoints"]',
          locateStrategy: 'xpath',
        },
        urlApplyToAllCheckbox: {
          selector: '//input[@data-test="url-all"]/following-sibling::div',
          locateStrategy: 'xpath',
        },
        timeoutApplyToAllCheckbox: {
          selector: '//input[@data-test="timeout-all"]/following-sibling::div',
          locateStrategy: 'xpath',
        },
        verifyCertApplyToAllCheckbox: {
          selector: '//input[@data-test="ssl-auth-all"]/following-sibling::div',
          locateStrategy: 'xpath',
        },
        serviceURL: {
          selector: '//input[@data-test="service-url"]',
          locateStrategy: 'xpath',
        },
        timeout: {
          selector: '//input[@data-test="service-timeout"]',
          locateStrategy: 'xpath',
        },
        sslAuth: {
          selector: '//input[@data-test="ssl-auth"]',
          locateStrategy: 'xpath',
        },
        sslAuthClickarea: {
          selector: '//input[@data-test="ssl-auth"]/following-sibling::div',
          locateStrategy: 'xpath',
        },
        save2Button: {
          selector:
            '//button[@data-test="save-service-parameters"]/following-sibling::div',
          locateStrategy: 'xpath',
        },
        saveButton: {
          selector: '//button[@data-test="save-service-parameters"]',
          locateStrategy: 'xpath',
        },
        closeButton: {
          selector: '//button[@data-test="close"]',
          locateStrategy: 'xpath',
        },
        urlHelp: {
          selector:
            '//div[@data-test="service-parameters-service-url-label"]//i',
          locateStrategy: 'xpath',
        },
        timeoutHelp: {
          selector: '//div[@data-test="service-parameters-timeout-label"]//i',
          locateStrategy: 'xpath',
        },
        verifyCertHelp: {
          selector:
            '//div[@data-test="service-parameters-verify-tls-label"]//i',
          locateStrategy: 'xpath',
        },
        activeTooltip: {
          selector:
            '//div[contains(@class, "v-tooltip__content") and contains(@class,"menuable__content__active")]//span',
          locateStrategy: 'xpath',
        },
        addSubjectsButton: {
          selector: '//button[@data-test="show-add-subjects"]',
          locateStrategy: 'xpath',
        },
        removeAllButton: {
          selector: '//button[@data-test="remove-subjects"]',
          locateStrategy: 'xpath',
        },
      },
    },
    restOperationDetails: {
      selector:
        '//div[contains(@class, "base-full-width") and .//*[@data-test="parameters"]]',
      locateStrategy: 'xpath',
      commands: [wsdlOperationCommands],
      elements: {
        parametersTab: {
          selector: '//*[@data-test="parameters"]',
          locateStrategy: 'xpath',
        },
        endpointsTab: {
          selector: '//*[@data-test="endpoints"]',
          locateStrategy: 'xpath',
        },
        serviceURL: {
          selector: '//input[@data-test="service-url"]',
          locateStrategy: 'xpath',
        },
        timeout: {
          selector: '//input[@data-test="service-timeout"]',
          locateStrategy: 'xpath',
        },
        sslAuth: {
          selector: '//input[@data-test="ssl-auth"]',
          locateStrategy: 'xpath',
        },
        sslAuthClickarea: {
          selector: '//input[@data-test="ssl-auth"]/following-sibling::div',
          locateStrategy: 'xpath',
        },
        save2Button: {
          selector:
            '//button[@data-test="save-service-parameters"]/following-sibling::div',
          locateStrategy: 'xpath',
        },
        saveButton: {
          selector: '//button[@data-test="save-service-parameters"]',
          locateStrategy: 'xpath',
        },
        addButton: {
          selector: '//button[@data-test="endpoint-add"]',
          locateStrategy: 'xpath',
        },
        closeButton: {
          selector: '//button[@data-test="close"]',
          locateStrategy: 'xpath',
        },
        urlHelp: {
          selector:
            '//div[@data-test="service-parameters-service-url-label"]//i',
          locateStrategy: 'xpath',
        },
        timeoutHelp: {
          selector: '//div[@data-test="service-parameters-timeout-label"]//i',
          locateStrategy: 'xpath',
        },
        verifyCertHelp: {
          selector:
            '//div[@data-test="service-parameters-verify-tls-label"]//i',
          locateStrategy: 'xpath',
        },
        activeTooltip: {
          selector:
            '//div[contains(@class, "v-tooltip__content") and contains(@class,"menuable__content__active")]//span',
          locateStrategy: 'xpath',
        },
        addSubjectsButton: {
          selector: '//button[@data-test="show-add-subjects"]',
          locateStrategy: 'xpath',
        },
        removeAllButton: {
          selector: '//button[@data-test="remove-subjects"]',
          locateStrategy: 'xpath',
        },
      },
    },
    sslCheckFailDialog: {
      selector:
        '//*[@data-test="dialog-simple" and .//*[@data-test="dialog-title"]]',
      locateStrategy: 'xpath',
      commands: [sslCheckFailDialogCommands],
      elements: {
        continueButton: {
          selector: '//button[@data-test="dialog-save-button"]',
          locateStrategy: 'xpath',
        },
        cancelButton: {
          selector: '//button[@data-test="dialog-cancel-button"]',
          locateStrategy: 'xpath',
        },
      },
    },
    restServiceEndpoints: {
      selector:
        '//div[contains(@class, "base-full-width") and .//*[@data-test="endpoints"]]',
      locateStrategy: 'xpath',
      commands: [restEndpointCommands],
      elements: {
        parametersTab: {
          selector: '//*[@data-test="parameters"]',
          locateStrategy: 'xpath',
        },
        endpointsTab: {
          selector: '//*[@data-test="endpoints"]',
          locateStrategy: 'xpath',
        },
        addButton: {
          selector: '//button[@data-test="endpoint-add"]',
          locateStrategy: 'xpath',
        },
        closeButton: {
          selector:
            '//*[contains(@class, "cert-dialog-header")]//*[@data-test="close-x"]',
          locateStrategy: 'xpath',
        },
        editButton: {
          selector: '//button[@data-test="endpoint-edit"]',
          locateStrategy: 'xpath',
        },
        accessRightsButton: {
          selector: '//button[@data-test="endpoint-edit-accessrights"]',
          locateStrategy: 'xpath',
        },
      },
    },
    wsdlAddSubjectsPopup: addSubjectsPopup,
    addEndpointPopup: {
      selector:
        '//*[@data-test="dialog-simple" and .//*[@data-test="dialog-title"]]',
      locateStrategy: 'xpath',
      commands: [addEndpointCommands],
      elements: {
        addButton: {
          selector: '//button[@data-test="dialog-save-button"]',
          locateStrategy: 'xpath',
        },
        cancelButton: {
          selector: '//button[@data-test="dialog-cancel-button"]',
          locateStrategy: 'xpath',
        },
        requestPath: {
          selector: '//input[@data-test="endpoint-path"]',
          locateStrategy: 'xpath',
        },
        requestPathMessage: {
          selector:
            '//div[contains(@class, "dlg-edit-row") and .//*[@data-test="endpoint-path"]]//*[contains(@class, "v-messages__message")]',
          locateStrategy: 'xpath',
        },
        methodDropdown: {
          selector: '//input[@data-test="endpoint-method"]/parent::*',
          locateStrategy: 'xpath',
        },
      },
    },
    editEndpointPopup: {
      selector:
        '//div[contains(@class, "xrd-tab-max-width") and //div[@data-test="endpoint-details-dialog"]]',
      locateStrategy: 'xpath',
      commands: [addEndpointCommands],
      elements: {
        addButton: {
          selector: '//button[.//*[contains(text(), "Save")]]',
          locateStrategy: 'xpath',
        },
        cancelButton: {
          selector: '//button[.//*[contains(text(), "Cancel")]]',
          locateStrategy: 'xpath',
        },
        deleteButton: {
          selector: '//button[.//*[contains(text(), "Delete")]]',
          locateStrategy: 'xpath',
        },
        requestPath: {
          selector: '//input[@data-test="endpoint-path"]',
          locateStrategy: 'xpath',
        },
        requestPathMessage: {
          selector:
            '//div[contains(@class, "dlg-edit-row") and .//*[@data-test="endpoint-path"]]//*[contains(@class, "v-messages__message")]',
          locateStrategy: 'xpath',
        },
        methodDropdown: {
          selector: '//input[@data-test="endpoint-method"]/parent::*',
          locateStrategy: 'xpath',
        },
        deleteYesButton: {
          selector: '//button[@data-test="dialog-save-button"]',
          locateStrategy: 'xpath',
        },
        deleteCancelButton: {
          selector: '//button[@data-test="dialog-cancel-button"]',
          locateStrategy: 'xpath',
        },
      },
    },
    removeAccessRightPopup: {
      selector:
        '//div[contains(@class, "xrd-card") and //div[@data-test="dialog-simple"] and .//*[@data-test="dialog-title"]]',
      locateStrategy: 'xpath',
      commands: [confirmationDialogCommands],
      elements: {
        yesButton: {
          selector: '//button[@data-test="dialog-save-button"]',
          locateStrategy: 'xpath',
        },
        cancelButton: {
          selector: '//button[@data-test="dialog-cancel-button"]',
          locateStrategy: 'xpath',
        },
      },
    },
    removeAllAccessRightsPopup: {
      selector:
        '//div[contains(@class, "xrd-card") and //div[@data-test="dialog-simple"] and .//*[@data-test="dialog-title"]]',
      locateStrategy: 'xpath',
      commands: [confirmationDialogCommands],
      elements: {
        yesButton: {
          selector: '//button[@data-test="dialog-save-button"]',
          locateStrategy: 'xpath',
        },
        cancelButton: {
          selector: '//button[@data-test="dialog-cancel-button"]',
          locateStrategy: 'xpath',
        },
      },
    },
    deleteCertPopup: {
      selector:
        '//*[@data-test="dialog-simple" and .//*[@data-test="dialog-title" and //*[@data-test="dialog-content-text"]]]',
      locateStrategy: 'xpath',
      commands: [confirmationDialogCommands],
      elements: {
        yesButton: {
          selector: '//button[@data-test="dialog-save-button"]',
          locateStrategy: 'xpath',
        },
        cancelButton: {
          selector: '//button[@data-test="dialog-cancel-button"]',
          locateStrategy: 'xpath',
        },
      },
    },
    deleteCSRPopup: {
      selector:
        '//*[@data-test="dialog-simple" and .//*[@data-test="dialog-title"]]',
      locateStrategy: 'xpath',
      commands: [confirmationDialogCommands],
      elements: {
        yesButton: {
          selector: '//button[@data-test="dialog-save-button"]',
          locateStrategy: 'xpath',
        },
        cancelButton: {
          selector: '//button[@data-test="dialog-cancel-button"]',
          locateStrategy: 'xpath',
        },
      },
    },
  },
};
