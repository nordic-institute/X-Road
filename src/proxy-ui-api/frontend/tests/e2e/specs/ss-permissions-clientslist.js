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
  tags: ['ss', 'clients', 'permissions'],
  'Security server clients list system administrator role': (browser) => {
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
    browser.assert.containsText(
      mainPage.elements.userMenuButton,
      browser.globals.login_system_administrator,
    );

    // System admin should be in keys and certs view and not see clients tab
    browser.waitForElementVisible(keysTab);
    browser.waitForElementNotPresent(clientsTab);

    browser.end();
  },
  'Security server clients list security officer role': (browser) => {
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
    browser.assert.containsText(
      mainPage.elements.userMenuButton,
      browser.globals.login_security_officer,
    );

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
  'Security server clients list registration officer role': (browser) => {
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
    browser.assert.containsText(
      mainPage.elements.userMenuButton,
      browser.globals.login_registration_officer,
    );

    // Registration officer should see clients list
    mainPage.openClientsTab();

    // Registration officer should see add client button
    browser.waitForElementVisible(clientsTab.elements.addClientButton);

    // Registration officer should see clients details
    clientsTab.openTestGov();
    browser.waitForElementVisible(clientInfo);

    browser
      .waitForElementVisible('//div[contains(@class, "xrd-view-title") and contains(text(),"TestGov")]')
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

    browser.end();
  },
  'Security server clients list service administrator role': (browser) => {
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
    browser.assert.containsText(
      mainPage.elements.userMenuButton,
      browser.globals.login_service_administrator,
    );

    // Security officer should see clients list
    mainPage.openClientsTab();

    // Service administrator should not see add client button
    browser.waitForElementVisible(clientsTab);
    browser.waitForElementNotPresent(clientsTab.elements.addClientButton);

    // Service administrator should see clients details
    clientsTab.openTestGov();
    browser.waitForElementVisible(clientInfo);

    browser
      .waitForElementVisible('//div[contains(@class, "xrd-view-title") and contains(text(),"TestGov")]')
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

    browser.end();
  },
  'Security server clients list security server observer role': (browser) => {
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
    browser.assert.containsText(
      mainPage.elements.userMenuButton,
      browser.globals.login_securityserver_observer,
    );

    // Security server observer should see clients list
    mainPage.openClientsTab();

    // Security server observer should not see add client button
    browser.waitForElementVisible(clientsTab);
    browser.waitForElementNotPresent(clientsTab.elements.addClientButton);

    // Security server observer should see clients details
    clientsTab.openTestGov();
    browser.waitForElementVisible(clientInfo);

    browser
      .waitForElementVisible('//div[contains(@class, "xrd-view-title") and contains(text(),"TestGov")]')
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
    browser.end();
  },
};
