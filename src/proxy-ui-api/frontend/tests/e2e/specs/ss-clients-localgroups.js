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
  tags: ['ss', 'clients', 'localgroups'],
  'Security server client local groups filtering': (browser) => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const clientLocalGroups = clientInfo.section.localGroups;

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Open TestGov Internal Servers
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openLocalGroupsTab();
    browser.waitForElementVisible(clientLocalGroups);

    // Verify default sorting
    clientLocalGroups.verifyGroupListRow(2, '1122');
    clientLocalGroups.verifyGroupListRow(3, '1212');
    clientLocalGroups.verifyGroupListRow(4, '2233');
    clientLocalGroups.verifyGroupListRow(5, 'abb');
    clientLocalGroups.verifyGroupListRow(6, 'bac');
    clientLocalGroups.verifyGroupListRow(7, 'cbb');

    // Change filtering and verify
    clientLocalGroups.filterBy('bb');
    clientLocalGroups.verifyGroupListRow(2, '1212');
    clientLocalGroups.verifyGroupListRow(3, 'abb');
    clientLocalGroups.verifyGroupListRow(4, 'cbb');

    // Change filtering and verify
    clientLocalGroups.filterBy('Desc');
    clientLocalGroups.verifyGroupListRow(2, '1122');
    clientLocalGroups.verifyGroupListRow(3, 'bac');

    browser.end();
  },
  'Security server client add local group': (browser) => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const clientLocalGroups = clientInfo.section.localGroups;

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Open TestGov Internal Servers
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openLocalGroupsTab();
    browser.waitForElementVisible(clientLocalGroups);

    // Cancel add local group dialog, verify that nothing happens
    clientLocalGroups.verifyGroupListRow(2, '1122');
    clientLocalGroups.verifyGroupListRow(3, '1212');
    clientLocalGroups.verifyGroupListRow(4, '2233');
    clientLocalGroups.verifyGroupListRow(5, 'abb');
    clientLocalGroups.verifyGroupListRow(6, 'bac');
    clientLocalGroups.verifyGroupListRow(7, 'cbb');

    clientLocalGroups.openAddDialog();
    clientLocalGroups.enterCode('abc');
    browser.assert.valueContains(
      '//div[contains(@class, "dlg-edit-row") and .//*[contains(text(), "Code")]]//input',
      'abc',
    );
    clientLocalGroups.enterDescription('addDesc');
    clientLocalGroups.cancelAddDialog();

    clientLocalGroups.verifyGroupListRow(2, '1122');
    clientLocalGroups.verifyGroupListRow(3, '1212');
    clientLocalGroups.verifyGroupListRow(4, '2233');
    clientLocalGroups.verifyGroupListRow(5, 'abb');
    clientLocalGroups.verifyGroupListRow(6, 'bac');
    clientLocalGroups.verifyGroupListRow(7, 'cbb');

    // Verify that local group dialog fields are empty after re-opening
    clientLocalGroups.openAddDialog();
    browser.assert.value(
      '//div[contains(@class, "dlg-edit-row") and .//*[contains(text(), "Code")]]//input',
      '',
    );
    browser.assert.value(
      '//div[contains(@class, "dlg-edit-row") and .//*[contains(text(), "Description")]]//input',
      '',
    );

    // Verify that add is disabled if only Code is entered
    clientLocalGroups.enterCode('abc');
    browser.waitForElementVisible(
      '//button[contains(@data-test, "dialog-save-button") and @disabled="disabled"]',
    );
    clientLocalGroups.cancelAddDialog();

    // Verify that add is disabled if only description is entered
    clientLocalGroups.openAddDialog();
    clientLocalGroups.enterDescription('addDesc');
    browser.waitForElementVisible(
      '//button[contains(@data-test, "dialog-save-button") and @disabled="disabled"]',
    );

    // Verify that trying to add a group with existing code results in an error message
    clientLocalGroups.enterCode('abb');
    clientLocalGroups.confirmAddDialog();
    browser.assert.containsText(
      mainPage.elements.snackBarMessage,
      'Local group code already exists',
    );
    mainPage.closeSnackbar();

    // Add a new group and verify
    clientLocalGroups.enterCode('abc');
    clientLocalGroups.enterDescription('addDesc');
    clientLocalGroups.confirmAddDialog();
    browser.assert.containsText(
      mainPage.elements.snackBarMessage,
      'Local group added',
    );
    mainPage.closeSnackbar();

    clientLocalGroups.verifyGroupListRow(2, '1122');
    clientLocalGroups.verifyGroupListRow(3, '1212');
    clientLocalGroups.verifyGroupListRow(4, '2233');
    clientLocalGroups.verifyGroupListRow(5, 'abb');
    clientLocalGroups.verifyGroupListRow(6, 'abc');
    clientLocalGroups.verifyGroupListRow(7, 'bac');
    clientLocalGroups.verifyGroupListRow(8, 'cbb');

    browser.end();
  },
  'Security server add local group member': (browser) => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const clientLocalGroups = clientInfo.section.localGroups;
    const localGroupPopup = mainPage.section.localGroupPopup;
    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Open TestGov Internal Servers and local group details view
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openLocalGroupsTab();
    browser.waitForElementVisible(clientLocalGroups);
    clientLocalGroups.openDetails('abc');
    browser.waitForElementVisible(localGroupPopup);

    // Add new member to local group, cancel case
    localGroupPopup.openAddMembers();
    localGroupPopup.searchMembers();
    localGroupPopup.selectMember('REST-UI-TEST:COM:1710128-9:TestClient');
    localGroupPopup.selectMember('REST-UI-TEST:GOV:0245437-2:TestService');
    localGroupPopup.selectMember('REST-UI-TEST:ORG:2908758-4:Management');
    localGroupPopup.cancelAddMembersDialog();

    browser.waitForElementNotVisible(
      '//span[contains(@class, "headline") and contains(text(), "Add Members")]',
    );
    browser.waitForElementVisible(localGroupPopup);
    browser.assert.not.elementPresent('//*[contains(text(),"TestCom")]');

    // Add new member to local group
    localGroupPopup.openAddMembers();
    localGroupPopup.searchMembers();
    localGroupPopup.selectMember('REST-UI-TEST:COM:1710128-9:TestClient');
    localGroupPopup.selectMember('REST-UI-TEST:GOV:0245437-2:TestService');
    localGroupPopup.selectMember('REST-UI-TEST:ORG:2908758-4:Management');
    localGroupPopup.addSelectedMembers();

    browser.waitForElementNotVisible(
      '//span[contains(@class, "headline") and contains(text(), "Add Members")]',
    );
    browser.waitForElementVisible(localGroupPopup);
    browser.assert.elementPresent('//*[contains(text(),"TestCom")]');

    browser.end();
  },
  'Security server delete local group members': (browser) => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const clientLocalGroups = clientInfo.section.localGroups;
    const localGroupPopup = mainPage.section.localGroupPopup;
    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Open TestGov Internal Servers and local group details view
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openLocalGroupsTab();
    browser.waitForElementVisible(clientLocalGroups);
    clientLocalGroups.openDetails('abc');
    browser.waitForElementVisible(localGroupPopup);

    // Remove single
    localGroupPopup.clickRemoveTestComMember();
    localGroupPopup.cancelMemberRemove();
    browser.waitForElementNotVisible(
      '//*[contains(@data-test, "dialog-title") and contains(text(), "Remove member?")]',
    );
    browser.assert.elementPresent('//*[contains(text(),"TestCom")]');

    localGroupPopup.clickRemoveTestComMember();
    localGroupPopup.confirmMemberRemove();
    browser.waitForElementNotVisible(
      '//*[contains(@data-test, "dialog-title") and contains(text(), "Remove member?")]',
    );
    browser.assert.not.elementPresent('//*[contains(text(),"TestCom")]');
    localGroupPopup.close();

    // Remove All
    clientLocalGroups.openDetails('abc');
    browser.waitForElementVisible(localGroupPopup);
    browser.assert.elementPresent('//*[contains(text(),"TestGov")]');
    browser.assert.elementPresent('//*[contains(text(),"TestOrg")]');

    localGroupPopup.clickRemoveAll();
    localGroupPopup.cancelMemberRemove();
    browser.waitForElementNotVisible(
      '//*[contains(@data-test, "dialog-title") and contains(text(), "Remove all members?")]',
    );
    browser.assert.elementPresent('//*[contains(text(),"TestGov")]');
    browser.assert.elementPresent('//*[contains(text(),"TestOrg")]');

    localGroupPopup.clickRemoveAll();
    browser.waitForElementVisible(
      '//*[contains(@data-test, "dialog-title") and contains(text(), "Remove all members?")]',
    );
    localGroupPopup.confirmMemberRemove();
    browser.waitForElementNotVisible(
      '//*[contains(@data-test, "dialog-title") and contains(text(), "Remove all members?")]',
    );
    browser.assert.not.elementPresent('//*[contains(text(),"TestGov")]');
    browser.assert.not.elementPresent('//*[contains(text(),"TestOrg")]');

    browser.end();
  },
  'Security server edit local group': (browser) => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const clientLocalGroups = clientInfo.section.localGroups;
    const localGroupPopup = mainPage.section.localGroupPopup;
    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Open TestGov Internal Servers and local group details view
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openLocalGroupsTab();
    browser.waitForElementVisible(clientLocalGroups);
    clientLocalGroups.openDetails('cbb');
    browser.waitForElementVisible(localGroupPopup);

    // Change description
    localGroupPopup.changeDescription('');
    localGroupPopup.clickDescriptionLabel();
    browser.assert.containsText(
      '//div[contains(@class, "v-snack__content")]',
      'Validation failure',
    );
    browser.assert.containsText(
      mainPage.elements.snackBarMessage,
      'Validation failure',
    );
    mainPage.closeSnackbar();
    localGroupPopup.close();
    browser.waitForElementVisible(
      '//table[contains(@class, "details-certificates")]//tr[.//*[contains(text(),"cbb")] and .//*[contains(text(), "Group4")]]',
    );
    clientLocalGroups.openDetails('cbb');
    browser.waitForElementVisible(localGroupPopup);
    localGroupPopup.changeDescription(
      browser.globals.test_string_300.slice(0, 256),
    );
    localGroupPopup.clickDescriptionLabel();
    browser.assert.containsText(
      '//div[contains(@class, "v-snack__content")]',
      'Validation failure',
    );
    browser.assert.containsText(
      mainPage.elements.snackBarMessage,
      'Validation failure',
    );
    mainPage.closeSnackbar();
    localGroupPopup.close();
    browser.waitForElementVisible(
      '//table[contains(@class, "details-certificates")]//tr[.//*[contains(text(),"cbb")] and .//*[contains(text(), "Group4")]]',
    );
    clientLocalGroups.openDetails('cbb');
    browser.waitForElementVisible(localGroupPopup);
    localGroupPopup.changeDescription(
      browser.globals.test_string_300.slice(0, 255),
    );
    localGroupPopup.clickDescriptionLabel();
    browser.assert.containsText(
      '//div[contains(@class, "v-snack__content")]',
      'Description saved',
    );
    browser.assert.containsText(
      mainPage.elements.snackBarMessage,
      'Description saved',
    );
    mainPage.closeSnackbar();
    localGroupPopup.close();
    browser.waitForElementVisible(
      '//table[contains(@class, "details-certificates")]//tr[.//*[contains(text(),"cbb")] and .//*[contains(text(), "' +
        browser.globals.test_string_300.slice(0, 255) +
        '")]]',
    );
    clientLocalGroups.openDetails('cbb');
    browser.waitForElementVisible(localGroupPopup);
    localGroupPopup.changeDescription('Group4');
    localGroupPopup.clickDescriptionLabel();
    browser.assert.containsText(
      '//div[contains(@class, "v-snack__content")]',
      'Description saved',
    );
    browser.assert.containsText(
      mainPage.elements.snackBarMessage,
      'Description saved',
    );
    mainPage.closeSnackbar();
    localGroupPopup.close();
    browser.waitForElementVisible(
      '//table[contains(@class, "details-certificates")]//tr[.//*[contains(text(),"cbb")] and .//*[contains(text(), "Group4")]]',
    );
    browser.end();
  },
  'Security server delete local group': (browser) => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const clientLocalGroups = clientInfo.section.localGroups;
    const localGroupPopup = mainPage.section.localGroupPopup;

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Open TestGov Internal Servers and local group details view
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openLocalGroupsTab();
    browser.waitForElementVisible(clientLocalGroups);

    // Delete and confirm
    browser.assert.elementPresent(
      '//table[contains(@class, "details-certificates")]//tr[.//*[contains(text(),"bac")]]',
    );
    clientLocalGroups.openDetails('abc');
    browser.waitForElementVisible(localGroupPopup);
    browser.waitForElementVisible(
      localGroupPopup.elements.localGroupPopupCloseButton,
    );
    localGroupPopup.deleteThisGroup();
    browser.waitForElementVisible(
      '//*[contains(@data-test, "dialog-title") and contains(text(), "Delete group?")]',
    );
    localGroupPopup.confirmDelete();
    browser.waitForElementVisible(clientLocalGroups);
    browser.assert.not.elementPresent(
      '//table[contains(@class, "details-certificates")]//tr[.//*[contains(text(),"abc")]]',
    );

    // Delete and cancel
    clientLocalGroups.openDetails('cbb');
    browser.waitForElementVisible(localGroupPopup);
    localGroupPopup.deleteThisGroup();
    browser.waitForElementVisible(
      '//*[contains(@data-test, "dialog-title") and contains(text(), "Delete group?")]',
    );
    localGroupPopup.cancelDelete();
    browser.waitForElementNotVisible(
      '//*[contains(@data-test, "dialog-title") and contains(text(), "Delete group?")]',
    );
    localGroupPopup.close();
    browser.waitForElementVisible(
      '//table[contains(@class, "details-certificates")]//tr[.//*[contains(text(),"cbb")]]',
    );
  },
};
