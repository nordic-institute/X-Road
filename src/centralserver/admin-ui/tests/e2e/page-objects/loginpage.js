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

const { User } = require('../constants/');
const loginCommands = {
  clearUsername() {
    this.clearValue('@usernameInput');
    return this;
  },
  clearPassword() {
    this.clearValue('@passwordInput');
    return this;
  },
  enterUsername(username) {
    this.setValue('@usernameInput', username);
    return this;
  },
  enterPassword(password) {
    this.setValue('@passwordInput', password);
    return this;
  },
  signIn() {
    this.click('@loginButton');
    return this;
  },
  loginErrorMessageIsShown() {
    this.assert.visible('@loginError');
    return this;
  },
  signInUser(role) {
    switch (role) {
      case User.REGISTRATION_OFFICER:
        return this.api.LoginCommand(
          this.api.globals.login_registration_officer,
          this.api.globals.login_pwd,
        );
      case User.SECURITY_OFFICER:
        return this.api.LoginCommand(
          this.api.globals.login_security_officer,
          this.api.globals.login_pwd,
        );
      case User.ADMIN:
        return this.api.LoginCommand();
      default:
        throw Error('Login role not provided');
    }
  },
};

module.exports = {
  url: `${process.env.VUE_DEV_SERVER_URL}/#/login`,
  commands: [loginCommands],
  elements: {
    usernameInput: {
      selector: '//input[@data-test="login-username-input"]',
      locateStrategy: 'xpath',
    },
    passwordInput: {
      selector: '//input[@data-test="login-password-input"]',
      locateStrategy: 'xpath',
    },
    loginButton: {
      selector: '//button[@data-test="login-button"]',
      locateStrategy: 'xpath',
    },
    loginError: {
      selector: '//div[@data-test="contextual-alert"]',
      locateStrategy: 'xpath',
    },
  },
};
