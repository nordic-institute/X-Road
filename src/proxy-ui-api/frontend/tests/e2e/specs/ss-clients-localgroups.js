
module.exports = {
  tags: ['ss', 'clients', 'localgroups'],
  'Security server client local groups filtering': browser => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const clientDetails = clientInfo.section.details;
    const clientInternalServers = clientInfo.section.internalServers;
    const clientLocalGroups = clientInfo.section.localGroups;
    const certificatePopup = mainPage.section.certificatePopup;

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
  }
};
