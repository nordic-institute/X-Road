
module.exports = {
  tags: ['ss', 'login'],
  'Security server failed login': browser => {
    const frontPage = browser.page.ssFrontPage();

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter invalid credentials
    frontPage
      .enterUsername(browser.globals.login_wrong_usr)
      .enterPassword(browser.globals.login_wrong_pwd)
      .signin();

    // Verify error message
    browser.waitForElementVisible('//div[text() = "Wrong username or password"]');

    browser.end();

  },
  'Security server passed login': browser => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();

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

    // Verify successful login    
    browser.waitForElementVisible('//div[contains(@class, "server-name")]');

    // Test refresh
    browser
      .refresh()
      .waitForElementVisible('//div[contains(@class, "server-name")]');

    browser
      .end();
  }
};
