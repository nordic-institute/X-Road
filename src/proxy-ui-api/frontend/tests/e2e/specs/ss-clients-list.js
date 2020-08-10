
module.exports = {
  tags: ['ss', 'clients', 'clientslist'],
  'Security server clients list': browser => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Switch to clients tab and verify
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);

    // Verify default sorting and list items
    browser.waitForElementVisible('//th[span[contains(text(),"Name")] and contains(@class, "active") and contains(@aria-sort, "ascending")]');
    clientsTab.verifyRowName(1, 'TestGov (Owner');
    clientsTab.verifyRowName(2, 'TestSaved');
    clientsTab.verifyRowName(3, 'TestService');
    clientsTab.verifyRowName(4, 'TestSub');
    clientsTab.verifyRowName(5, 'TestCom');
    clientsTab.verifyRowName(6, 'TestClient');
    clientsTab.verifyRowName(7, 'TestOrg');
    clientsTab.verifyRowName(8, 'Management');

    // Re-sort by name and verify re-sorted list item positions
    clientsTab.clickNameHeader();
    browser.waitForElementVisible('//th[span[contains(text(),"Name")] and contains(@class, "active") and contains(@aria-sort, "descending")]');

    clientsTab.verifyRowName(1, 'TestGov (Owner');
    clientsTab.verifyRowName(2, 'TestSub');
    clientsTab.verifyRowName(3, 'TestService');
    clientsTab.verifyRowName(4, 'TestSaved');
    clientsTab.verifyRowName(5, 'TestOrg');
    clientsTab.verifyRowName(6, 'Management');
    clientsTab.verifyRowName(7, 'TestCom');
    clientsTab.verifyRowName(8, 'TestClient');

    // Sort by ID and verify new sorting and list items
    clientsTab.clickIDHeader();
    browser
      .waitForElementVisible('//th[span[contains(text(),"ID")] and contains(@class, "active") and contains(@aria-sort, "ascending")]')
      .waitForElementVisible('//th[span[contains(text(),"Name")] and contains(@aria-sort, "none")]');

    clientsTab.verifyRowName(1, 'TestGov (Owner');
    clientsTab.verifyRowName(2, 'TestSaved');
    clientsTab.verifyRowName(3, 'TestService');
    clientsTab.verifyRowName(4, 'TestSub');
    clientsTab.verifyRowName(5, 'TestCom');
    clientsTab.verifyRowName(6, 'TestClient');
    clientsTab.verifyRowName(7, 'TestOrg');
    clientsTab.verifyRowName(8, 'Management');

    // Re-sort by ID and verify list items 
    clientsTab.clickIDHeader();
    browser.waitForElementVisible('//th[span[contains(text(),"ID")] and contains(@class, "active") and contains(@aria-sort, "descending")]');

    clientsTab.verifyRowName(1, 'TestGov (Owner');
    clientsTab.verifyRowName(2, 'TestSub');
    clientsTab.verifyRowName(3, 'TestService');
    clientsTab.verifyRowName(4, 'TestSaved');
    clientsTab.verifyRowName(5, 'TestCom');
    clientsTab.verifyRowName(6, 'TestClient');
    clientsTab.verifyRowName(7, 'TestOrg');
    clientsTab.verifyRowName(8, 'Management');

    // Sort by Status and verify items
    clientsTab.clickStatusHeader();
    browser.waitForElementVisible('//th[span[contains(text(),"Status")] and contains(@class, "active") and contains(@aria-sort, "ascending")]');

    clientsTab.verifyRowName(1, 'TestGov (Owner');
    clientsTab.verifyRowName(2, 'TestService');
    clientsTab.verifyRowName(3, 'TestSub');
    clientsTab.verifyRowName(4, 'TestSaved');
    clientsTab.verifyRowName(5, 'TestCom');
    clientsTab.verifyRowName(6, 'TestClient');
    clientsTab.verifyRowName(7, 'TestOrg');
    clientsTab.verifyRowName(8, 'Management');

    // Re-sort by Status and verify list items
    clientsTab.clickStatusHeader();
    browser.waitForElementVisible('//th[span[contains(text(),"Status")] and contains(@class, "active") and contains(@aria-sort, "descending")]');

    clientsTab.verifyRowName(1, 'TestGov (Owner');
    clientsTab.verifyRowName(2, 'TestSaved');
    clientsTab.verifyRowName(3, 'TestSub');
    clientsTab.verifyRowName(4, 'TestService');
    clientsTab.verifyRowName(5, 'TestCom');
    clientsTab.verifyRowName(6, 'TestClient');
    clientsTab.verifyRowName(7, 'TestOrg');
    clientsTab.verifyRowName(8, 'Management');
    
    browser.end();
  }
};
