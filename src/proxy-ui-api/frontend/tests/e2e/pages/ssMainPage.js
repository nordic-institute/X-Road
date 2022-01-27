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
const {
  certificatePopupCommands,
  certificatePopupElements,
} = require('./popups/certificatePopup');
const localGroupPopup = require('./popups/localGroupPopup');
const simpleSaveCancelPopup = require('./popups/simpleSaveCancelPopup');
const servicesWarningPopup = require('./popups/servicesWarningPopup');
const {
  serviceDetailsCommands,
  serviceDetailsElements,
} = require('./services/serviceDetails');
const {
  operationDetailsElements,
  operationDetailsCommands,
} = require('./services/operationDetails');
const {
  restEndPointPopupCommands,
  addRestEndPointPopupElements,
  editRestEndPointPopupElements,
} = require('./endpoints/restEndPointPopup');
const restServiceEndpointsPage = require('./endpoints/restServiceEndpointPage');

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
    sshScript = `ssh -o StrictHostKeyChecking=no ${this.api.globals.testdata
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
        certificateHash: '//*[@data-test="cert-hash-value"]',
      },
    },
    certificateDetails: {
      selector: '//*[@data-test="certificate-details-dialog"]',
      commands: [certificatePopupCommands],
      elements: certificatePopupElements,
    },
    localGroupPopup: localGroupPopup,
    servicesWarningPopup: servicesWarningPopup,
    wsdlServiceDetails: {
      selector:
        '//div[@data-test="service-description-details-dialog" and .//div[@data-test="wsdl-service-description-details-dialog"]]',
      commands: [serviceDetailsCommands],
      elements: serviceDetailsElements,
    },
    restServiceDetails: {
      selector:
        '//div[@data-test="service-description-details-dialog" and .//div[@data-test="rest-service-description-details-dialog"]]',
      commands: [serviceDetailsCommands],
      elements: serviceDetailsElements,
    },
    openApiServiceDetails: {
      selector:
        '//div[@data-test="service-description-details-dialog" and .//div[@data-test="openapi-service-description-details-dialog"]]',
      commands: [serviceDetailsCommands],
      elements: serviceDetailsElements,
    },
    wsdlOperationDetails: {
      selector:
        '//div[contains(@class, "base-full-width") and .//div[contains(@class, "apply-to-all-text")]]',
      commands: [operationDetailsCommands],
      elements: operationDetailsElements,
    },
    restOperationDetails: {
      selector:
        '//div[contains(@class, "base-full-width") and .//*[@data-test="parameters"]]',
      commands: [operationDetailsCommands],
      elements: operationDetailsElements,
    },
    sslCheckFailDialog: simpleSaveCancelPopup,
    restServiceEndpoints: restServiceEndpointsPage,
    wsdlAddSubjectsPopup: addSubjectsPopup,
    addEndpointPopup: {
      selector:
        '//*[@data-test="dialog-simple" and .//*[@data-test="dialog-title"] and .//input[@data-test="endpoint-path"]]',
      commands: [restEndPointPopupCommands],
      elements: addRestEndPointPopupElements,
    },
    editEndpointPopup: {
      selector:
        '//div[contains(@class, "xrd-tab-max-width") and //div[@data-test="endpoint-details-dialog"]]',
      commands: [restEndPointPopupCommands],
      elements: editRestEndPointPopupElements,
    },
    removeAccessRightPopup: simpleSaveCancelPopup,
    removeAllAccessRightsPopup: simpleSaveCancelPopup,
    deleteCertPopup: simpleSaveCancelPopup,
    deleteCSRPopup: simpleSaveCancelPopup,
  },
};
