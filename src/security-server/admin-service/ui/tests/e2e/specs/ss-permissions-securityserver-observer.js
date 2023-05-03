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
  settingsTab;

// Other
let clientInfo,
  searchField,
  tokenName,
  generateKeyButton,
  anchorDownloadButton,
  createAPIKeyButton,
  localGroupPopup,
  clientLocalGroups,
  globalConfiguration;

module.exports = {
  tags: ['ss', 'xroad-securityserver-observer', 'permissions'],

  before: function (browser) {
    // Populate pageObjects for whole test suite
    mainPage = browser.page.ssMainPage();
    clientsTab = mainPage.section.clientsTab;
    keysTab = mainPage.section.keysTab;
    settingsTab = mainPage.section.settingsTab;
    diagnosticsTab = mainPage.section.diagnosticsTab;
    backupAndRestoreTab = settingsTab.sections.backupAndRestoreTab;

    createAPIKeyButton = mainPage.section.keysTab.elements.createAPIKeyButton;
    generateKeyButton = mainPage.section.keysTab.elements.generateKeyButton;
    anchorDownloadButton = backupAndRestoreTab.elements.anchorDownloadButton;

    tokenName = mainPage.section.keysTab.elements.tokenName;
    searchField = mainPage.section.clientsTab.elements.searchField;
    clientInfo = mainPage.section.clientInfo;
    clientLocalGroups = clientInfo.section.localGroups;
    localGroupPopup = mainPage.section.localGroupPopup;
    globalConfiguration =
      mainPage.section.diagnosticsTab.elements.globalConfiguration;

    // Test starts here...
    browser.LoginCommand(
      browser.globals.login_securityserver_observer,
      browser.globals.login_pwd,
    );
  },

  after: function (browser) {
    browser.end();
  },

  'Can not add clients': (browser) => {
    // clients
    mainPage.openClientsTab();
    clientsTab.clickSearchIcon();
    browser.waitForElementVisible(searchField);
    browser.waitForElementNotPresent(clientsTab.elements.addClientButton);
  },

  'Can not add keys': (browser) => {
    mainPage.openKeysTab();
    browser.waitForElementVisible(keysTab);
    keysTab.openSignAndAuthKeys();
    browser.waitForElementVisible(tokenName);
    keysTab.openAPIKeys();
    browser.waitForElementNotPresent(createAPIKeyButton);
    keysTab.openSecurityServerTLSKey();
    browser.waitForElementNotPresent(generateKeyButton);
  },

  'Can see functions in diagnostics': (browser) => {
    // diagnostics
    mainPage.openDiagnosticsTab();
    browser.waitForElementVisible(diagnosticsTab);
    browser.waitForElementVisible(globalConfiguration);
  },

  'Can see functions in settingsettings': (browser) => {
    mainPage.openSettingsTab();
    browser.waitForElementVisible(settingsTab);
    settingsTab.openSystemParameters();
    browser.waitForElementNotPresent(anchorDownloadButton);
    browser.waitForElementNotPresent(backupAndRestoreTab);
  },
  'Should be able to see clients details': (browser) => {
    // Security server observer should see clients details
    mainPage.openClientsTab();
    clientsTab.openClient('TestGov');
    browser.waitForElementVisible(clientInfo);

    browser
      .waitForElementVisible(
        '//div[contains(@class, "xrd-view-title") and contains(text(),"TestGov")]',
      )
      .waitForElementVisible(
        '//tr[td[contains(text(),"Member Name")] and td[contains(text(),"TestGov")]]',
      )
      .waitForElementVisible(
        '//tr[td[contains(text(),"Member Class")] and td[contains(text(),"GOV")]]',
      )
      .waitForElementVisible(
        '//tr[td[contains(text(),"Member Code")] and td[contains(text(),"0245437-2")]]',
      )
      .waitForElementVisible(
        '//span[contains(@class,"cert-name") and contains(text(),"X-Road Test CA CN")]',
      );
  },
  'should see local groups list': (browser) => {
    mainPage.openClientsTab();
    clientsTab.openClient('TestService');
    // TODO This following locator is directly written to project, since it fails create proper locator when polling
    //  for 'clientLocalGroups', figure out why
    browser.click(
      '//div[contains(@class, "v-tabs-bar__content")]//a[contains(@class, "v-tab") and contains(text(), "Local groups")]',
    );
    browser.waitForElementVisible(clientLocalGroups);

    // Security server observer should not see add local groups button
    browser.waitForElementNotPresent(clientLocalGroups.elements.addGroupButton);

    // security server observer should see local group members but not be able to edit them
    clientLocalGroups.openDetails('bac');

    browser.assert.containsText(
      localGroupPopup.elements.groupIdentifier,
      'bac',
    );
    browser.waitForElementVisible('//tr[.//*[contains(text(), "TestCom")]]');
    browser.waitForElementNotPresent(
      localGroupPopup.elements.localGroupAddMembersButton,
    );
    browser.waitForElementNotPresent(
      localGroupPopup.elements.localGroupRemoveAllButton,
    );
    browser.waitForElementNotPresent(
      localGroupPopup.elements.localGroupTestComRemoveButton,
    );
    browser.waitForElementNotPresent(
      localGroupPopup.elements.localGroupDeleteButton,
    );
  },
};
