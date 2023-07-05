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
  tags: ['ss', 'clients', 'clientdetails'],
  'Security server client details view': (browser) => {
    const frontPage = browser.page.ssLoginPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const clientDetails = clientInfo.section.details;
    const certificatePopup = mainPage.section.certificatePopup;

    // Open SUT and check that page is loaded
    frontPage.navigateAndMakeTestable();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Open TestGov client details view
    mainPage.openClientsTab();
    clientsTab.openClient('TestGov');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openDetailsTab();
    browser.waitForElementVisible(clientDetails);

    // Verify info
    browser
      .waitForElementVisible(
        '//div[contains(@class, "xrd-view-title") and contains(text(),"TestGov")]',
      )
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

    // Open sign certificate
    clientDetails.openSignCertificateInfo();
    browser.waitForElementVisible(certificatePopup);

    // Verify sign certificate info
    browser.assert.containsText(certificatePopup, 'X-Road Test CA CN');
    browser.assert.containsText(certificatePopup, 'SHA256withRSA');
    let hashRegex = /^(([A-F0-9]{2}:?){20})$/;
    browser.expect.element(certificatePopup.elements.certificateHash).text.to.match(hashRegex);
    browser.assert.containsText(
      certificatePopup,
      'SERIALNUMBER=REST-UI-TEST/ss1-vrk/GOV, CN=0245437-2, O=VRK, C=FI',
    );

    // Close sign certificate popup
    certificatePopup.close();

    // Open TestService
    mainPage.openClientsTab();
    clientsTab.openClient('TestService');
    clientInfo.openDetailsTab();

    // Verify info
    browser
      .waitForElementVisible(
        '//div[contains(@class, "xrd-view-title") and contains(text(),"TestService")]',
      )
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
        '//tr[td[contains(text(),"Subsystem Code")] and td[contains(text(),"TestService")]]',
      )
      .waitForElementVisible(
        '//span[contains(@class,"cert-name") and contains(text(),"X-Road Test CA CN")]',
      );

    // Open sign certificate
    clientDetails.openSignCertificateInfo();
    browser.waitForElementVisible(certificatePopup);

    // Verify sign certificate info
    browser.assert.containsText(certificatePopup, 'X-Road Test CA CN');
    browser.assert.containsText(certificatePopup, 'SHA256withRSA');
    browser.expect.element(certificatePopup.elements.certificateHash).text.to.match(hashRegex);
    browser.assert.containsText(
      certificatePopup,
      'SERIALNUMBER=REST-UI-TEST/ss1-vrk/GOV, CN=0245437-2, O=VRK, C=FI',
    );

    // Close sign certificate popup
    certificatePopup.close();

    browser.end();
  },
};
