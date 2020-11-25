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
  tags: ['ss', 'xroad-securityserver-observer', 'permissions'],
  'Security server observer role': (browser) => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const keysTab = mainPage.section.keysTab;
    const clientsTab = mainPage.section.clientsTab;
    const diagnosticsTab = mainPage.section.diagnosticsTab;
    const settingsTab = mainPage.section.settingsTab;
    const tokenName = mainPage.section.keysTab.elements.tokenName;
    const searchField = mainPage.section.clientsTab.elements.searchField;
    const createAPIKeyButton = mainPage.section.keysTab.elements.createAPIKeyButton;
    const generateKeyButton = mainPage.section.keysTab.elements.generateKeyButton;
    const globalConfiguration = mainPage.section.diagnosticsTab.elements.globalConfiguration;
    const anchorDownloadButton = mainPage.section.settingsTab.elements.anchorDownloadButton;
    const backupAndRestoreTab = mainPage.section.settingsTab.elements.backupAndRestoreTab;

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage
      .clearUsername()
      .clearPassword()
      .enterUsername(browser.globals.login_securityserver_observer)
      .enterPassword(browser.globals.login_pwd)
      .signin();

    // Check username
    browser.waitForElementVisible(
      '//div[contains(@class,"auth-container") and contains(text(),"' +
        browser.globals.login_securityserver_observer +
        '")]',
    );

    // clients
    mainPage.openClientsTab();
    browser.waitForElementVisible(searchField);
    browser.waitForElementNotPresent(clientsTab.elements.addClientButton);

    // keys and certs
    mainPage.openKeysTab();
    browser.waitForElementVisible(keysTab);
    keysTab.openSignAndAuthKeys();
    browser.waitForElementVisible(tokenName);
    keysTab.openAPIKeys();
    browser.waitForElementNotPresent(createAPIKeyButton);
    keysTab.openSecurityServerTLSKey();
    browser.waitForElementNotPresent(generateKeyButton);

    // diagnostics
    mainPage.openDiagnosticsTab();
    browser.waitForElementVisible(diagnosticsTab);
    browser.waitForElementVisible(globalConfiguration);

    // settings
    mainPage.openSettingsTab();
    browser.waitForElementVisible(settingsTab);
    settingsTab.openSystemParameters();
    browser.waitForElementNotPresent(anchorDownloadButton);
    browser.waitForElementNotPresent(backupAndRestoreTab);

    browser.end();
  },
};
