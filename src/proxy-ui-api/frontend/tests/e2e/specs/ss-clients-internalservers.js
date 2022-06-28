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
  tags: ['ss', 'clients', 'internalservers'],
  'Security server client internal servers page': (browser) => {
    const frontPage = browser.page.ssLoginPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const certificateDetails = mainPage.section.certificateDetails;
    const deletePopup = mainPage.section.deleteCertPopup;
    const clientInternalServers = clientInfo.section.internalServers;

    // Delete old test file
    try {
      fs.unlinkSync(
        __dirname +
          browser.globals.e2etest_testdata +
          '/' +
          browser.globals.export_cert,
      );
    } catch (err) {
      if (err && err.code == 'ENOENT') {
        // no file to delete, do nothing
      } else {
        throw err;
      }
    }

    // Open SUT and check that page is loaded
    frontPage.navigateAndMakeTestable();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Test owner
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openClient('TestGov');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openInternalServersTab();
    browser.waitForElementVisible(clientInternalServers);
    browser.assert.containsText(
      clientInternalServers.elements.connectionTypeMenu,
      'HTTPS NO AUTH',
    );

    // Open TestService Internal Servers
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openClient('TestSub');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openInternalServersTab();
    browser.waitForElementVisible(clientInternalServers);

    // Change connection type
    browser.assert.containsText(
      clientInternalServers.elements.connectionTypeMenu,
      'HTTPS',
    );
    browser.assert.not.containsText(
      clientInternalServers.elements.connectionTypeMenu,
      'HTTPS NO AUTH',
    );
    clientInternalServers.selectConnectionType('HTTP');
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Connection type updated'
    mainPage.closeSnackbar();
    browser.assert.containsText(
      clientInternalServers.elements.connectionTypeMenu,
      'HTTP',
    );
    browser.assert.not.containsText(
      clientInternalServers.elements.connectionTypeMenu,
      'HTTPS',
    );

    // Add certificate
    clientInternalServers.addCert(
      browser.globals.e2etest_testdata + '/' + browser.globals.test_cert,
    );
    browser.assert.containsText(
      clientInternalServers.elements.tlsCertificate,
      '29:F4:6E:58:F2:ED:A0:6A:AC:37:10:95:35:F8:7A:79:B6:C3:70:0E',
    );
    clientInternalServers.addCert(
      browser.globals.e2etest_testdata + '/' + browser.globals.test_cert,
    );
    browser.waitForElementVisible(mainPage.elements.alertMessage); // 'Certificate already exists'

    browser.logMessage('closing alertMessage now');

    mainPage.closeAlertMessage();

    // Open and verify certificate info
    clientInternalServers.openTLSCert();
    browser.waitForElementVisible(certificateDetails);

    browser.assert.containsText(
      certificateDetails,
      'CN=restuitest-ss1.i.x-road.rocks',
    );
    browser.assert.containsText(certificateDetails, 'SHA256withRSA');
    browser.assert.containsText(
      certificateDetails,
      '29:F4:6E:58:F2:ED:A0:6A:AC:37:10:95:35:F8:7A:79:B6:C3:70:0E',
    );

    // Delete cert
    certificateDetails.deleteCert();
    browser.waitForElementVisible(certificateDetails);
    deletePopup.confirm();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Certificate deleted'
    mainPage.closeSnackbar();
    browser.waitForElementNotPresent(
      clientInternalServers.elements.tlsCertificate,
    );

    // Export certificate
    browser.assert.equal(
      fs.existsSync(
        __dirname +
          browser.globals.e2etest_testdata +
          '/' +
          browser.globals.export_cert,
      ),
      false,
      'Export output file should not exist before export',
    );
    clientInternalServers.exportCert();

    browser.perform(function () {
      browser.assert.equal(
        fs.existsSync(
          __dirname +
            browser.globals.e2etest_testdata +
            '/' +
            browser.globals.export_cert,
        ),
        true,
        'Export file downloaded successfully',
      );
    });

    browser.end();
  },
};
