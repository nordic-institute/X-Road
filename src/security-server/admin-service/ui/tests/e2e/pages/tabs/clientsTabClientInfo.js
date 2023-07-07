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

var clientInternalServersCommands = {
  addCert: function (certfile) {
    this.api.setValue(
      '//input[@type="file"]',
      require('path').resolve(__dirname + '/..' + certfile),
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
  initCode: function (code) {
    this.assert.value('@groupCode', '');
    this.clearValue2('@groupCode');
    this.setValue('@groupCode', code);
    return this;
  },
  initDescription: function (description) {
    this.assert.value('@groupDescription', '');
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
      `(//*[contains(@data-test, "local-groups-table")]//tr)[${row}]//*[contains(text(),"${code}")]`,
    );
    return this;
  },
  openDetails: function (code) {
    this.api.click(
      `${this.selector}//*[contains(@data-test, "local-groups-table")]//*[contains(text(),"${code}")]`,
    );
    return this;
  },
};

var clientServicesCommands = {
  groupMemberTableContains: function (expectedText) {
    this.api.waitForElementVisible(`//table[contains(@class, "group-members-table")]//td[contains(text(), "${expectedText}")]`);
    return this;
  },
  errorMessageIsShown: function (expectedMessage) {
    // Verifies that error message element is shown, if argument is given, function polls that text from page.
    this.api.waitForElementVisible('//div[contains(@class, "v-messages__message")]');
    if (expectedMessage) {
      this.api.waitForElementVisible(`//div[contains(@class, "v-messages__message") and contains(text(), "${expectedMessage}")]`)
    }
    return this;
  },
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
  initServiceUrl: function (url) {
    this.logMessage('initServiceUrl -> ' + url);
    this.assert.value('@newServiceUrl', '');
    this.setValue('@newServiceUrl', url);
    return this;
  },
  modifyServiceUrl: function (url) {
    this.waitForNonEmpty('@newServiceUrl');
    this.clearValue2('@newServiceUrl');
    this.setValue('@newServiceUrl', url);
    return this;
  },
  initServiceCode: function (code) {
    this.assert.value('@newServiceCode', '');
    this.clearValue2('@newServiceCode');
    this.setValue('@newServiceCode', code);
    return this;
  },
  modifyServiceCode: function (code) {
    this.waitForNonEmpty('@newServiceCode');
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
  initDisableNotice: function (notice) {
    this.assert.value('@disableNotice', '');
    this.clearValue2('@disableNotice');
    this.setValue('@disableNotice', notice);
    return this;
  },
  modifyDisableNotice: function (notice) {
    this.waitForNonEmpty('@disableNotice');
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
        `//td[@data-test="service-link" and contains(text(),"${op}")]`,
    );
    return this;
  },
  verifyServiceSSL: function (service, status) {
    this.api.waitForElementVisible(
      `//tr[.//td[@data-test="service-link" and contains(text(), "${service}")]]//*[contains(@class, "mdi-lock") and contains(@style, "${status}")]`,
    );
    return this;
  },
  verifyServiceURL: function (service, url) {
    this.api.waitForElementVisible(
      `//tr[.//td[@data-test="service-link" and contains(text(), "${service}")]]//*[contains(text(), "${url}")]`,
    );
    return this;
  },
};

const clientTabClientInfo = {
  selector:
    '//div[contains(@class, "v-main__wrap") and .//*[@data-test="clients" and @aria-selected="true"]]',
  commands: [clientInfoCommands],
  elements: {
    detailsTab:
      '//div[contains(@class, "v-tabs-bar__content")]//a[contains(@class, "v-tab") and contains(text(), "Details")]',
    serviceClientsTab:
      '//div[contains(@class, "v-tabs-bar__content")]//a[contains(@class, "v-tab") and contains(text(), "Service clients")]',

    servicesTab:
      '//div[contains(@class, "v-tabs-bar__content")]//a[contains(@class, "v-tab") and contains(text(), "Services")]',

    internalServersTab:
      '//div[contains(@class, "v-tabs-bar__content")]//a[contains(@class, "v-tab") and contains(text(), "Internal servers")]',

    localGroupsTab:
      '//div[contains(@class, "v-tabs-bar__content")]//a[contains(@class, "v-tab") and contains(text(), "Local groups")]',
  },
  sections: {
    details: {
      selector:
        '//div[contains(@class, "base-full-width") and .//*[contains(@class, "v-tab--active") and contains(text(),"Details")]]//div[contains(@class, "xrd-view-common")]',
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
      commands: [clientInternalServersCommands],
      elements: {
        addButton: '//button[.//*[contains(text(), "Add")]]',
        exportButton: '//button[.//*[contains(text(), "Export")]]',
        connectionTypeMenu: '//div[contains(@class, "v-select__selection")]',
        tlsCertificate:
          '//table[contains(@class, "server-certificates")]//span[contains(@class, "certificate-link")]',
      },
    },
    localGroups: {
      selector:
        '//div[contains(@class, "base-full-width") and .//*[contains(@class, "v-tab--active") and contains(text(),"Local groups")]]//div[contains(@class, "xrd-view-common")]',
      commands: [clientLocalGroupsCommands],
      elements: {
        filterInput: '//input',
        addGroupButton: '//button[@data-test="add-local-group-button"]',
        confirmAddButton: '//button[@data-test="dialog-save-button"]',
        cancelAddButton: '//button[@data-test="dialog-cancel-button"]',
        groupCode: '//input[@data-test="add-local-group-code-input"]',
        groupDescription:
          '//input[@data-test="add-local-group-description-input"]',
        groupCodeCellAbb:
          '//*[contains(@data-test, "local-groups-table")]//*[contains(text(),"abb")]',
        groupCodeCellBac:
          '//*[contains(@data-test, "local-groups-table")]//*[contains(text(),"bac")]',
        groupCodeCellCbb:
          '//*[contains(@data-test, "local-groups-table")]//*[contains(text(),"cbb")]',
      },
    },
    services: {
      selector:
        '//div[contains(@class, "base-full-width") and .//*[contains(@class, "v-tab--active") and contains(text(),"Services")]]//div[contains(@class, "xrd-view-common")]',
      commands: [clientServicesCommands],
      elements: {
        serviceURLBoxErrorMessage: '//div[contains(@class, "v-messages__message")]',
        addWSDLButton: '//button[@data-test="add-wsdl-button"]',
        addRESTButton: '//button[@data-test="add-rest-button"]',
        filterServices: '//input[@data-test="search-service"]',
        addDialogTitle: '//input[@data-test="dialog-title"]',
        newServiceUrl: '//input[contains(@name, "serviceUrl")]',
        newServiceCode: '//input[contains(@name, "serviceCode")]',
        serviceUrlMessage: '//div[contains(@class, "v-messages__message")]',
        serviceCodeMessage:
          '//div[contains(@class, "v-input") and .//input[@name="serviceCode"]]//div[contains(@class, "v-messages__message")]',
        confirmAddServiceButton: '//button[@data-test="dialog-save-button"]',
        cancelAddServiceButton: '//button[@data-test="dialog-cancel-button"]',
        RESTPathRadioButton: '//input[@name="REST"]',
        RESTPathRadioButtonClickArea:
          '//input[@name="REST"]/following-sibling::div',
        OpenApiRadioButton: '//input[@name="OPENAPI3"]',
        OpenApiRadioButtonClickArea:
          '//input[@name="OPENAPI3"]/following-sibling::div',
        serviceDescription: '//*[@data-test="service-description-header"]',
        serviceExpandButton:
          '//*[@data-test="service-description-accordion"]//button',
        refreshButton: '//button[@data-test="refresh-button"]',
        refreshTimestamp: '//*[contains(@class, "refresh-time")]',
        serviceDetailsDeleteButton:
          '//button[.//*[contains(text(), "Delete")]]',
        serviceDetailsSaveButton: '//button[.//*[contains(text(), "Save")]]',
        serviceDetailsCancelButton:
          '//button[.//*[contains(text(), "Cancel")]]',
        serviceEnableToggle:
          '//*[contains(@class, "v-input--selection-controls__ripple")]',
        confirmDisableButton: '//button[@data-test="dialog-save-button"]',
        cancelDisableButton: '//button[@data-test="dialog-cancel-button"]',
        disableNotice:
          '//div[contains(@class, "dlg-edit-row") and .//*[contains(@class, "dlg-row-title")]]//input',
        operationUrl: '//td[@data-test="service-url"]',
      },
    },
  },
};

module.exports = clientTabClientInfo;
