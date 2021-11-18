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

// Tabs
let mainPage,
  diagnosticsTab,
  clientsTab,
  keysTab,
  backupAndRestoreTab,
  settingsTab,
  APIKeysTab;

// Other
let clientInfo, searchField, tokenName, generateKeyButton, anchorDownloadButton;

module.exports = {
  tags: ['ss', 'xroad-security-officer', 'permissions'],
  before: function (browser) {
    // Populate pageObjects for whole test suite
    mainPage = browser.page.ssMainPage();
    diagnosticsTab = mainPage.section.diagnosticsTab;
    keysTab = mainPage.section.keysTab;
    clientsTab = mainPage.section.clientsTab;
    APIKeysTab = mainPage.section.keysTab.elements.APIKeysTab;
    settingsTab = mainPage.section.settingsTab;
    backupAndRestoreTab = settingsTab.sections.backupAndRestoreTab;

    clientInfo = mainPage.section.clientInfo;
    searchField = mainPage.section.clientsTab.elements.searchField;
    tokenName = mainPage.section.keysTab.elements.tokenName;
    generateKeyButton = mainPage.section.keysTab.elements.generateKeyButton;
    anchorDownloadButton = backupAndRestoreTab.elements.anchorDownloadButton;

    browser.LoginCommand(
      browser.globals.login_security_officer,
      browser.globals.login_pwd,
    );
  },

  after: function (browser) {
    browser.end();
  },

  'Can not add clients': (browser) => {
    mainPage.openClientsTab();
    clientsTab.clickSearchIcon();
    browser.waitForElementVisible(searchField);
    browser.waitForElementNotPresent(clientsTab.elements.addClientButton);
  },

  'Can not see API-keys': (browser) => {
    mainPage.openKeysTab();
    browser.waitForElementVisible(keysTab);
    keysTab.openSignAndAuthKeys();
    browser.waitForElementVisible(tokenName);
    browser.waitForElementNotPresent(APIKeysTab);
    keysTab.openSecurityServerTLSKey();
    browser.waitForElementVisible(generateKeyButton);
  },

  'Can not do backup and restore': (browser) => {
    mainPage.openSettingsTab();
    browser.waitForElementVisible(settingsTab);
    settingsTab.openSystemParameters();
    browser.waitForElementVisible(anchorDownloadButton);
    browser.waitForElementNotPresent(backupAndRestoreTab);
  },

  'Can not see diagnostics-tab': (browser) => {
    browser.waitForElementNotPresent(diagnosticsTab);
  },

  'Can not see client details': (browser) => {
    // Security officer should see clients list
    mainPage.openClientsTab();
    // Security officer should not see add client button
    browser.waitForElementVisible(clientsTab);
    browser.waitForElementNotPresent(clientsTab.elements.addClientButton);

    // Security officer should not see clients details
    clientsTab.openClient('TestGov');
    browser.waitForElementNotPresent(clientInfo.elements.detailsTab);
  },
};
