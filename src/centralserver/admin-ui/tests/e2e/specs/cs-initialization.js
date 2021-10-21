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
let login;
let initialization;
let members;
const { User } = require('../constants');

const STRONG_PIN = 'Valid_Pin_11';
const WEAK_PIN = '1';
const VALID_INSTANCE = 'VALID_INSTANCE';
const INVALID_INSTANCE = 'INVALID&&::INSTANCE';
const VALID_SERVER_ADDRESS = 'valid.example.org';
const INVALID_SERVER_ADDRESS = 'invalid...address...fo';
// NOTE: update according to localization/en.json (or dig from there?)
const INSTANCE_INVALID_FIELD_NOTE =
  'Use valid instance identifier characters only';
const ADDRESS_INVALID_FIELD_NOTE =
  'Valid IP address or fully qualified domain name needed';
module.exports = {
  tags: ['cs', 'initialization'],
  before(browser) {
    login = browser.page.csLoginPage();
    initialization = browser.page.csInitializationPage();
    members = browser.page.csMembersPage();
  },
  beforeEach() {
    login.navigateAndMakeTestable();
    login.signInUser();
    initialization.navigateAndMakeTestable();
  },
  after(browser) {
    browser.end();
  },
  'Central server is not initialized': (browser) => {
    initialization
      .waitForElementVisible('@initializationView')
      .assert.visible('@initializationPhaseId');
  },
  'Central server PIN repeat field shows check-mark only when it matches with PIN prompt':
    (browser) => {
      initialization
        .waitForElementVisible('@initializationView')
        .waitForElementVisible('@initializationPhaseId')
        .verify.not.elementPresent('@confirmPinOKIcon')
        .assert.elementPresent('@pinInput')
        .initPin('1111')
        .assert.elementPresent('@confirmPinInput')
        .initConfirmPin('1111')
        .verify.elementPresent('@confirmPinOKIcon')
        .modifyConfirmPin('111')
        .verify.not.elementPresent('@confirmPinOKIcon');
    },
  'Submit enabled only when all fields are filled': (browser) => {
    initialization
      .waitForElementVisible('@initializationView')
      .waitForElementVisible('@initializationPhaseId')
      .waitForElementVisible('@submitButton')
      .verify.not.enabled('@submitButton')
      .initInstanceId(VALID_INSTANCE)
      .initServerAddress(VALID_SERVER_ADDRESS)
      .initPin(STRONG_PIN)
      .initConfirmPin(STRONG_PIN)
      .verify.enabled('@submitButton')
      .modifyConfirmPin(STRONG_PIN.concat('err'))
      .verify.not.enabled('@submitButton')
      .modifyConfirmPin(STRONG_PIN)
      .verify.enabled('@submitButton');
  },
  'Too weak pin causes error note after submit': (browser) => {
    initialization
      .waitForElementVisible('@initializationPhaseId')
      .waitForElementVisible('@submitButton')
      .initInstanceId(VALID_INSTANCE)
      .initServerAddress(VALID_SERVER_ADDRESS)
      .initPin(WEAK_PIN)
      .initConfirmPin(WEAK_PIN)
      .verify.enabled('@submitButton')
      .click('@submitButton')
      .waitForElementVisible('@contextualAlertsNote');
    // To be updated after XRDDEV-1813
    // .verify.containsText('@contextualAlertsNote', 'weak');
  },
  'Invalid address & instance id error': (browser) => {
    initialization
      .waitForElementVisible('@initializationPhaseId')
      .waitForElementVisible('@submitButton')
      .initInstanceId(INVALID_INSTANCE)
      .initServerAddress(INVALID_SERVER_ADDRESS)
      .initPin(STRONG_PIN)
      .initConfirmPin(STRONG_PIN)
      .verify.enabled('@submitButton')
      .click('@submitButton')
      .waitForElementVisible('@contextualAlertsNote')
      .verify.containsText('@contextualAlertsNote', 'Validation failure')
      .verify.containsText(
        '@instanceIdentifierValidation',
        INSTANCE_INVALID_FIELD_NOTE,
      )
      .verify.containsText(
        '@serverAddressValidation',
        ADDRESS_INVALID_FIELD_NOTE,
      );
  },
  'Success message on members page shown after successfull initialisation':
    async (browser) => {
      await initialization
        .waitForElementVisible('@initializationView')
        .waitForElementVisible('@initializationPhaseId')
        .waitForElementVisible('@submitButton')
        .initInstanceId(VALID_INSTANCE)
        .initServerAddress(VALID_SERVER_ADDRESS)
        .initPin(STRONG_PIN)
        .initConfirmPin(STRONG_PIN)
        .verify.enabled('@submitButton')
        .click('@submitButton');
      await members
        .waitForElementVisible('@membersView')
        .verify.visible('@initNotificationNote');
    },
};
