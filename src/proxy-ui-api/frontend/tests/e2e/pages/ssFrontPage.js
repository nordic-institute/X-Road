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

var loginCommands = {
  clearUsername: function () {
    this.clearValue2('@usernameInput');
    return this;
  },
  clearPassword: function () {
    this.clearValue2('@passwordInput');
    return this;
  },
  enterUsername: function (username) {
    this.setValue('@usernameInput', username);
    return this;
  },
  enterPassword: function (password) {
    this.setValue('@passwordInput', password);
    return this;
  },
  signin: function () {
    this.click('@loginButton');
    return this;
  },
  loginErrorMessageIsShown: function () {
    this.assert.visible('@LoginError');
    return this;
  },
  navigateAndMakeTestable: function () {
    this.logMessage('navigateAndMakeTestable()');
    this.navigate();
    this.waitForElementVisible('//*[@id="app"]');
    this.makeTestable();
    this.logMessage('navigateAndMakeTestable() done');
    return this;
  },
  signinDefaultUser: function () {
    this.clearValue2('@usernameInput');
    this.clearValue2('@passwordInput');
    this.setValue('@usernameInput', this.api.globals.login_usr);
    this.setValue('@passwordInput', this.api.globals.login_pwd);
    this.click('@loginButton');
    // wait for login to complete, and disable transitions
    this.api.page.ssMainPage().verifyCurrentUser(this.api.globals.login_usr);
    this.makeTestable();
    return this;
  },
};

module.exports = {
  url: process.env.VUE_DEV_SERVER_URL,
  commands: [loginCommands],
  elements: {
    usernameInput: '//input[@id="username"]',
    passwordInput: '//input[@id="password"]',
    loginButton: '//button[@id="submit-button"]',
    LoginError: '//button[@id="submit-button"]',
  },
};
