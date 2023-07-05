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

let mainPage, settingsTab, systemParametersTab;

module.exports = {
  tags: ['ss', 'settings', 'timestamping'],
  before: function (browser) {
    // Populate pageObjects for whole test suite
    mainPage = browser.page.ssMainPage();
    settingsTab = mainPage.section.settingsTab;
    systemParametersTab = settingsTab.section.systemParametersTab;
    browser.LoginCommand(browser.globals.login_usr, browser.globals.login_pwd);
    mainPage.openSettingsTab();
    browser.waitForElementVisible(settingsTab);
  },

  after: function (browser) {
    browser.end();
  },

  'timestamping service can be deleted': () => {
    // when user confirms:
    systemParametersTab.deleteCurrentTimestampingService();
  },

  'User can add new timestamping service': () => {
    systemParametersTab.openTimeStampingAddDialog();
    // adding can be cancelled
    systemParametersTab.click('@timestampingAddDialogCancelButton');
    systemParametersTab.waitForElementNotVisible(
      '@timestampingAddDialogCancelButton',
    );
    systemParametersTab.waitForElementNotPresent(
      '@timestampingServiceTableRow',
    );

    // cancelling after selecting doesn't yet add server
    systemParametersTab.click('@timestampingAddButton');
    systemParametersTab.click('@timestampingAddDialogServiceSelection');
    systemParametersTab.click('@timestampingAddDialogCancelButton');
    systemParametersTab.waitForElementNotPresent(
      '@timestampingServiceTableRow',
    );

    // clicking 'add' updates table, closes dialog and gives success message.
    systemParametersTab.click('@timestampingAddButton');
    systemParametersTab.click('@timestampingAddDialogServiceSelection');
    systemParametersTab.click('@timestampingAddDialogAddButton');
    systemParametersTab.assertTimestampingTableContents('X-Road Test TSA CN');

    // add button is disabled if there is no room for new services
    systemParametersTab.waitForElementVisible('@timestampingServiceTableRow');
    systemParametersTab.waitForElementPresent('@timestampingAddButton');
  },

  'Timestamp-table is visible': () => {
    // list shows name and url of the service
    systemParametersTab.assertTimestampingTableContents('X-Road Test TSA CN');
  },
  'service deletion can be cancelled': () => {
    systemParametersTab.openTimeStampingDeleteDialog();
    systemParametersTab.waitForElementVisible(
      '@timestampingDeleteDialogCancelButton',
    );
    systemParametersTab.click('@timestampingDeleteDialogCancelButton');
    systemParametersTab.assertTimestampingTableContents('X-Road Test TSA CN');
  },
};
