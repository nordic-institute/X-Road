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
  tags: ['ss', 'xroad-security-officer', 'permissions'],
  before: function (browser) {
    browser.LoginCommand(browser.globals.login_security_officer, browser.globals.login_pwd);
  },

  after: function (browser) {
    browser.end();
  },

  'Can not add clients': (browser) => {
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const searchField = mainPage.section.clientsTab.elements.searchField;

    mainPage.openClientsTab();
    clientsTab.clickSearchIcon();
    browser.waitForElementVisible(searchField);
    browser.waitForElementNotPresent(clientsTab.elements.addClientButton);
  },

  'Can not see API-keys': (browser) => {
    const mainPage = browser.page.ssMainPage();
    const keysTab = mainPage.section.keysTab;
    const tokenName = mainPage.section.keysTab.elements.tokenName;
    const APIKeysTab = mainPage.section.keysTab.elements.APIKeysTab;
    const generateKeyButton =
      mainPage.section.keysTab.elements.generateKeyButton;

    mainPage.openKeysTab();
    browser.waitForElementVisible(keysTab);
    keysTab.openSignAndAuthKeys();
    browser.waitForElementVisible(tokenName);
    browser.waitForElementNotPresent(APIKeysTab);
    keysTab.openSecurityServerTLSKey();
    browser.waitForElementVisible(generateKeyButton);
  },

  'Can not do backup and restore': (browser) => {
    const mainPage = browser.page.ssMainPage();
    const settingsTab = mainPage.section.settingsTab;
    const backupAndRestoreTab = settingsTab.sections.backupAndRestoreTab;
    const anchorDownloadButton =
      backupAndRestoreTab.elements.anchorDownloadButton;

    mainPage.openSettingsTab();
    browser.waitForElementVisible(settingsTab);
    settingsTab.openSystemParameters();
    browser.waitForElementVisible(anchorDownloadButton);
    browser.waitForElementNotPresent(backupAndRestoreTab);
  },

  'Can not see diagnostics-tab': (browser) => {
    const mainPage = browser.page.ssMainPage();
    const diagnosticsTab = mainPage.section.diagnosticsTab;
    browser.waitForElementNotPresent(diagnosticsTab);
  },
  'Can not see client details': (browser) => {
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;

    // Security officer should see clients list
    mainPage.openClientsTab();
    // Security officer should not see add client button
    browser.waitForElementVisible(clientsTab);
    browser.waitForElementNotPresent(clientsTab.elements.addClientButton);

    // Security officer should not see clients details
    clientsTab.openClient('TestGov');
    browser.waitForElementNotPresent(clientInfo.elements.detailsTab);
  },
}

