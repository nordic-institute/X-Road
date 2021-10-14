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

const createInitField = (fieldSelector) => {
  return (value) => {
    this.assert.value(fieldSelector, '');
    this.setValue(fieldSelector, value);
    return this;
  };
};
const createModifyField = (fieldSelector) => {
  return (value) => {
    this.waitForNonEmpty(fieldSelector);
    this.clearValue2(fieldSelector);
    this.setValue(fieldSelector, value);
  };
};
const initializationCommands = {
  initializationViewIsVisible() {
    this.assert.visible('@initializationView');
    return this;
  },
  initServerAddress: createInitField('@serverAddressInput'),
  modifyServerAddress: createModifyField('@serverAddressInput'),
  initInstanceId: createInitField('@instanceIdentifierInput'),
  modifyInstanceId: createModifyField('@instanceIdentifierInput'),
  initPin: createInitField('@pinInput'),
  modifyPin: createModifyField('@pinInput'),
  initConfirmPin: createInitField('@confirmPinInput'),
  modifyConfirmPin: createModifyField('@confirmPinInput'),
  navigateAndMakeTestable: function () {
    this.logMessage('navigateAndMakeTestable()');
    this.navigate();
    this.waitForElementVisible('//*[@id="app"]');
    this.makeTestable();
    this.logMessage('navigateAndMakeTestable() done');
    return this;
  },

  verifyCurrentUser: function (user) {
    this.api.assert.containsText(this.elements.userMenuButton, user);
    return this;
  },
};

module.exports = {
  url: `${process.env.VUE_DEV_SERVER_URL}/#/init`,
  commands: [initializationCommands],
  elements: {
    initializationView: {
      selector: '//div[@data-test="central-server-initialization-page-title"]',
      locateStrategy: 'xpath',
    },
    initializationPhaseId: {
      selector: '//div[@data-test="app-toolbar-server-init-phase-id"]',
      locateStrategy: 'xpath',
    },
    instanceIdentifierInput: {
      selector: '//input[@data-test="instance-identifier--input"]',
      locateStrategy: 'xpath',
    },
    serverAddressInput: {
      selector: '//input[@data-test="address--input"]',
      locateStrategy: 'xpath',
    },
    pinInput: {
      selector: '//input[@data-test="pin--input"]',
      locateStrategy: 'xpath',
    },
    confirmPinInput: {
      selector: '//input[@data-test="confirm-pin--input"]',
      locateStrategy: 'xpath',
    },
    confirmPinOKIcon: {
      selector: '//*[@data-test="confirm-pin-append-input-icon"]',
      locateStrategy: 'xpath',
    },
    userMenuButton: {
      selector: '//button[@data-test="username-button"]',
      locateStrategy: 'xpath',
    },
    submitButton: {
      selector: '//button[@data-test="submit-button"]',
      locateStrategy: 'xpath',
    },
  },
};
