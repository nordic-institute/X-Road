
module.exports = {
  tags: ['ss', 'clients', 'permissions'],
  'Security server clients list system administrator role': browser => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const keysTab = mainPage.section.keysTab;

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage
      .clearUsername()
      .clearPassword()
      .enterUsername(browser.globals.login_system_administrator)
      .enterPassword(browser.globals.login_pwd)
      .signin();

    // Check username
    browser.waitForElementVisible('//div[contains(@class,"auth-container") and contains(text(),"'+browser.globals.login_system_administrator+'")]');

    // System admin should be in keys and certs view and not see clients tab
    browser.waitForElementVisible(keysTab)
    browser.waitForElementNotPresent(clientsTab)

    browser.end();
  },
  'Security server clients list security officer role': browser => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage
      .clearUsername()
      .clearPassword()
      .enterUsername(browser.globals.login_security_officer)
      .enterPassword(browser.globals.login_pwd)
      .signin();

    // Check username
    browser.waitForElementVisible('//div[contains(@class,"auth-container") and contains(text(),"'+browser.globals.login_security_officer+'")]');

    // Security officer should see clients list
    mainPage.openClientsTab();

    // Security officer should not see add client button
    browser.waitForElementVisible(clientsTab);
    browser.waitForElementNotPresent(clientsTab.elements.addClientButton);

    // Security officer should not see clients details
    clientsTab.openTestGov();
    browser.waitForElementNotPresent(clientInfo);

    browser.end();
  },
  'Security server clients list registration officer role': browser => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage
      .clearUsername()
      .clearPassword()
      .enterUsername(browser.globals.login_registration_officer)
      .enterPassword(browser.globals.login_pwd)
      .signin();

    // Check username
    browser.waitForElementVisible('//div[contains(@class,"auth-container") and contains(text(),"'+browser.globals.login_registration_officer+'")]');

    // Registration officer should see clients list
    mainPage.openClientsTab();

    // Registration officer should see add client button
    browser.waitForElementVisible(clientsTab.elements.addClientButton);

    // Registration officer should see clients details
    clientsTab.openTestGov();
    browser.waitForElementVisible(clientInfo);

    browser
      .waitForElementVisible('//h1[contains(text(),"TestGov")]')
      .waitForElementVisible('//tr[td[contains(text(),"Member Name")] and td[contains(text(),"TestGov")]]')
      .waitForElementVisible('//tr[td[contains(text(),"Member Class")] and td[contains(text(),"GOV")]]')
      .waitForElementVisible('//tr[td[contains(text(),"Member Code")] and td[contains(text(),"0245437-2")]]')
      .waitForElementVisible('//span[contains(@class,"cert-name") and contains(text(),"X-Road Test CA CN")]');

    browser.end();
  },
  'Security server clients list service administrator role': browser => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage
      .clearUsername()
      .clearPassword()
      .enterUsername(browser.globals.login_service_administrator)
      .enterPassword(browser.globals.login_pwd)
      .signin();

    // Check username
    browser.waitForElementVisible('//div[contains(@class,"auth-container") and contains(text(),"'+browser.globals.login_service_administrator+'")]');

    // Security officer should see clients list
    mainPage.openClientsTab();

    // Service administrator should not see add client button
    browser.waitForElementVisible(clientsTab);
    browser.waitForElementNotPresent(clientsTab.elements.addClientButton);

    // Service administrator should see clients details
    clientsTab.openTestGov();
    browser.waitForElementVisible(clientInfo);

    browser
      .waitForElementVisible('//h1[contains(text(),"TestGov")]')
      .waitForElementVisible('//tr[td[contains(text(),"Member Name")] and td[contains(text(),"TestGov")]]')
      .waitForElementVisible('//tr[td[contains(text(),"Member Class")] and td[contains(text(),"GOV")]]')
      .waitForElementVisible('//tr[td[contains(text(),"Member Code")] and td[contains(text(),"0245437-2")]]')
      .waitForElementVisible('//span[contains(@class,"cert-name") and contains(text(),"X-Road Test CA CN")]');

    browser.end();
  },
  'Security server clients list security server observer role': browser => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage
      .clearUsername()
      .clearPassword()
      .enterUsername(browser.globals.login_securityserver_observer)
      .enterPassword(browser.globals.login_pwd)
      .signin();

    // Check username
    browser.waitForElementVisible('//div[contains(@class,"auth-container") and contains(text(),"'+browser.globals.login_securityserver_observer+'")]');

    // Security server observer should see clients list
    mainPage.openClientsTab();

    // Security server observer should not see add client button
    browser.waitForElementVisible(clientsTab);
    browser.waitForElementNotPresent(clientsTab.elements.addClientButton);

    // Security server observer should see clients details
    clientsTab.openTestGov();
    browser.waitForElementVisible(clientInfo);

    browser
      .waitForElementVisible('//h1[contains(text(),"TestGov")]')
      .waitForElementVisible('//tr[td[contains(text(),"Member Name")] and td[contains(text(),"TestGov")]]')
      .waitForElementVisible('//tr[td[contains(text(),"Member Class")] and td[contains(text(),"GOV")]]')
      .waitForElementVisible('//tr[td[contains(text(),"Member Code")] and td[contains(text(),"0245437-2")]]')
      .waitForElementVisible('//span[contains(@class,"cert-name") and contains(text(),"X-Road Test CA CN")]');
    browser.end();
  }
};
