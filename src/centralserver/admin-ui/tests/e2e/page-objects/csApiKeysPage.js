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

const {
  navigateAndMakeTestable,
  dialogCancelButton,
  dialogSaveButton,
  dialogTitle,
  snackBarCloseButton,
} = require('./csCommonObjectsPage');
const apiKeysCommands = {
  apiKeysViewIsVisible() {
    this.assert.visible('@apiKeysView');
    return this;
  },
  navigateAndMakeTestable,
};

module.exports = {
  url: `${process.env.VUE_DEV_SERVER_URL}/#/settings/apikeys`,
  commands: [apiKeysCommands],
  elements: {
    apiKeysView: {
      selector: '//div[@data-test="api-keys-view"]',
      locateStrategy: 'xpath',
    },
    apiKeysWizardView: {
      selector: '//div[@data-test="create-api-key-stepper-view"]',
      locateStrategy: 'xpath',
    },
    apiKeysCreatedKeyId: {
      selector: '//div[@data-test="created-apikey-id"]',
      locateStrategy: 'xpath',
    },
    apiKeysViewCreateKeyButton: {
      selector: '//button[@data-test="api-key-create-key-button"]',
      locateStrategy: 'xpath',
    },
    apiKeysViewWizardCreateKeyButton: {
      selector: '//button[@data-test="create-key-button"]',
      locateStrategy: 'xpath',
    },
    apiKeysViewWizardPreviousButton: {
      selector: '//button[@data-test="previous-button"]',
      locateStrategy: 'xpath',
    },
    apiKeysViewWizardNextButton: {
      selector: '//button[@data-test="next-button"]',
      locateStrategy: 'xpath',
    },
    apiKeysViewWizardFinishButton: {
      selector: '//button[@data-test="finish-button"]',
      locateStrategy: 'xpath',
    },

    apiKeysCheckboxRoleRegistrationOfficerButton: {
      selector:
        '//input[@data-test="role-XROAD_REGISTRATION_OFFICER-checkbox"]/following-sibling::div',
      locateStrategy: 'xpath',
    },
    apiKeysCheckboxRoleSecurityOfficerButton: {
      selector:
        '//input[@data-test="role-XROAD_SECURITY_OFFICER-checkbox"]/following-sibling::div',
      locateStrategy: 'xpath',
    },
    apiKeysCheckboxRoleSystemAdministratorButton: {
      selector:
        '//input[@data-test="role-XROAD_SYSTEM_ADMINISTRATOR-checkbox"]/following-sibling::div',
      locateStrategy: 'xpath',
    },
    dialogCancelButton,
    dialogTitle,
    dialogSaveButton,
    snackBarCloseButton,
  },
};
