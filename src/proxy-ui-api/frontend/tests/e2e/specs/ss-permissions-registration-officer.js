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
  tags: ['ss', 'xroad-registration-officer', 'permissions'],
  before: function (browser) {
    browser.LoginCommand(browser.globals.login_registration_officer, browser.globals.login_pwd);
  },

  'Can add clients': (browser) => {
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const searchField = mainPage.section.clientsTab.elements.searchField;

    mainPage.openClientsTab();
    clientsTab.clickSearchIcon();
    browser.waitForElementVisible(searchField);
    browser.waitForElementVisible(clientsTab.elements.addClientButton);
  },

  'Can see keys and certs': (browser) => {
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
    browser.waitForElementNotPresent(generateKeyButton);
    browser.waitForElementVisible(keysTab.elements.exportCertButton);
  },

  'Can not see diagnostics': (browser) => {
    const mainPage = browser.page.ssMainPage();
    const diagnosticsTab = mainPage.section.diagnosticsTab;
    browser.waitForElementNotPresent(diagnosticsTab);
  },

  'Can not see settings': (browser) => {
    const mainPage = browser.page.ssMainPage();
    const settingsTab = mainPage.section.settingsTab;

    browser.waitForElementNotPresent(settingsTab);
  },
  'Should see client details': (browser) => {
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;

    // Registration officer should see clients list
    mainPage.openClientsTab();

    // Registration officer should see add client button
    browser.waitForElementVisible(clientsTab.elements.addClientButton);

    // Registration officer should see clients details
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
};
