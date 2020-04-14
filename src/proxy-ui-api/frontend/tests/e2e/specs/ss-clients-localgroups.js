
module.exports = {
  tags: ['ss', 'clients', 'localgroups'],
  'Security server client local groups filtering': browser => {
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
    clientsTab.openTestService();
    browser.waitForElementVisible(clientInfo);
    clientInfo.openLocalGroupsTab();
    browser.waitForElementVisible(clientLocalGroups);

    // Verify default sorting
    browser
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[2]//span[contains(text(),"1122")]')
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[3]//span[contains(text(),"1212")]')
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[4]//span[contains(text(),"2233")]')
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[5]//span[contains(text(),"abb")]')
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[6]//span[contains(text(),"bac")]')
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[7]//span[contains(text(),"cbb")]');

    // Change filtering and verify
    clientLocalGroups.filterBy('bb');
    browser
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[2]//span[contains(text(),"1212")]')
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[3]//span[contains(text(),"abb")]')
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[4]//span[contains(text(),"cbb")]')

    // Change filtering and verify
    clientLocalGroups.filterBy('Desc');
    browser
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[2]//span[contains(text(),"1122")]')
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[3]//span[contains(text(),"bac")]');

    browser.end();
  },
  'Security server client add local group': browser => {
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
    clientsTab.openTestService();
    browser.waitForElementVisible(clientInfo);
    clientInfo.openLocalGroupsTab();
    browser.waitForElementVisible(clientLocalGroups);

    // Cancel add local group dialog, verify that nothing happens
    browser
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[2]//span[contains(text(),"1122")]')
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[3]//span[contains(text(),"1212")]')
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[4]//span[contains(text(),"2233")]')
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[5]//span[contains(text(),"abb")]')
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[6]//span[contains(text(),"bac")]')
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[7]//span[contains(text(),"cbb")]');

    clientLocalGroups.openAddDialog();
    clientLocalGroups.enterCode('abc');
    browser.assert.valueContains('//div[contains(@class, "dlg-edit-row") and .//*[contains(text(), "Code")]]//input', "abc");
    clientLocalGroups.enterDescription('addDesc');
    clientLocalGroups.cancelAddDialog();

    browser
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[2]//span[contains(text(),"1122")]')
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[3]//span[contains(text(),"1212")]')
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[4]//span[contains(text(),"2233")]')
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[5]//span[contains(text(),"abb")]')
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[6]//span[contains(text(),"bac")]')
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[7]//span[contains(text(),"cbb")]')
      .waitForElementNotPresent('(//table[contains(@class, "details-certificates")]/tr)[8]');;

    // Verify that local group dialog fields are empty after re-opening
    clientLocalGroups.openAddDialog();
    browser.assert.value('//div[contains(@class, "dlg-edit-row") and .//*[contains(text(), "Code")]]//input', "");
    browser.assert.value('//div[contains(@class, "dlg-edit-row") and .//*[contains(text(), "Description")]]//input', "");

    // Verify that add is disabled if only Code is entered
    clientLocalGroups.enterCode('abc');
    browser.waitForElementVisible('//button[contains(@data-test, "dialog-save-button") and @disabled="disabled"]');
    clientLocalGroups.cancelAddDialog();

    // Verify that add is disabled if only description is entered
    clientLocalGroups.openAddDialog();
    clientLocalGroups.enterDescription('addDesc');
    browser.waitForElementVisible('//button[contains(@data-test, "dialog-save-button") and @disabled="disabled"]');

    // Verify that trying to add a group with existing code results in an error message
    clientLocalGroups.enterCode('abb');
    clientLocalGroups.confirmAddDialog();
    browser.waitForElementVisible('//*[contains(@class, "v-snack") and .//*[contains(text(), "409")]]');
    mainPage.closeSnackbar();
 
    // Add a new group and verify
    clientLocalGroups.enterCode('abc');
    clientLocalGroups.enterDescription('addDesc');
    clientLocalGroups.confirmAddDialog();
    browser.waitForElementVisible('//*[contains(@class, "v-snack") and .//*[contains(text(), "Local group added")]]');
    mainPage.closeSnackbar();

    browser
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[2]//span[contains(text(),"1122")]')
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[3]//span[contains(text(),"1212")]')
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[4]//span[contains(text(),"2233")]')
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[5]//span[contains(text(),"abb")]')
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[6]//span[contains(text(),"abc")]')
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[7]//span[contains(text(),"bac")]')
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[8]//span[contains(text(),"cbb")]')
      .waitForElementNotPresent('(//table[contains(@class, "details-certificates")]/tr)[9]');;

    browser.end();

  },
  'Security server add local group member': browser => {
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
    clientsTab.openTestService();
    browser.waitForElementVisible(clientInfo);
    clientInfo.openLocalGroupsTab();
    browser.waitForElementVisible(clientLocalGroups);
    clientLocalGroups.openAbbDetails();
    browser.waitForElementVisible(localGroupPopup);

    // Add new member to local group, cancel case
    localGroupPopup.openAddMembers();
    localGroupPopup.searchMembers();
    localGroupPopup.selectNewTestComMember();
    localGroupPopup.cancelAddMembersDialog();

    browser.waitForElementNotVisible('//span[contains(@class, "headline") and contains(text(), "Add Members")]');
    browser.waitForElementVisible(localGroupPopup);
    browser.assert.not.elementPresent('//*[contains(text(),"TestCom")]')

    // Add new member to local group
    localGroupPopup.openAddMembers();
    localGroupPopup.searchMembers();
    localGroupPopup.selectNewTestComMember();
    localGroupPopup.addSelectedMembers();

    browser.waitForElementNotVisible('//span[contains(@class, "headline") and contains(text(), "Add Members")]');
    browser.waitForElementVisible(localGroupPopup);
    browser.assert.elementPresent('//*[contains(text(),"TestCom")]')

    browser.end();

  },
  'Security server delete local group members': browser => {
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
    clientsTab.openTestService();
    browser.waitForElementVisible(clientInfo);
    clientInfo.openLocalGroupsTab();
    browser.waitForElementVisible(clientLocalGroups);
    clientLocalGroups.openBacDetails();
    browser.waitForElementVisible(localGroupPopup);


    // Remove single 
    localGroupPopup.clickRemoveTestComMember();
    localGroupPopup.cancelMemberRemove();
    browser.waitForElementNotVisible('//*[contains(@data-test, "dialog-title") and contains(text(), "Remove member?")]');
    browser.assert.elementPresent('//*[contains(text(),"TestCom")]');

    localGroupPopup.clickRemoveTestComMember();
    localGroupPopup.confirmMemberRemove();
    browser.waitForElementNotVisible('//*[contains(@data-test, "dialog-title") and contains(text(), "Remove member?")]');
    browser.assert.not.elementPresent('//*[contains(text(),"TestCom")]');
    localGroupPopup.close();

    // Remove All

    clientLocalGroups.openBacDetails();
    browser.waitForElementVisible(localGroupPopup);
    browser.assert.elementPresent('//*[contains(text(),"TestGov")]');
    browser.assert.elementPresent('//*[contains(text(),"TestOrg")]');

    localGroupPopup.clickRemoveAll();
    localGroupPopup.cancelMemberRemove();
    browser.waitForElementNotVisible('//*[contains(@data-test, "dialog-title") and contains(text(), "Remove all members?")]');
    browser.assert.elementPresent('//*[contains(text(),"TestGov")]');
    browser.assert.elementPresent('//*[contains(text(),"TestOrg")]');


    localGroupPopup.clickRemoveAll();
    browser.waitForElementVisible('//*[contains(@data-test, "dialog-title") and contains(text(), "Remove all members?")]');
    localGroupPopup.confirmMemberRemove();
    browser.waitForElementNotVisible('//*[contains(@data-test, "dialog-title") and contains(text(), "Remove all members?")]');
    browser.assert.not.elementPresent('//*[contains(text(),"TestGov")]');
    browser.assert.not.elementPresent('//*[contains(text(),"TestOrg")]');

    browser.end();

  },
  'Security server edit local group': browser => {
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
    clientsTab.openTestService();
    browser.waitForElementVisible(clientInfo);
    clientInfo.openLocalGroupsTab();
    browser.waitForElementVisible(clientLocalGroups);
    clientLocalGroups.openCbbDetails();
    browser.waitForElementVisible(localGroupPopup);

    // Change description
    localGroupPopup.changeDescription('');
    localGroupPopup.clickDescriptionLabel();
    browser.assert.containsText('//div[contains(@class, "v-snack__content")]', 'Request failed with status code 400');
    mainPage.closeSnackbar();
    localGroupPopup.close();
    browser.waitForElementVisible('//table[contains(@class, "details-certificates")]//tr[.//*[contains(text(),"cbb")] and .//*[contains(text(), "Group4")]]')
    clientLocalGroups.openCbbDetails();
    browser.waitForElementVisible(localGroupPopup);
    localGroupPopup.changeDescription(browser.globals.test_string_300.slice(0,256));
    localGroupPopup.clickDescriptionLabel();
    browser.assert.containsText('//div[contains(@class, "v-snack__content")]', 'Request failed with status code 400');
    mainPage.closeSnackbar();
    localGroupPopup.close();
    browser.waitForElementVisible('//table[contains(@class, "details-certificates")]//tr[.//*[contains(text(),"cbb")] and .//*[contains(text(), "Group4")]]');
    clientLocalGroups.openCbbDetails();
    browser.waitForElementVisible(localGroupPopup);
    localGroupPopup.changeDescription(browser.globals.test_string_300.slice(0,255));
    localGroupPopup.clickDescriptionLabel();
    browser.assert.containsText('//div[contains(@class, "v-snack__content")]', 'Description saved');
    mainPage.closeSnackbar();
    localGroupPopup.close();
    browser.waitForElementVisible('//table[contains(@class, "details-certificates")]//tr[.//*[contains(text(),"cbb")] and .//*[contains(text(), "'+browser.globals.test_string_300.slice(0,255)+'")]]')
    clientLocalGroups.openCbbDetails();
    browser.waitForElementVisible(localGroupPopup);
    localGroupPopup.changeDescription('GroupChanged');
    localGroupPopup.clickDescriptionLabel();
    browser.assert.containsText('//div[contains(@class, "v-snack__content")]', 'Description saved');
    mainPage.closeSnackbar();
    localGroupPopup.close();
    browser.waitForElementVisible('//table[contains(@class, "details-certificates")]//tr[.//*[contains(text(),"cbb")] and .//*[contains(text(), "GroupChanged")]]')
    browser.end();

  },
  'Security server delete local group': browser => {
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
    clientsTab.openTestService();
    browser.waitForElementVisible(clientInfo);
    clientInfo.openLocalGroupsTab();
    browser.waitForElementVisible(clientLocalGroups);

    // Delete and confirm
    browser.assert.elementPresent('//table[contains(@class, "details-certificates")]//tr[.//*[contains(text(),"bac")]]')
    clientLocalGroups.openBacDetails();
    browser.waitForElementVisible(localGroupPopup);
    browser.waitForElementVisible(localGroupPopup.elements.localGroupPopupCloseButton);
    localGroupPopup.deleteThisGroup();
    browser.waitForElementVisible('//*[contains(@data-test, "dialog-title") and contains(text(), "Delete group?")]');
    localGroupPopup.confirmDelete();
    browser.waitForElementVisible(clientLocalGroups);
    browser.assert.not.elementPresent('//table[contains(@class, "details-certificates")]//tr[.//*[contains(text(),"bac")]]')

    // Delete and cancel
    clientLocalGroups.openCbbDetails();
    browser.waitForElementVisible(localGroupPopup);
    localGroupPopup.deleteThisGroup();
    browser.waitForElementVisible('//*[contains(@data-test, "dialog-title") and contains(text(), "Delete group?")]');
    localGroupPopup.cancelDelete();
    browser.waitForElementNotVisible('//*[contains(@data-test, "dialog-title") and contains(text(), "Delete group?")]');   
    localGroupPopup.close();
    browser.waitForElementVisible('//table[contains(@class, "details-certificates")]//tr[.//*[contains(text(),"cbb")]]')

  }
};
