
module.exports = {
  tags: ['ss', 'logout'],
  'Security server logout': browser => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();

    // Navigate to app and check that the browser has loaded the page
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

    // Logout and verify
    mainPage.logout();
    browser.waitForElementVisible('//*[@id="username"]');

    browser.end();

  },
  'Security server timeout logout': browser => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();

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

    // Wait for the timeout message to appear
    browser.waitForElementVisible(mainPage.elements.snackBarMessage, browser.globals.logout_timeout_ms + 60000, 1000); 
    browser.assert.containsText(mainPage.elements.snackBarMessage, 'Error: Request failed with status code 401');
    mainPage.closeSnackbar();

    browser.waitForElementVisible('//*[@id="username"]');
    browser.end();
  }
};
