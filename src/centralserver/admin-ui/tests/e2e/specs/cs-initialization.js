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
const { User } = require('../constants');
module.exports = {
  tags: ['cs', 'initialization'],
  before(browser) {
    // FUTURE STUDY: HOw to ensure we use uninitalized CS for this test?
    login = browser.page.csLoginPage();
    initialization = browser.page.csInitializationPage();
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
      .navigateAndMakeTestable()
      .waitForElementVisible('@initializationView')
      .assert.visible('@initializationPhaseId');
  },
  'Central server PIN repeat field shows check-mark only when it matches with PIN prompt':
    (browser) => {
      initialization
        .navigateAndMakeTestable()
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
};
