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
const login = (browser) => {
  const frontPage = browser.page.loginpage();

  frontPage
    .clearUsername()
    .clearPassword()
    .enterUsername(browser.globals.login_usr)
    .enterPassword(browser.globals.login_pwd)
    .signin();
};

module.exports = {
  tags: ['cs', 'login'],
  'Security server failed login': (browser) => {
    const frontPage = browser.page.loginpage();

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter invalid credentials
    frontPage
      .enterUsername(browser.globals.login_wrong_usr)
      .enterPassword(browser.globals.login_wrong_pwd)
      .signin();

    // Verify there's an error message
    browser.waitForElementVisible(
      '//div[contains(@class, "v-messages__message")]',
    );

    browser.end();
  },
  'Security server passed login': (browser) => {
    const frontPage = browser.page.loginpage();

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    login(browser);

    // Verify successful login
    browser.waitForElementVisible('//div[contains(@class, "server-name")]');

    // Test refresh
    browser
      .refresh()
      .waitForElementVisible('//div[contains(@class, "server-name")]');

    browser.end();
  },
};
