
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
    frontPage
      .clearUsername()
      .clearPassword()
      .enterUsername(browser.globals.login_usr)
      .enterPassword(browser.globals.login_pwd)
      .signin();

    // Open TestGov Internal Servers
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openTestService();
    browser.waitForElementVisible(clientInfo);
    clientInfo.openLocalGroupsTab();
    browser.waitForElementVisible(clientLocalGroups);
    // Verify default sorting
    browser
      .useXpath()
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[2]//span[contains(text(),"1122")]')
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[3]//span[contains(text(),"1212")]')
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[4]//span[contains(text(),"2233")]')
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[5]//span[contains(text(),"abb")]')
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[6]//span[contains(text(),"bac")]')
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[7]//span[contains(text(),"cbb")]');

    // Change filtering and verify
    clientLocalGroups.filterBy('bb');
    browser
      .useXpath()
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[2]//span[contains(text(),"1212")]')
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[3]//span[contains(text(),"abb")]')
      .waitForElementVisible('(//table[contains(@class, "details-certificates")]/tr)[4]//span[contains(text(),"cbb")]')

    // Change filtering and verify
    clientLocalGroups.filterBy('Desc');
    browser
      .useXpath()
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
    frontPage
      .clearUsername()
      .clearPassword()
      .enterUsername(browser.globals.login_usr)
      .enterPassword(browser.globals.login_pwd)
      .signin();

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
 
    // Add a new group and verify
    clientLocalGroups.enterCode('abc');
    clientLocalGroups.enterDescription('addDesc');
    clientLocalGroups.confirmAddDialog();
    browser.waitForElementVisible('//*[contains(@class, "v-snack") and .//*[contains(text(), "Local group added")]]');

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

  }
};
