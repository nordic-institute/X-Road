
module.exports = {
  tags: ['ss', 'clients', 'clientdetails'],
  'Security server client details view': browser => {
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
    frontPage.signinDefaultUser();

    // Open TestGov client details view
    mainPage.openClientsTab();
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
    browser.assert.containsText(certificatePopup, "X-Road Test CA CN");
    browser.assert.containsText(certificatePopup, "SHA256withRSA");
    browser.assert.containsText(certificatePopup, "93:7F:89:09:B0:8F:B3:DA:40:96:50:8A:24:8A:0C:E2:F8:77:AC:A7");
    browser.assert.containsText(certificatePopup, "SERIALNUMBER=REST-UI-TEST/ss1-vrk/GOV, CN=0245437-2, O=VRK, C=FI");     
     
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
    browser.assert.containsText(certificatePopup, "X-Road Test CA CN");
    browser.assert.containsText(certificatePopup, "SHA256withRSA");
    browser.assert.containsText(certificatePopup, "93:7F:89:09:B0:8F:B3:DA:40:96:50:8A:24:8A:0C:E2:F8:77:AC:A7");
    browser.assert.containsText(certificatePopup, "SERIALNUMBER=REST-UI-TEST/ss1-vrk/GOV, CN=0245437-2, O=VRK, C=FI");
     
    // Close sign certificate popup
    certificatePopup.close();

    browser.end();
  }
};
