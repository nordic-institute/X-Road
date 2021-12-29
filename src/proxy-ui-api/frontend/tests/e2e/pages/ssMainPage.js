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
const clientInfo = require('./tabs/clientsTabClientInfo');

const navigateCommands = {
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
  updateWSDLFileTo: function (newfile) {
    var sshScript;
    const { exec } = require('child_process');

    // remove protocol and port data from globals.testdata
    sshScript = `ssh ${this.api.globals.testdata
      .split(':')[1]
      .substring(2)} "cp ${this.api.globals.testfiles_path}/${newfile} ${
      this.api.globals.testfiles_path
    }/testserviceX.wsdl"`;

    exec(sshScript, (err, stdout, stderr) => {
      if (stderr) {
        console.log('SSH error, stderr: ' + stderr);
        console.log('SSH error, script: ' + sshScript);
      }
    });

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

var localGroupPopupCommands = {
  waitForDescription: function (value) {
    this.waitForValue('@localGroupDescription', value);
    return this;
  },
  modifyCode: function (code) {
    this.waitForNonEmpty('@localGroupCode');
    this.clearValue2('@localGroupCode');
    this.setValue('@localGroupCode', code);
    return this;
  },
  modifyDescription: function (description) {
    this.waitForNonEmpty('@localGroupDescription');
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

var serviceDetailsCommands = {
  closeServiceDetails: function () {
    this.click('@serviceDetailsCloseButton');
    return this;
  },
  deleteService: function () {
    this.click('@deleteServiceButton');
    return this;
  },
  initServiceUrl: function (url) {
    this.assert.value('@serviceURL', '');
    this.setValue('@serviceURL', url);
    return this;
  },
  modifyServiceUrl: function (url) {
    this.waitForNonEmpty('@serviceURL');
    this.clearValue2('@serviceURL');
    this.setValue('@serviceURL', url);
    return this;
  },
  initServiceCode: function (code) {
    this.assert.value('@serviceCode', '');
    this.clearValue2('@serviceCode');
    this.setValue('@serviceCode', code);
    return this;
  },
  modifyServiceCode: function (code) {
    this.waitForNonEmpty('@serviceCode');
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
  modifyUrl: function (url) {
    this.waitForNonEmpty('@serviceURL');
    this.clearValue2('@serviceURL');
    this.setValue('@serviceURL', url);
    return this;
  },
  modifyTimeout: function (timeout) {
    this.waitForNonEmpty('@timeout');
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
  // previous value must be empty
  initPath: function (path) {
    this.assert.valueContains('@requestPath', '');
    this.setValue('@requestPath', path);
    return this;
  },
  // previous value must be non-empty
  modifyPath: function (path) {
    this.waitForNonEmpty('@requestPath');
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
    clientsTab: '//a[@data-test="clients"]',
    keysTab: '//a[@data-test="keys"]',
    diagnosticsTab: '//a[@data-test="diagnostics"]',
    settingsTab: '//a[@data-test="settings"]',
    userMenuButton: '//button[@data-test="username-button"]',
    userMenuitemLogout: '//*[@data-test="logout-list-tile"]',
    logoutOKButton:
      '//div[contains(@class, "v-dialog")]//button[.//*[contains(text(), "Ok")]]',
    snackBarCloseButton: '//button[@data-test="close-snackbar"]',
    snackBarMessage: '//div[@data-test="success-snackbar"]',
    alertCloseButton: '//button[@data-test="close-alert"]',
    alertMessage:
      '//div[@data-test="contextual-alert"]//div[contains(@class, "row-wrapper")]/div',
    sessionExpiredPopupOkButton:
      '//button[@data-test="session-expired-ok-button"]',
  },
  sections: {
    clientsTab: clientsTab,
    keysTab: keysTab,
    diagnosticsTab: diagnosticsTab,
    settingsTab: settingsTab,
    clientInfo: clientInfo,
    certificatePopup: {
      selector: '//div[contains(@class, "certificate-details-wrapper")]',
      commands: [certificatePopupCommands],
      elements: {
        certificateInfoCloseButton: '//*[@data-test="close-x"]',
        deleteButton: '//button[.//*[contains(text(), "Delete")]]',
      },
    },
    certificateDetails: {
      selector: '//*[@data-test="certificate-details-dialog"]',
      commands: [certificatePopupCommands],
      elements: {
        certificateInfoCloseButton: '//*[@data-test="close-x"]',
        deleteButton: '//button[.//*[contains(text(), "Delete")]]',
      },
    },
    localGroupPopup: {
      selector:
        '//div[contains(@class, "xrd-tab-max-width") and .//div[contains(@class, "cert-hash") and @data-test="local-group-title"]]',
      commands: [localGroupPopupCommands],
      elements: {
        groupIdentifier: '//span[contains(@class, "identifier-wrap")]', // Title in the "xrd-sub-view-title" component
        localGroupAddMembersButton: '//button[@data-test="add-members-button"]',
        localGroupRemoveAllButton:
          '//button[@data-test="remove-all-members-button"]',
        localGroupDeleteButton:
          '//button[@data-test="delete-local-group-button"]',
        localGroupAddSelectedButton:
          '//button[.//*[contains(text(), "Add selected")]]',
        localGroupSearchButton: '//button[.//*[contains(text(), "Search")]]',
        localGroupCancelAddButton: '//button[.//*[contains(text(), "Cancel")]]',
        localGroupTestComCheckbox:
          '//tr[.//*[contains(text(), "TestCom")]]//*[contains(@class, "v-input--selection-controls__ripple")]',
        localGroupRemoveMemberButton:
          '//button[.//*[contains(text(), "Add group")]]',
        localGroupSearchWrap: '//div[contains(@class, "search-wrap")]',
        localGroupRemoveYesButton: '//button[@data-test="dialog-save-button"]',
        localGroupRemoveCancelButton:
          '//button[@data-test="dialog-cancel-button"]',
        localGroupTestComRemoveButton:
          '//tr[.//*[contains(text(), "TestCom")]]//button[.//*[contains(text(), "Remove")]]',
        localGroupTestGovRemoveButton:
          '//tr[.//*[contains(text(), "TestGov")]]//button[.//*[contains(text(), "Remove")]]',
        localGroupTestOrgRemoveButton:
          '//tr[.//*[contains(text(), "TestOrg")]]//button[.//*[contains(text(), "Remove")]]',
        localGroupDescription:
          '//input[@data-test="local-group-edit-description-input"]',
        localGroupPopupCloseButton: '//button[.//*[contains(text(), "Close")]]',
      },
    },
    servicesWarningPopup: {
      selector:
        '//div[contains(@class, "v-dialog") and .//*[contains(@class, "headline")]]',
      commands: [servicesWarningPopupCommands],
      elements: {
        warningContinueButton: '//button[.//*[contains(text(), "Continue")]]',
        warningCancelButton: '//button[.//*[contains(text(), "Cancel")]]',
        addedServices:
          '//div[contains(@class, "dlg-warning-header") and contains(text(), "Adding services:")]/following-sibling::div',
        deletedServices:
          '//div[contains(@class, "dlg-warning-header") and contains(text(), "Deleting services:")]/following-sibling::div',
      },
    },
    wsdlServiceDetails: {
      selector:
        '//div[@data-test="service-description-details-dialog" and .//div[@data-test="wsdl-service-description-details-dialog"]]',
      commands: [serviceDetailsCommands],
      elements: {
        serviceDetailsCloseButton:
          '//*[contains(@class, "cert-dialog-header")]//*[contains(@id, "close-x")]',
        deleteServiceButton:
          '//button[@data-test="service-description-details-delete-button"]',
        confirmDeleteButton: '//button[@data-test="dialog-save-button"]',
        cancelDeleteButton: '//button[@data-test="dialog-cancel-button"]',
        serviceURL: '//*[contains(@class, "url-input")]//input',
        URLMessage:
          '//*[contains(@class, "validation-provider")]//*[contains(@class, "v-messages__message")]',
        confirmDialogButton:
          '//button[@data-test="service-description-details-save-button"]',
        cancelDialogButton:
          '//button[@data-test="service-description-details-cancel-button"]',
      },
    },
    restServiceDetails: {
      selector:
        '//div[@data-test="service-description-details-dialog" and .//div[@data-test="rest-service-description-details-dialog"]]',
      commands: [serviceDetailsCommands],
      elements: {
        serviceDetailsCloseButton:
          '//*[contains(@class, "cert-dialog-header")]//*[contains(@id, "close-x")]',
        deleteServiceButton:
          '//button[@data-test="service-description-details-delete-button"]',
        confirmDeleteButton: '//button[@data-test="dialog-save-button"]',
        cancelDeleteButton: '//button[@data-test="dialog-cancel-button"]',
        serviceURL: '//*[contains(@class, "url-input")]//input',
        serviceCode: '//*[contains(@class, "code-input")]//input',
        serviceType:
          '//div[@data-test="service-description-details-url-type-value"]',
        URLMessage:
          '//*[contains(@class, "validation-provider")]//*[contains(@class, "v-messages__message")]',
        codeMessage:
          '//*[contains(@class, "code-input")]//*[contains(@class, "v-messages__message")]',
        confirmDialogButton:
          '//button[@data-test="service-description-details-save-button"]',
        cancelDialogButton:
          '//button[@data-test="service-description-details-cancel-button"]',
      },
    },
    openApiServiceDetails: {
      selector:
        '//div[@data-test="service-description-details-dialog" and .//div[@data-test="openapi-service-description-details-dialog"]]',
      commands: [serviceDetailsCommands],
      elements: {
        serviceDetailsCloseButton:
          '//*[contains(@class, "cert-dialog-header")]//*[contains(@id, "close-x")]',
        deleteServiceButton:
          '//button[@data-test="service-description-details-delete-button"]',
        confirmDeleteButton: '//button[@data-test="dialog-save-button"]',
        cancelDeleteButton: '//button[@data-test="dialog-cancel-button"]',
        serviceURL: '//*[contains(@class, "url-input")]//input',
        serviceCode:
          '//*[contains(@class, "code-input")]//input[@name="code_field"]',
        serviceType:
          '//div[@data-test="service-description-details-url-type-value"]',
        URLMessage:
          '//*[contains(@class, "validation-provider")]//*[contains(@class, "v-messages__message")]',
        codeMessage:
          '//*[contains(@class, "code-input")]//*[contains(@class, "v-messages__message")]',
        confirmDialogButton:
          '//button[@data-test="service-description-details-save-button"]',
        cancelDialogButton:
          '//button[@data-test="service-description-details-cancel-button"]',
      },
    },
    wsdlOperationDetails: {
      selector:
        '//div[contains(@class, "base-full-width") and .//div[contains(@class, "apply-to-all-text")]]',
      commands: [wsdlOperationCommands],
      elements: {
        parametersTab: '//*[@data-test="parameters"]',
        endpointsTab: '//*[@data-test="endpoints"]',
        urlApplyToAllCheckbox:
          '//input[@data-test="url-all"]/following-sibling::div',
        timeoutApplyToAllCheckbox:
          '//input[@data-test="timeout-all"]/following-sibling::div',
        verifyCertApplyToAllCheckbox:
          '//input[@data-test="ssl-auth-all"]/following-sibling::div',
        serviceURL: '//input[@data-test="service-url"]',
        timeout: '//input[@data-test="service-timeout"]',
        sslAuth: '//input[@data-test="ssl-auth"]',
        sslAuthClickarea:
          '//input[@data-test="ssl-auth"]/following-sibling::div',
        save2Button:
          '//button[@data-test="save-service-parameters"]/following-sibling::div',
        saveButton: '//button[@data-test="save-service-parameters"]',
        closeButton: '//button[@data-test="close"]',
        urlHelp: '//div[@data-test="service-parameters-service-url-label"]//i',
        timeoutHelp: '//div[@data-test="service-parameters-timeout-label"]//i',
        verifyCertHelp:
          '//div[@data-test="service-parameters-verify-tls-label"]//i',
        activeTooltip:
          '//div[contains(@class, "v-tooltip__content") and contains(@class,"menuable__content__active")]//span',
        addSubjectsButton: '//button[@data-test="show-add-subjects"]',
        removeAllButton: '//button[@data-test="remove-subjects"]',
      },
    },
    restOperationDetails: {
      selector:
        '//div[contains(@class, "base-full-width") and .//*[@data-test="parameters"]]',
      commands: [wsdlOperationCommands],
      elements: {
        parametersTab: '//*[@data-test="parameters"]',
        endpointsTab: '//*[@data-test="endpoints"]',
        serviceURL: '//input[@data-test="service-url"]',
        timeout: '//input[@data-test="service-timeout"]',
        sslAuth: '//input[@data-test="ssl-auth"]',
        sslAuthClickarea:
          '//input[@data-test="ssl-auth"]/following-sibling::div',
        save2Button:
          '//button[@data-test="save-service-parameters"]/following-sibling::div',
        saveButton: '//button[@data-test="save-service-parameters"]',
        addButton: '//button[@data-test="endpoint-add"]',
        closeButton: '//button[@data-test="close"]',
        urlHelp: '//div[@data-test="service-parameters-service-url-label"]//i',
        timeoutHelp: '//div[@data-test="service-parameters-timeout-label"]//i',
        verifyCertHelp:
          '//div[@data-test="service-parameters-verify-tls-label"]//i',
        activeTooltip:
          '//div[contains(@class, "v-tooltip__content") and contains(@class,"menuable__content__active")]//span',
        addSubjectsButton: '//button[@data-test="show-add-subjects"]',
        removeAllButton: '//button[@data-test="remove-subjects"]',
      },
    },
    sslCheckFailDialog: {
      selector:
        '//*[@data-test="dialog-simple" and .//*[@data-test="dialog-title"]]',
      commands: [sslCheckFailDialogCommands],
      elements: {
        continueButton: '//button[@data-test="dialog-save-button"]',
        cancelButton: '//button[@data-test="dialog-cancel-button"]',
      },
    },
    restServiceEndpoints: {
      selector:
        '//div[contains(@class, "base-full-width") and .//*[@data-test="endpoints"]]',
      commands: [restEndpointCommands],
      elements: {
        parametersTab: '//*[@data-test="parameters"]',
        endpointsTab: '//*[@data-test="endpoints"]',
        addButton: '//button[@data-test="endpoint-add"]',
        closeButton:
          '//*[contains(@class, "cert-dialog-header")]//*[@data-test="close-x"]',
        editButton: '//button[@data-test="endpoint-edit"]',
        accessRightsButton: '//button[@data-test="endpoint-edit-accessrights"]',
      },
    },
    wsdlAddSubjectsPopup: addSubjectsPopup,
    addEndpointPopup: {
      selector:
        '//*[@data-test="dialog-simple" and .//*[@data-test="dialog-title"]]',
      commands: [addEndpointCommands],
      elements: {
        addButton: '//button[@data-test="dialog-save-button"]',
        cancelButton: '//button[@data-test="dialog-cancel-button"]',
        requestPath: '//input[@data-test="endpoint-path"]',
        requestPathMessage:
          '//div[contains(@class, "dlg-edit-row") and .//*[@data-test="endpoint-path"]]//*[contains(@class, "v-messages__message")]',
        methodDropdown: '//input[@data-test="endpoint-method"]/parent::*',
      },
    },
    editEndpointPopup: {
      selector:
        '//div[contains(@class, "xrd-tab-max-width") and //div[@data-test="endpoint-details-dialog"]]',
      commands: [addEndpointCommands],
      elements: {
        addButton: '//button[.//*[contains(text(), "Save")]]',
        cancelButton: '//button[.//*[contains(text(), "Cancel")]]',
        deleteButton: '//button[.//*[contains(text(), "Delete")]]',
        requestPath: '//input[@data-test="endpoint-path"]',
        requestPathMessage:
          '//div[contains(@class, "dlg-edit-row") and .//*[@data-test="endpoint-path"]]//*[contains(@class, "v-messages__message")]',
        methodDropdown: '//input[@data-test="endpoint-method"]/parent::*',
        deleteYesButton: '//button[@data-test="dialog-save-button"]',
        deleteCancelButton: '//button[@data-test="dialog-cancel-button"]',
      },
    },
    removeAccessRightPopup: {
      selector:
        '//div[contains(@class, "xrd-card") and //div[@data-test="dialog-simple"] and .//*[@data-test="dialog-title"]]',
      commands: [confirmationDialogCommands],
      elements: {
        yesButton: '//button[@data-test="dialog-save-button"]',
        cancelButton: '//button[@data-test="dialog-cancel-button"]',
      },
    },
    removeAllAccessRightsPopup: {
      selector:
        '//div[contains(@class, "xrd-card") and //div[@data-test="dialog-simple"] and .//*[@data-test="dialog-title"]]',
      commands: [confirmationDialogCommands],
      elements: {
        yesButton: '//button[@data-test="dialog-save-button"]',
        cancelButton: '//button[@data-test="dialog-cancel-button"]',
      },
    },
    deleteCertPopup: {
      selector:
        '//*[@data-test="dialog-simple" and .//*[@data-test="dialog-title" and //*[@data-test="dialog-content-text"]]]',
      commands: [confirmationDialogCommands],
      elements: {
        yesButton: '//button[@data-test="dialog-save-button"]',
        cancelButton: '//button[@data-test="dialog-cancel-button"]',
      },
    },
    deleteCSRPopup: {
      selector:
        '//*[@data-test="dialog-simple" and .//*[@data-test="dialog-title"]]',
      commands: [confirmationDialogCommands],
      elements: {
        yesButton: '//button[@data-test="dialog-save-button"]',
        cancelButton: '//button[@data-test="dialog-cancel-button"]',
      },
    },
  },
};
