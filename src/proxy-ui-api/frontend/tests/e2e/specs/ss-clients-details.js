
module.exports = {
  tags: ['ss', 'clients', 'clientdetails'],
  'Security server clients list': browser => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const clientDetails = clientInfo.section.details;
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

    // Switch to clients tab and verify
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);

    // Click TestGov
    clientsTab.openTestGov();
    browser.waitForElementVisible(clientInfo);
    clientInfo.openDetailsTab();
    browser.waitForElementVisible(clientDetails);

    // Verify info
    browser
      .waitForElementVisible('//h1[contains(text(),"TestGov")]')
      .waitForElementVisible('//tr[td[contains(text(),"Member Name")] and td[contains(text(),"TestGov")]]')
      .waitForElementVisible('//tr[td[contains(text(),"Member Class")] and td[contains(text(),"GOV")]]')
      .waitForElementVisible('//tr[td[contains(text(),"Member Code")] and td[contains(text(),"0245437-2")]]')
      .waitForElementVisible('//span[contains(@class,"cert-name") and contains(text(),"X-Road Test CA CN")]');

    // Open sign certificate
    clientDetails.openSignCertificateInfo();
    browser.waitForElementVisible(certificatePopup); 

    // Verify sign certificate info 
    browser.assert.containsText(certificatePopup, "Name: X-Road Test CA CN");
    browser.assert.containsText(certificatePopup, "OCSP status: Good");
    browser.assert.containsText(certificatePopup, "Hash: 937F8909B08FB3DA4096508A248A0CE2F877ACA7");
    browser.assert.containsText(certificatePopup, "State: in use");
    browser.assert.containsText(certificatePopup, "Expires: 2039-09-11T18:53:53Z");
     
    // Close sign certificate popup
    certificatePopup.close();
    
    // Open TestService
    mainPage.openClientsTab();
    clientsTab.openTestService();
    clientInfo.openDetailsTab();

    // Verify info
    browser
      .waitForElementVisible('//h1[contains(text(),"TestService")]')
      .waitForElementVisible('//tr[td[contains(text(),"Member Name")] and td[contains(text(),"TestGov")]]')
      .waitForElementVisible('//tr[td[contains(text(),"Member Class")] and td[contains(text(),"GOV")]]')
      .waitForElementVisible('//tr[td[contains(text(),"Member Code")] and td[contains(text(),"0245437-2")]]')
      .waitForElementVisible('//tr[td[contains(text(),"Subsystem Code")] and td[contains(text(),"TestService")]]')
      .waitForElementVisible('//span[contains(@class,"cert-name") and contains(text(),"X-Road Test CA CN")]');

    // Open sign certificate
    clientDetails.openSignCertificateInfo();
    browser.waitForElementVisible(certificatePopup); 

    // Verify sign certificate info 
    browser.assert.containsText(certificatePopup, "Name: X-Road Test CA CN");
    browser.assert.containsText(certificatePopup, "OCSP status: Good");
    browser.assert.containsText(certificatePopup, "Hash: 937F8909B08FB3DA4096508A248A0CE2F877ACA7");
    browser.assert.containsText(certificatePopup, "State: in use");
    browser.assert.containsText(certificatePopup, "Expires: 2039-09-11T18:53:53Z");
     
    // Close sign certificate popup
    certificatePopup.close();

    browser.end();
  }
};
