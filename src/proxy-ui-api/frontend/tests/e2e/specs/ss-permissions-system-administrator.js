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

module.exports = {
  tags: ['ss', 'xroad-system-administrator', 'permissions'],
  before: (browser) => console.log('Test is starting...'),
  'Security server system administrator role': (browser) => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const keysTab = mainPage.section.keysTab;
    const diagnosticsTab = mainPage.section.diagnosticsTab;
    const settingsTab = mainPage.section.settingsTab;

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage
      .clearUsername()
      .clearPassword()
      .enterUsername(browser.globals.login_system_administrator)
      .enterPassword(browser.globals.login_pwd)
      .signin();

    // Check username
    browser.waitForElementVisible(
      '//div[contains(@class,"auth-container") and contains(text(),"' +
        browser.globals.login_system_administrator +
        '")]',
    );

    // xroad-system-administrator should be in keys and certs view and not see clients tab
    browser.waitForElementVisible(keysTab);
    browser.waitForElementNotPresent(clientsTab);
    keysTab.openSignAndAuthKeys();
    keysTab.openAPIKeys();
    keysTab.openSecurityServerTLSKey();

    // xroad-system-administrator should be able to open diagnostics tab
    mainPage.openDiagnosticsTab();
    browser.waitForElementVisible(
      '//div[contains(@class, "v-tabs-bar__content")]//a[text()="Diagnostics"]',
    );

    // xroad-system-administrator should be able to open settings tab
    mainPage.openSettingsTab();
    browser.waitForElementVisible(
      '//div[contains(@class, "v-tabs-bar__content")]//a[text()="Settings"]',
    );
    settingsTab.openSystemParameters();
    settingsTab.openBackupAndRestore();

    browser.end();
  },
};
