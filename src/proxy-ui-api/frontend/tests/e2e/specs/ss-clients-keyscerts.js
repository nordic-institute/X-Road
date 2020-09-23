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
  tags: ['ss', 'keyscerts', 'signauthkeys'],
  'Security server keys sign and auth keys view': browser => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const keysTab = browser.page.ssKeysAndCertsTab();
    const signAuthTab = keysTab.section.signAuthKeysTab;
    const logoutTokenPopup = keysTab.section.logoutTokenPopup;
    const loginTokenPopup = keysTab.section.loginTokenPopup;
    const authKeyDetails = keysTab.section.authKeyDetails;
    const signKeyDetails = keysTab.section.signKeyDetails;
    const certificatePopup = mainPage.section.certificatePopup;

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Navigate to target page
    mainPage.openKeysTab();
    keysTab.openSignAuthKeysTab();
    browser.waitForElementVisible(signAuthTab);

    signAuthTab.toggleExpandToken();

    // Test token logout and login, check button states

    // Test cancel logout
    signAuthTab.logoutToken();
    browser.waitForElementVisible(logoutTokenPopup);
    logoutTokenPopup.cancel();

    browser.expect.element(signAuthTab.elements.addKeyButton).to.be.enabled;
    browser.expect.element(signAuthTab.elements.importCertButton).to.be.enabled;
    browser.expect.element(signAuthTab.elements.authGenerateCSRButton).to.be.enabled;
    browser.expect.element(signAuthTab.elements.signGenerateCSRButton).to.be.enabled;

    // Test logout
    signAuthTab.logoutToken();
    browser.waitForElementVisible(logoutTokenPopup);
    logoutTokenPopup.confirm();
    browser.assert.containsText(mainPage.elements.snackBarMessage, 'Logged out of token');
    mainPage.closeSnackbar();

    browser.expect.element(signAuthTab.elements.addKeyButton).to.not.be.enabled;
    browser.expect.element(signAuthTab.elements.importCertButton).to.not.be.enabled;
    browser.expect.element(signAuthTab.elements.authGenerateCSRButton).to.not.be.enabled;
    browser.expect.element(signAuthTab.elements.signGenerateCSRButton).to.not.be.enabled;

    // Test cancel login and wrong pin
    signAuthTab.loginToken();
    browser.waitForElementVisible(loginTokenPopup);
    loginTokenPopup.enterPin('wrongpin');
    loginTokenPopup.confirm();
    browser.assert.containsText(mainPage.elements.snackBarMessage, 'Incorrect PIN. Please try again.');
    browser.assert.containsText(loginTokenPopup.elements.pinMessage, 'Incorrect PIN. Please try again.')
    mainPage.closeSnackbar();
    loginTokenPopup.cancel();

    browser.expect.element(signAuthTab.elements.addKeyButton).to.not.be.enabled;
    browser.expect.element(signAuthTab.elements.importCertButton).to.not.be.enabled;
    browser.expect.element(signAuthTab.elements.authGenerateCSRButton).to.not.be.enabled;
    browser.expect.element(signAuthTab.elements.signGenerateCSRButton).to.not.be.enabled;

    // Test login
    signAuthTab.loginToken();
    browser.waitForElementVisible(loginTokenPopup);
    loginTokenPopup.enterPin(browser.globals.token_pin);
    loginTokenPopup.confirm();
    browser.assert.containsText(mainPage.elements.snackBarMessage, 'Logged in token');
    mainPage.closeSnackbar();

    browser.expect.element(signAuthTab.elements.addKeyButton).to.be.enabled;
    browser.expect.element(signAuthTab.elements.importCertButton).to.be.enabled;
    browser.expect.element(signAuthTab.elements.authGenerateCSRButton).to.be.enabled;
    browser.expect.element(signAuthTab.elements.signGenerateCSRButton).to.be.enabled;

    // Test auth key and cert details
    signAuthTab.openAuthKeyDetails()
    browser.waitForElementVisible(authKeyDetails);
    browser.assert.value(authKeyDetails.elements.friendlyName, 'auth');
    browser.assert.containsText(authKeyDetails.elements.label, 'auth');
    authKeyDetails.cancel();

    signAuthTab.openAuthCertDetails()
    browser.waitForElementVisible(certificatePopup);
    browser.assert.containsText(certificatePopup, 'X-Road Test CA CN');
    browser.assert.containsText(certificatePopup, 'SHA256withRSA');
    browser.assert.containsText(certificatePopup, 'EE:4F:DC:AB:1B:DF:63:55:AB:8E:AC:51:E1:3D:3B:F3:24:D5:0C:6A');
    browser.assert.containsText(certificatePopup, 'SERIALNUMBER=REST-UI-TEST/ss1-vrk/GOV, CN=restuitest-ss1.i.x-road.rocks, O=VRK, C=FI');
    certificatePopup.close();


    // Test sign key and cert details
    signAuthTab.openSignKeyDetails()
    browser.waitForElementVisible(signKeyDetails);
    browser.assert.value(signKeyDetails.elements.friendlyName, 'sign');
    browser.assert.containsText(signKeyDetails.elements.label, 'sign');
    signKeyDetails.cancel();

    signAuthTab.openSignCertDetails()
    browser.waitForElementVisible(certificatePopup);
    browser.assert.containsText(certificatePopup, 'X-Road Test CA CN');
    browser.assert.containsText(certificatePopup, 'SHA256withRSA');
    browser.assert.containsText(certificatePopup, '93:7F:89:09:B0:8F:B3:DA:40:96:50:8A:24:8A:0C:E2:F8:77:AC:A7');
    browser.assert.containsText(certificatePopup, 'SERIALNUMBER=REST-UI-TEST/ss1-vrk/GOV, CN=0245437-2, O=VRK, C=FI');
    certificatePopup.close();

    browser.expect.element(signAuthTab.elements.addKeyButton).to.be.enabled;
    browser.expect.element(signAuthTab.elements.importCertButton).to.be.enabled;
    browser.expect.element(signAuthTab.elements.authGenerateCSRButton).to.be.enabled;
    browser.expect.element(signAuthTab.elements.signGenerateCSRButton).to.be.enabled;

    browser.end();
  }
};
