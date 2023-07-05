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

var fs = require('fs');

module.exports = {
  tags: ['ss', 'keyscerts', 'signauthkeys'],
  'Security server add signkey': (browser) => {
    const frontPage = browser.page.ssLoginPage();
    const mainPage = browser.page.ssMainPage();
    const keysTab = browser.page.tabs.keysTab();
    const signAuthTab = keysTab.section.signAuthKeysTab;
    const addKeyWizardDetails = keysTab.section.addKeyWizardDetails;
    const addKeyWizardCSR = keysTab.section.addKeyWizardCSR;
    const addKeyWizardGenerate = keysTab.section.addKeyWizardGenerate;

    // Delete any csr files from test dir
    let csrdir = __dirname + browser.globals.e2etest_testdata + '/';
    let regex = /^sign_csr_/;

    if (fs.existsSync(csrdir)) {
      fs.readdirSync(csrdir)
        .filter((f) => regex.test(f))
        .map((f) => fs.unlinkSync(csrdir + f));
    }
    // Open SUT and check that page is loaded
    frontPage.navigateAndMakeTestable();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Navigate to target page
    mainPage.openKeysTab();
    keysTab.openSignAndAuthKeys();
    browser.waitForElementVisible(signAuthTab);

    signAuthTab.toggleExpandToken();

    browser.waitForElementNotPresent(
      '//div[contains(@class, "clickable-link")]//span[contains(text(), "testsiglbl")]',
    );
    signAuthTab.openAddKeyWizard();
    browser.waitForElementVisible(addKeyWizardDetails);

    addKeyWizardDetails.enterLabel('testsiglbl');
    addKeyWizardDetails.next();

    browser.waitForElementVisible(addKeyWizardCSR);
    addKeyWizardCSR.selectUsageMethod('AUTHENTICATION');
    browser.waitForElementNotPresent(addKeyWizardCSR.elements.csrClient);
    addKeyWizardCSR.selectUsageMethod('SIGNING');
    browser.waitForElementVisible(addKeyWizardCSR.elements.csrClient);
    addKeyWizardCSR.selectService('X-Road Test CA CN');
    addKeyWizardCSR.selectFormat('DER');
    addKeyWizardCSR.selectClient('REST-UI-TEST:GOV:0245437-2');

    addKeyWizardCSR.next();
    browser.waitForElementVisible(addKeyWizardGenerate);
    addKeyWizardGenerate.enterOrganization('TestOrg');

    browser.expect.element(addKeyWizardGenerate.elements.doneButton).to.not.be
      .enabled;
    addKeyWizardGenerate.generateCSR();
    browser.expect.element(addKeyWizardGenerate.elements.doneButton).enabled;
    addKeyWizardGenerate.close();

    browser.waitForElementVisible(
      '//table[.//th[contains(@class, "title-col")]]//span[text()="testsiglbl"]',
    );

    // Verify that the csr file is found in the file system
    regex = /^sign_csr_.*[.]der$/;

    browser.perform(function () {
      browser.assert.equal(
        fs.readdirSync(csrdir).filter((f) => regex.test(f)).length,
        1,
        'CSR file generated successfully',
      );
    });

    browser.end();
  },
  'Security server add authkey': (browser) => {
    const frontPage = browser.page.ssLoginPage();
    const mainPage = browser.page.ssMainPage();
    const keysTab = browser.page.tabs.keysTab();
    const signAuthTab = keysTab.section.signAuthKeysTab;
    const addKeyWizardDetails = keysTab.section.addKeyWizardDetails;
    const addKeyWizardCSR = keysTab.section.addKeyWizardCSR;
    const addKeyWizardGenerate = keysTab.section.addKeyWizardGenerate;

    // Delete any csr files from test dir
    let csrdir = __dirname + browser.globals.e2etest_testdata + '/';
    let regex = /^auth_csr_/;

    if (fs.existsSync(csrdir)) {
      fs.readdirSync(csrdir)
        .filter((f) => regex.test(f))
        .map((f) => fs.unlinkSync(csrdir + f));
    }
    // Open SUT and check that page is loaded
    frontPage.navigateAndMakeTestable();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Navigate to target page
    mainPage.openKeysTab();
    keysTab.openSignAndAuthKeys();
    browser.waitForElementVisible(signAuthTab);

    signAuthTab.toggleExpandToken();
    browser.waitForElementNotPresent(
      '//div[contains(@class, "clickable-link")]//span[contains(text(), "testautlbl")]',
    );

    signAuthTab.openAddKeyWizard();
    browser.waitForElementVisible(addKeyWizardDetails);

    addKeyWizardDetails.enterLabel('testautlbl');
    addKeyWizardDetails.next();

    browser.waitForElementVisible(addKeyWizardCSR);
    addKeyWizardCSR.selectUsageMethod('AUTHENTICATION');
    browser.waitForElementNotPresent(addKeyWizardCSR.elements.csrClient);
    addKeyWizardCSR.selectService('X-Road Test CA CN');
    addKeyWizardCSR.selectFormat('DER');

    addKeyWizardCSR.next();
    browser.waitForElementVisible(addKeyWizardGenerate);

    addKeyWizardGenerate.enterOrganization('TestOrg');
    browser.waitForElementVisible(addKeyWizardGenerate.elements.serverDNS);
    addKeyWizardGenerate.enterServerDNS('foodns');

    browser.expect.element(addKeyWizardGenerate.elements.doneButton).to.not.be
      .enabled;
    addKeyWizardGenerate.generateCSR();
    browser.expect.element(addKeyWizardGenerate.elements.doneButton).enabled;
    addKeyWizardGenerate.close();

    browser.waitForElementVisible(
      '//table[.//th[contains(@class, "title-col")]]//span[contains(text(), "testautlbl")]',
    );

    // Verify that the csr file is found in the file system
    regex = /^auth_csr_.*[.]der$/;

    browser.perform(function () {
      browser.assert.equal(
        fs.readdirSync(csrdir).filter((f) => regex.test(f)).length,
        1,
        'CSR file generated successfully',
      );
    });

    browser.end();
  },
  'Security server add signkey pem/empty label': (browser) => {
    const frontPage = browser.page.ssLoginPage();
    const mainPage = browser.page.ssMainPage();
    const keysTab = browser.page.tabs.keysTab();
    const signAuthTab = keysTab.section.signAuthKeysTab;
    const signKeyDetails = keysTab.section.signKeyDetails;
    const addKeyWizardDetails = keysTab.section.addKeyWizardDetails;
    const addKeyWizardCSR = keysTab.section.addKeyWizardCSR;
    const addKeyWizardGenerate = keysTab.section.addKeyWizardGenerate;

    // Delete any csr files from test dir
    let csrdir = __dirname + browser.globals.e2etest_testdata + '/';
    let regex = /^sign_csr_/;

    if (fs.existsSync(csrdir)) {
      fs.readdirSync(csrdir)
        .filter((f) => regex.test(f))
        .map((f) => fs.unlinkSync(csrdir + f));
    }
    // Open SUT and check that page is loaded
    frontPage.navigateAndMakeTestable();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Navigate to target page
    mainPage.openKeysTab();
    keysTab.openSignAndAuthKeys();
    browser.waitForElementVisible(signAuthTab);

    signAuthTab.toggleExpandToken();

    var initialKeys;

    browser.waitForElementNotPresent(
      '//table[.//th[contains(@class, "title-col")]]//span[text()=""]',
    );

    browser.waitForElementVisible(
      '//table[./thead//th[@class="title-col"]]//div[contains(@class, "clickable-link")]',
    );

    browser.elements(
      'xpath',
      '//table[./thead//th[@class="title-col"]]//div[contains(@class, "clickable-link")]',
      function (result) {
        initialKeys = result.value.length;
      },
    );

    signAuthTab.openAddKeyWizard();
    browser.waitForElementVisible(addKeyWizardDetails);

    addKeyWizardDetails.enterLabel('');
    addKeyWizardDetails.next();

    browser.waitForElementVisible(addKeyWizardCSR);
    addKeyWizardCSR.selectUsageMethod('AUTHENTICATION');
    browser.waitForElementNotPresent(addKeyWizardCSR.elements.csrClient);
    addKeyWizardCSR.selectUsageMethod('SIGNING');
    browser.waitForElementVisible(addKeyWizardCSR.elements.csrClient);
    addKeyWizardCSR.selectService('X-Road Test CA CN');
    addKeyWizardCSR.selectFormat('PEM');
    addKeyWizardCSR.selectClient('REST-UI-TEST:GOV:0245437-2');

    addKeyWizardCSR.next();
    browser.waitForElementVisible(addKeyWizardGenerate);
    addKeyWizardGenerate.enterOrganization('TestOrg');

    browser.expect.element(addKeyWizardGenerate.elements.doneButton).to.not.be
      .enabled;
    addKeyWizardGenerate.generateCSR();
    browser.expect.element(addKeyWizardGenerate.elements.doneButton).enabled;
    addKeyWizardGenerate.close();
    browser.perform(function () {
      browser.click(
        '(//table[./thead//th[@class="title-col"]]//div[contains(@class, "clickable-link")])[' +
          (initialKeys + 1) +
          ']',
      );
    });

    browser.expect
      .element(signKeyDetails.elements.label)
      .text.to.match(/^\s*$/);

    // Verify that the csr file is found in the file system
    regex = /^sign_csr_.*[.]pem$/;

    browser.perform(function () {
      browser.assert.equal(
        fs.readdirSync(csrdir).filter((f) => regex.test(f)).length,
        1,
        'CSR file generated successfully',
      );
    });

    browser.end();
  },
  'Security server add authkey pem/empty label': (browser) => {
    const frontPage = browser.page.ssLoginPage();
    const mainPage = browser.page.ssMainPage();
    const keysTab = browser.page.tabs.keysTab();
    const signAuthTab = keysTab.section.signAuthKeysTab;
    const authKeyDetails = keysTab.section.authKeyDetails;
    const addKeyWizardDetails = keysTab.section.addKeyWizardDetails;
    const addKeyWizardCSR = keysTab.section.addKeyWizardCSR;
    const addKeyWizardGenerate = keysTab.section.addKeyWizardGenerate;

    // Delete any csr files from test dir
    let csrdir = __dirname + browser.globals.e2etest_testdata + '/';
    let regex = /^auth_csr_/;

    if (fs.existsSync(csrdir)) {
      fs.readdirSync(csrdir)
        .filter((f) => regex.test(f))
        .map((f) => fs.unlinkSync(csrdir + f));
    }
    // Open SUT and check that page is loaded
    frontPage.navigateAndMakeTestable();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Navigate to target page
    mainPage.openKeysTab();
    keysTab.openSignAndAuthKeys();
    browser.waitForElementVisible(signAuthTab);

    signAuthTab.toggleExpandToken();

    var initialKeys;

    browser.waitForElementNotPresent(
      '//table[.//th[contains(@class, "title-col"))]]//span[text()=""]',
    );

    // Get number of inital auth key are links
    browser.elements(
      'xpath',
      '//table[./thead//th[@class="title-col"]]//div[contains(@class, "clickable-link")]',
      function (result) {
        initialKeys = result.value.length;
      },
    );

    signAuthTab.openAddKeyWizard();
    browser.waitForElementVisible(addKeyWizardDetails);

    addKeyWizardDetails.enterLabel('');
    addKeyWizardDetails.next();

    browser.waitForElementVisible(addKeyWizardCSR);
    addKeyWizardCSR.selectUsageMethod('AUTHENTICATION');
    addKeyWizardCSR.selectService('X-Road Test CA CN');
    addKeyWizardCSR.selectFormat('PEM');

    addKeyWizardCSR.next();
    browser.waitForElementVisible(addKeyWizardGenerate);
    addKeyWizardGenerate.enterOrganization('TestOrg');
    addKeyWizardGenerate.enterServerDNS('foodns');

    browser.expect.element(addKeyWizardGenerate.elements.doneButton).to.not.be
      .enabled;
    addKeyWizardGenerate.generateCSR();
    browser.expect.element(addKeyWizardGenerate.elements.doneButton).enabled;
    addKeyWizardGenerate.close();

    browser.perform(function () {
      browser.click(
        '(//table[./thead//th[@class="title-col"]]//div[contains(@class, "clickable-link")])[' +
          (initialKeys + 1) +
          ']',
      );
    });

    // Check empty key label
    browser.expect
      .element(authKeyDetails.elements.label)
      .text.to.match(/^\s*$/);

    // Verify that the csr file is found in the file system
    regex = /^auth_csr_.*[.]pem$/;

    browser.perform(function () {
      browser.assert.equal(
        fs.readdirSync(csrdir).filter((f) => regex.test(f)).length,
        1,
        'CSR file generated successfully',
      );
    });

    browser.end();
  },
  'Security server generate sign key csr': (browser) => {
    const frontPage = browser.page.ssLoginPage();
    const mainPage = browser.page.ssMainPage();
    const keysTab = browser.page.tabs.keysTab();
    const signAuthTab = keysTab.section.signAuthKeysTab;
    const generateKeyCsrWizardCsr = keysTab.section.generateKeyCsrWizardCsr;
    const generateKeysCsrWizardGenerate =
      keysTab.section.generateKeyCsrWizardGenerate;
    // Delete any csr files from test dir
    let csrdir = __dirname + browser.globals.e2etest_testdata + '/';
    let regex = /^sign_csr_/;

    if (fs.existsSync(csrdir)) {
      fs.readdirSync(csrdir)
        .filter((f) => regex.test(f))
        .map((f) => fs.unlinkSync(csrdir + f));
    }
    // Open SUT and check that page is loaded
    frontPage.navigateAndMakeTestable();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Navigate to target page
    mainPage.openKeysTab();
    keysTab.openSignAndAuthKeys();
    browser.waitForElementVisible(signAuthTab);

    signAuthTab.toggleExpandToken();

    var initialRequests;

    // Get initial number of requests
    browser.elements(
      'xpath',
      '//tbody[.//div[contains(@class, "clickable-link") and .//*[contains(text(), "sign")]]]//div[contains(@class, "name-wrap")]//div[text()="Request"]',
      function (result) {
        initialRequests = result.value.length;
      },
    );

    // Test cancel
    signAuthTab.generateSignCSRForKey('sign');

    browser.waitForElementVisible(generateKeyCsrWizardCsr);
    browser.assert.containsText(
      generateKeyCsrWizardCsr.elements.csrUsage,
      'SIGNING',
    );
    browser.waitForElementVisible(generateKeyCsrWizardCsr.elements.csrClient);
    generateKeyCsrWizardCsr.selectService('X-Road Test CA CN');
    generateKeyCsrWizardCsr.selectFormat('PEM');
    generateKeyCsrWizardCsr.selectClient('REST-UI-TEST:GOV:0245437-2');
    generateKeyCsrWizardCsr.next();

    browser.waitForElementVisible(generateKeysCsrWizardGenerate);
    generateKeysCsrWizardGenerate.enterOrganization('TestOrg2');

    generateKeysCsrWizardGenerate.cancel();

    // Verify that no request has been added
    browser.perform(function () {
      browser.waitForElementNotPresent(
        '(//tbody[.//div[contains(@class, "clickable-link") and .//*[contains(text(), "sign")]]]//div[contains(@class, "name-wrap")]//div[text()="Request"])[' +
          (initialRequests + 1) +
          ']',
      );
    });

    // Test CSR generation
    signAuthTab.generateSignCSRForKey('sign');

    browser.waitForElementVisible(generateKeyCsrWizardCsr);
    browser.assert.containsText(
      generateKeyCsrWizardCsr.elements.csrUsage,
      'SIGNING',
    );
    browser.waitForElementVisible(generateKeyCsrWizardCsr.elements.csrClient);
    generateKeyCsrWizardCsr.selectService('X-Road Test CA CN');
    generateKeyCsrWizardCsr.selectFormat('PEM');
    generateKeyCsrWizardCsr.selectClient('REST-UI-TEST:GOV:0245437-2');
    generateKeyCsrWizardCsr.next();

    browser.waitForElementVisible(generateKeysCsrWizardGenerate);
    generateKeysCsrWizardGenerate.enterOrganization('TestOrg2');

    browser.expect.element(generateKeysCsrWizardGenerate.elements.doneButton).to
      .not.be.enabled;
    generateKeysCsrWizardGenerate.generateCSR();
    browser.expect.element(generateKeysCsrWizardGenerate.elements.doneButton)
      .enabled;
    generateKeysCsrWizardGenerate.close();

    // Verify that a request has been added
    browser.perform(function () {
      browser.waitForElementVisible(
        '(//tbody[.//div[contains(@class, "clickable-link") and .//*[contains(text(), "sign")]]]//div[contains(@class, "name-wrap")]//div[text()="Request"])[' +
          (initialRequests + 1) +
          ']',
      );
    });

    // Verify that the csr file is found in the file system
    regex = /^sign_csr_.*[.]pem$/;

    browser.perform(function () {
      browser.assert.equal(
        fs.readdirSync(csrdir).filter((f) => regex.test(f)).length,
        1,
        'CSR file generated successfully',
      );
    });

    browser.end();
  },
  'Security server delete sign key csr': (browser) => {
    const frontPage = browser.page.ssLoginPage();
    const mainPage = browser.page.ssMainPage();
    const keysTab = browser.page.tabs.keysTab();
    const signAuthTab = keysTab.section.signAuthKeysTab;
    const deleteCSRPopup = mainPage.section.deleteCSRPopup;

    // Open SUT and check that page is loaded
    frontPage.navigateAndMakeTestable();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Navigate to target page
    mainPage.openKeysTab();
    keysTab.openSignAndAuthKeys();
    browser.waitForElementVisible(signAuthTab);

    signAuthTab.toggleExpandToken();

    var initialRequests;

    // Get initial number of requests
    browser.elements(
      'xpath',
      '//tbody[.//div[contains(@class, "clickable-link") and .//*[contains(text(), "sign")]]]//div[contains(@class, "name-wrap")]//div[text()="Request"]',
      function (result) {
        initialRequests = result.value.length;
      },
    );

    // Test cancel, see that number does not change
    signAuthTab.deleteSignCSRForKey('sign');
    browser.waitForElementVisible(deleteCSRPopup);
    deleteCSRPopup.cancel();

    browser.perform(function () {
      browser.waitForElementVisible(
        '(//tbody[.//div[contains(@class, "clickable-link") and .//*[contains(text(), "sign")]]]//div[contains(@class, "name-wrap")]//div[text()="Request"])[' +
          initialRequests +
          ']',
      );
    });

    // Test delete, confirm that number decreases
    signAuthTab.deleteSignCSRForKey('sign');
    browser.waitForElementVisible(deleteCSRPopup);
    deleteCSRPopup.confirm();

    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'CSR deleted'
    mainPage.closeSnackbar();

    browser.perform(function () {
      browser.waitForElementNotPresent(
        '(//tbody[.//div[contains(@class, "clickable-link") and .//*[contains(text(), "sign")]]]//div[contains(@class, "name-wrap")]//div[text()="Request"])[' +
          initialRequests +
          ']',
      );
    });

    browser.end();
  },
  'Security server generate auth key csr': (browser) => {
    const frontPage = browser.page.ssLoginPage();
    const mainPage = browser.page.ssMainPage();
    const keysTab = browser.page.tabs.keysTab();
    const signAuthTab = keysTab.section.signAuthKeysTab;
    const generateKeyCsrWizardCsr = keysTab.section.generateKeyCsrWizardCsr;
    const generateKeysCsrWizardGenerate =
      keysTab.section.generateKeyCsrWizardGenerate;

    // Delete any csr files from test dir
    let csrdir = __dirname + browser.globals.e2etest_testdata + '/';
    let regex = /^auth_csr_*/;

    if (fs.existsSync(csrdir)) {
      fs.readdirSync(csrdir)
        .filter((f) => regex.test(f))
        .map((f) => fs.unlinkSync(csrdir + f));
    }
    // Open SUT and check that page is loaded
    frontPage.navigateAndMakeTestable();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Navigate to target page
    mainPage.openKeysTab();
    keysTab.openSignAndAuthKeys();
    browser.waitForElementVisible(signAuthTab);

    signAuthTab.toggleExpandToken();

    var initialRequests;

    // Get initial number of requests
    browser.elements(
      'xpath',
      '//tbody[.//div[contains(@class, "clickable-link") and .//*[contains(text(), "auth")]]]//div[contains(@class, "name-wrap")]//div[text()="Request"]',
      function (result) {
        initialRequests = result.value.length;
      },
    );

    // Test cancel

    signAuthTab.generateAuthCSRForKey('auth');

    browser.waitForElementVisible(generateKeyCsrWizardCsr);
    browser.assert.containsText(
      generateKeyCsrWizardCsr.elements.csrUsage,
      'AUTHENTICATION',
    );

    generateKeyCsrWizardCsr.selectService('X-Road Test CA CN');
    generateKeyCsrWizardCsr.selectFormat('DER');
    generateKeyCsrWizardCsr.next();

    browser.waitForElementVisible(generateKeysCsrWizardGenerate);
    generateKeysCsrWizardGenerate.enterOrganization('TestOrg3');
    generateKeysCsrWizardGenerate.enterServerDNS('foodns');

    generateKeysCsrWizardGenerate.cancel();

    // Verify that no request has been added
    browser.perform(function () {
      browser.waitForElementNotPresent(
        '(//tbody[.//div[contains(@class, "clickable-link") and .//*[contains(text(), "auth")]]]//div[contains(@class, "name-wrap")]//div[text()="Request"])[' +
          (initialRequests + 1) +
          ']',
      );
    });

    // Test CSR generation
    signAuthTab.generateAuthCSRForKey('auth');

    browser.waitForElementVisible(generateKeyCsrWizardCsr);
    browser.assert.containsText(
      generateKeyCsrWizardCsr.elements.csrUsage,
      'AUTHENTICATION',
    );
    browser.waitForElementNotPresent(
      generateKeyCsrWizardCsr.elements.csrClient,
    );
    generateKeyCsrWizardCsr.selectService('X-Road Test CA CN');
    generateKeyCsrWizardCsr.selectFormat('DER');

    generateKeyCsrWizardCsr.next();

    browser.waitForElementVisible(generateKeysCsrWizardGenerate);
    generateKeysCsrWizardGenerate.enterOrganization('TestOrg3');
    generateKeysCsrWizardGenerate.enterServerDNS('foodns');

    browser.expect.element(generateKeysCsrWizardGenerate.elements.doneButton).to
      .not.be.enabled;
    generateKeysCsrWizardGenerate.generateCSR();
    browser.expect.element(generateKeysCsrWizardGenerate.elements.doneButton)
      .enabled;
    generateKeysCsrWizardGenerate.close();

    // Verify that a request has been added
    browser.perform(function () {
      browser.waitForElementVisible(
        '(//tbody[.//div[contains(@class, "clickable-link") and .//*[contains(text(), "auth")]]]//div[contains(@class, "name-wrap")]//div[text()="Request"])[' +
          (initialRequests + 1) +
          ']',
      );
    });

    // Verify that the csr file is found in the file system
    regex = /^auth_csr_.*[.]der$/;

    browser.perform(function () {
      browser.assert.equal(
        fs.readdirSync(csrdir).filter((f) => regex.test(f)).length,
        1,
        'CSR file generated successfully',
      );
    });

    browser.end();
  },
  'Security server delete auth key csr': (browser) => {
    const frontPage = browser.page.ssLoginPage();
    const mainPage = browser.page.ssMainPage();
    const keysTab = browser.page.tabs.keysTab();
    const signAuthTab = keysTab.section.signAuthKeysTab;
    const deleteCSRPopup = mainPage.section.deleteCSRPopup;

    // Open SUT and check that page is loaded
    frontPage.navigateAndMakeTestable();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Navigate to target page
    mainPage.openKeysTab();
    keysTab.openSignAndAuthKeys();
    browser.waitForElementVisible(signAuthTab);

    signAuthTab.toggleExpandToken();

    var initialRequests;

    // Get initial number of requests
    browser.elements(
      'xpath',
      '//tbody[.//div[contains(@class, "clickable-link") and .//*[contains(text(), "auth")]]]//div[contains(@class, "name-wrap")]//div[text()="Request"]',
      function (result) {
        initialRequests = result.value.length;
      },
    );

    // Test cancel, see that number does not change
    signAuthTab.deleteAuthCSRForKey('auth');
    browser.waitForElementVisible(deleteCSRPopup);
    deleteCSRPopup.cancel();

    browser.perform(function () {
      browser.waitForElementVisible(
        '(//tbody[.//div[contains(@class, "clickable-link") and .//*[contains(text(), "auth")]]]//div[contains(@class, "name-wrap")]//div[text()="Request"])[' +
          initialRequests +
          ']',
      );
    });

    // Test delete, confirm that number decreases
    signAuthTab.deleteAuthCSRForKey('auth');
    browser.waitForElementVisible(deleteCSRPopup);
    deleteCSRPopup.confirm();

    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'CSR deleted'
    mainPage.closeSnackbar();

    browser.perform(function () {
      browser.waitForElementNotPresent(
        '(//tbody[.//div[contains(@class, "clickable-link") and .//*[contains(text(), "auth")]]]//div[contains(@class, "name-wrap")]//div[text()="Request"])[' +
          initialRequests +
          ']',
      );
    });

    browser.end();
  },
  'Security server import authkey': (browser) => {
    const frontPage = browser.page.ssLoginPage();
    const mainPage = browser.page.ssMainPage();
    const keysTab = browser.page.tabs.keysTab();
    const signAuthTab = keysTab.section.signAuthKeysTab;

    // Open SUT and check that page is loaded
    frontPage.navigateAndMakeTestable();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Navigate to target page
    mainPage.openKeysTab();
    keysTab.openSignAndAuthKeys();
    browser.waitForElementVisible(signAuthTab);

    signAuthTab.toggleExpandToken();
    browser.waitForElementNotPresent(signAuthTab.elements.initializedAuthCert);

    // Test wrong type of file
    signAuthTab.importCert(
      '/../' +
        browser.globals.e2etest_testdata +
        '/' +
        browser.globals.ss2_auth_csr,
    );
    browser.waitForElementVisible(mainPage.elements.alertMessage); // 'Invalid certificate'
    mainPage.closeAlertMessage();

    // Test wrong server cert
    signAuthTab.importCert(
      '/../' +
        browser.globals.e2etest_testdata +
        '/' +
        browser.globals.ss2_auth_cert,
    );
    browser.waitForElementVisible(mainPage.elements.alertMessage); // 'Key not found'
    mainPage.closeAlertMessage();

    signAuthTab.importCert(
      '/../' +
        browser.globals.e2etest_testdata +
        '/' +
        browser.globals.import_auth_cert,
    );
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Uploading certificate succeeded'
    mainPage.closeSnackbar();

    // Verify status of new certificate
    browser.waitForElementVisible(
      '//tr[.//td[contains(text(), "Disabled")] and .//div[contains(@class, "status-text") and contains(text(), "Saved")]]//div[contains(@class, "clickable-link")]',
    );

    browser.click(signAuthTab.elements.initializedAuthCert);

    // Verify that the correct crt was imported
    browser.waitForElementVisible(
      '//div[contains(@class, "certificate-details-wrapper") and .//span[contains(text(), "O=X-Road import")]]',
    );

    browser.end();
  },
  'Security server import signkey': (browser) => {
    const frontPage = browser.page.ssLoginPage();
    const mainPage = browser.page.ssMainPage();
    const keysTab = browser.page.tabs.keysTab();
    const signAuthTab = keysTab.section.signAuthKeysTab;

    var initialCerts;

    // Open SUT and check that page is loaded
    frontPage.navigateAndMakeTestable();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Navigate to target page
    mainPage.openKeysTab();
    keysTab.openSignAndAuthKeys();
    browser.waitForElementVisible(signAuthTab);

    signAuthTab.toggleExpandToken();

    // Get number of inital registered sign keys
    browser.elements(
      'xpath',
      '//tr[.//div[contains(@class, "clickable-link")] and .//div[contains(@class, "status-text") and contains(text(), "Registered")]]',
      function (result) {
        initialCerts = result.value.length;
      },
    );
    // Test wrong type of file
    signAuthTab.importCert(
      '/../' +
        browser.globals.e2etest_testdata +
        '/' +
        browser.globals.ss2_sign_csr,
    );
    browser.waitForElementVisible(mainPage.elements.alertMessage); // 'Invalid certificate'
    mainPage.closeAlertMessage();

    // Test wrong server cert
    signAuthTab.importCert(
      '/../' +
        browser.globals.e2etest_testdata +
        '/' +
        browser.globals.ss2_sign_cert,
    );
    browser.waitForElementVisible(mainPage.elements.alertMessage); // 'Key not found'
    mainPage.closeAlertMessage();

    signAuthTab.importCert(
      '/../' +
        browser.globals.e2etest_testdata +
        '/' +
        browser.globals.import_sign_cert,
    );
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Uploading certificate succeeded'
    mainPage.closeSnackbar();

    // Verify that one certificate has been added (position is not known for certain though)
    browser.perform(function () {
      browser.waitForElementVisible(
        '(//tr[.//div[contains(@class, "clickable-link")] and .//div[contains(@class, "status-text") and contains(text(), "Registered")]])[' +
          (initialCerts + 1) +
          ']',
      );
    });

    // Open imported certificate
    browser.perform(function () {
      browser.click(
        '//div[contains(@class, "clickable-link") and contains(text(), "X-Road Test CA CN 20")]'
      );
    });

    // Verify that the correct crt was imported
    browser.waitForElementVisible(
      '//div[contains(@class, "certificate-details-wrapper") and .//span[contains(text(), "O=X-Road Import")]]',
    );

    browser.end();
  },
};
