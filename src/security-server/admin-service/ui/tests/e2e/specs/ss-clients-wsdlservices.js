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

let frontPage, mainPage, clientsTab, clientInfo, clientServices, operationDetails, sslCheckFail;
let addSubjectsPopup, removeAccessRightPopup, removeAllAccessRightsPopup, serviceChangePopup, serviceDetails,servicesPopup ;

module.exports = {
  tags: ['ss', 'clients', 'wsdlservices'],
  before: function (browser) {
    frontPage = browser.page.ssLoginPage();
    mainPage = browser.page.ssMainPage();
    clientsTab = mainPage.section.clientsTab;
    clientInfo = mainPage.section.clientInfo;
    clientServices = clientInfo.section.services;
    operationDetails = mainPage.section.wsdlOperationDetails;
    sslCheckFail = mainPage.section.sslCheckFailDialog;
    addSubjectsPopup = mainPage.section.wsdlAddSubjectsPopup;
    removeAccessRightPopup = mainPage.section.removeAccessRightPopup;
    removeAllAccessRightsPopup = mainPage.section.removeAllAccessRightsPopup;
    serviceChangePopup = mainPage.section.servicesWarningPopup;
    serviceDetails = mainPage.section.wsdlServiceDetails;
    servicesPopup = mainPage.section.servicesWarningPopup;

    browser.logMessage('copy remote wsdl testservice1.wsdl -> testserviceX.wsdl',);
    browser.page.ssMainPage().updateWSDLFileTo('testservice1.wsdl');
  },
  beforeEach: function (browser) {
    browser.LoginCommand();
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    },

  afterEach: function (browser) {
    mainPage.logout();
  },
  after: function (browser) {
    browser.end();
  },



  'Invalid URLs give proper error message': (browser) => {
    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);

    clientServices.openAddWSDL();
    clientServices.initServiceUrl('a');
    clientServices.errorMessageIsShown('URL is not valid');
    clientServices.modifyServiceUrl('');
    clientServices.errorMessageIsShown('The URL field is required');
    clientServices.initServiceUrl('foobar');
    clientServices.errorMessageIsShown('URL is not valid');
    clientServices.cancelAddDialog();
    },

  'Security server client add wsdl service': (browser) => {
    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);
    // Verify that URL field is empty after reopening
    clientServices.openAddWSDL();
    browser.assert.value(clientServices.elements.newServiceUrl, '');

    // Verify opening nonexisting URL
    clientServices.initServiceUrl('https://www.niis.org/nosuch.wsdl');
    clientServices.confirmAddDialog();
    browser.waitForElementVisible(mainPage.elements.alertMessage); // 'WSDL download failed'
    mainPage.closeAlertMessage();

    // Verify successful URL open
    clientServices.openAddWSDL();
    clientServices.initServiceUrl(
      browser.globals.testdata + '/' + browser.globals.wsdl_url_1,
    );
    clientServices.confirmAddDialog();
    browser.assert.containsText(
      clientServices.elements.serviceDescription,
      browser.globals.testdata + '/' + browser.globals.wsdl_url_1,
    );

    clientServices.expandServiceDetails();
    browser.waitForElementVisible(
      '//td[@data-test="service-link" and contains(text(),"testOp1")]',
    );
  },
  'Security server client edit wsdl operation': (browser) => {
    // Navigate
    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);

    clientServices.expandServiceDetails();
    clientServices.openOperation('testOp1');
    operationDetails.close();
    clientServices.openOperation('testOp1');
    browser.waitForElementVisible(operationDetails);

    // Verify cancel
    operationDetails.toggleCertVerification();
    operationDetails.modifyUrl('https://www.niis.org/nosuch2/');
    operationDetails.modifyTimeout('40');
    operationDetails.toggleVerifyCertApplyToAll();
    operationDetails.toggleUrlApplyToAll();
    operationDetails.toggleTimeoutApplyToAll();
    operationDetails.close();

    // Verify that options were not changed
    browser.assert.containsText(
      clientServices.elements.operationUrl,
      'https://www.niis.org/nosuch1/',
    );
    browser.waitForElementVisible(
      '//tr[.//td[@data-test="service-link" and contains(text(), "testOp1")]]//*[contains(@class, "mdi-lock") and contains(@style, "' +
        browser.globals.service_ssl_auth_on_style +
        '")]',
    );
    browser.waitForElementVisible(
      '//tr[.//td[@data-test="service-link" and contains(text(), "testOpA")]]//*[contains(@class, "mdi-lock") and contains(@style, "' +
        browser.globals.service_ssl_auth_on_style +
        '")]',
    );
    clientServices.openOperation('testOp1');
    browser.waitForElementVisible(operationDetails);
    browser.assert.valueContains(
      operationDetails.elements.serviceURL,
      'https://www.niis.org/nosuch1/',
    );
    browser.assert.valueContains(operationDetails.elements.timeout, '60');
    browser.expect.element(operationDetails.elements.sslAuth).to.be.selected;

    // Verify change single operation
    operationDetails.modifyUrl('https://www.niis.org/nosuch2/');
    operationDetails.modifyTimeout('40');
    browser.expect.element(operationDetails.elements.sslAuth).to.be.not
      .selected;
    operationDetails.saveParameters();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Service saved'
    mainPage.closeSnackbar();
    operationDetails.close();

    browser.waitForElementVisible(
      '//tr[.//td[@data-test="service-link" and contains(text(), "testOp1")]]//*[contains(text(), "https://www.niis.org/nosuch2/")]',
    );
    browser.waitForElementVisible(
      '//tr[.//td[@data-test="service-link" and contains(text(), "testOpA")]]//*[contains(text(), "https://www.niis.org/nosuch1/")]',
    );
    browser.waitForElementVisible(
      '//tr[.//td[@data-test="service-link" and contains(text(), "testOp1")]]//*[contains(@class, "mdi-lock") and contains(@style, "' +
        browser.globals.service_ssl_auth_off_style +
        '")]',
    );
    browser.waitForElementVisible(
      '//tr[.//td[@data-test="service-link" and contains(text(), "testOpA")]]//*[contains(@class, "mdi-lock") and contains(@style, "' +
        browser.globals.service_ssl_auth_on_style +
        '")]',
    );

    clientServices.openOperation('testOp1');
    browser.waitForElementVisible(operationDetails);
    browser.assert.valueContains(
      operationDetails.elements.serviceURL,
      'https://www.niis.org/nosuch2/',
    );
    browser.assert.valueContains(operationDetails.elements.timeout, '40');
    browser.expect.element(operationDetails.elements.sslAuth).to.be.not
      .selected;
    operationDetails.close();

    clientServices.openOperation('testOpA');
    browser.waitForElementVisible(operationDetails);
    browser.assert.valueContains(
      operationDetails.elements.serviceURL,
      'https://www.niis.org/nosuch1/',
    );
    browser.assert.valueContains(operationDetails.elements.timeout, '60');
    browser.expect.element(operationDetails.elements.sslAuth).to.be.selected;
    operationDetails.close();

    // Verify change all operations
    clientServices.openOperation('testOpA');
    browser.waitForElementVisible(operationDetails);
    operationDetails.toggleUrlApplyToAll();
    operationDetails.toggleTimeoutApplyToAll();
    operationDetails.toggleVerifyCertApplyToAll();
    operationDetails.modifyUrl('https://www.niis.org/nosuch3/');
    operationDetails.modifyTimeout('30');
    operationDetails.toggleCertVerification();
    operationDetails.saveParameters();
    browser.waitForElementVisible(sslCheckFail);
    browser.expect
      .element(sslCheckFail.elements.yesButton)
      .to.be.visible.and.text.to.equal('Continue');
    sslCheckFail.confirm();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Service saved'
    mainPage.closeSnackbar();
    operationDetails.close();

    browser.waitForElementVisible(
      '//tr[.//td[@data-test="service-link" and contains(text(), "testOp1")]]//*[contains(text(), "https://www.niis.org/nosuch3/")]',
    );
    browser.waitForElementVisible(
      '//tr[.//td[@data-test="service-link" and contains(text(), "testOpA")]]//*[contains(text(), "https://www.niis.org/nosuch3/")]',
    );
    browser.waitForElementVisible(
      '//tr[.//td[@data-test="service-link" and contains(text(), "testOp1")]]//*[contains(@class, "mdi-lock") and contains(@style, "' +
        browser.globals.service_ssl_auth_on_style +
        '")]',
    );
    browser.waitForElementVisible(
      '//tr[.//td[@data-test="service-link" and contains(text(), "testOpA")]]//*[contains(@class, "mdi-lock") and contains(@style, "' +
        browser.globals.service_ssl_auth_on_style +
        '")]',
    );

    clientServices.openOperation('testOp1');
    browser.waitForElementVisible(operationDetails);
    browser.assert.valueContains(
      operationDetails.elements.serviceURL,
      'https://www.niis.org/nosuch3/',
    );
    browser.assert.valueContains(operationDetails.elements.timeout, '30');
    browser.expect.element(operationDetails.elements.sslAuth).to.be.selected;
    operationDetails.close();

    clientServices.openOperation('testOpA');
    browser.waitForElementVisible(operationDetails);
    browser.assert.valueContains(
      operationDetails.elements.serviceURL,
      'https://www.niis.org/nosuch3/',
    );
    browser.assert.valueContains(operationDetails.elements.timeout, '30');
    browser.expect.element(operationDetails.elements.sslAuth).to.be.selected;
    operationDetails.close();
    },
  'Security server client add wsdl operation access rights': (browser) => {
    // Navigate
    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);

    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);

    clientServices.expandServiceDetails();
    clientServices.openOperation('testOp1');
    browser.waitForElementVisible(operationDetails);

    operationDetails.openAddAccessRights();
    browser.waitForElementVisible(addSubjectsPopup);

    // Verify cancel
    addSubjectsPopup.startSearch();
    addSubjectsPopup.selectSubject('TestCom');
    addSubjectsPopup.cancel();
    browser.waitForElementNotPresent(
      '//table[contains(@class, "group-members-table")]//td[contains(text(), "TestCom")]',
    );

    // Verify add
    operationDetails.openAddAccessRights();
    browser.waitForElementVisible(addSubjectsPopup);
    addSubjectsPopup.startSearch();
    addSubjectsPopup.selectSubject('TestOrg');
    addSubjectsPopup.selectSubject('Security server owners');
    addSubjectsPopup.selectSubject('Group1');
    addSubjectsPopup.addSelected();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Access rights added successfully'
    mainPage.closeSnackbar();

    browser.waitForElementVisible(
      '//table[contains(@class, "group-members-table")]//td[contains(text(), "TestOrg")]',
    );
    browser.waitForElementVisible(
      '//table[contains(@class, "group-members-table")]//td[contains(text(), "Security server owners")]',
    );
    browser.waitForElementVisible(
      '//table[contains(@class, "group-members-table")]//td[contains(text(), "Group1")]',
    );
    browser.waitForElementNotPresent(
      '//table[contains(@class, "group-members-table")]//td[contains(text(), "TestCom")]',
    );

  },
  'Security server client remove wsdl operation access rights': (browser) => {
    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);

    clientServices.expandServiceDetails();
    clientServices.openOperation('testOp1');
    browser.waitForElementVisible(operationDetails);

    // Verify cancel remove
    operationDetails.removeAccessRight('TestOrg');
    browser.waitForElementVisible(removeAccessRightPopup);
    removeAccessRightPopup.cancel();
    clientServices.groupMemberTableContains('TestOrg');

    // Verify remove
    operationDetails.removeAccessRight('TestOrg');
    browser.waitForElementVisible(removeAccessRightPopup);
    removeAccessRightPopup.confirm();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Access rights removed successfully'
    mainPage.closeSnackbar();
    browser.waitForElementNotPresent(mainPage.elements.snackBarMessage);
    browser.waitForElementNotPresent(
      '//table[contains(@class, "group-members-table")]//td[contains(text(), "TestOrg")]',
    );
    clientServices.groupMemberTableContains('Security server owners');
    clientServices.groupMemberTableContains('Group1');


    // Verify cancel remove all
    operationDetails.removeAllAccessRights();
    browser.waitForElementVisible(removeAllAccessRightsPopup);
    removeAllAccessRightsPopup.cancel();
    clientServices.groupMemberTableContains('Security server owners');
    clientServices.groupMemberTableContains('Group1');

    // Verify remove all
    operationDetails.removeAllAccessRights();
    browser.waitForElementVisible(removeAllAccessRightsPopup);
    removeAllAccessRightsPopup.confirm();

    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Access rights removed successfully'
    mainPage.closeSnackbar();
    browser.waitForElementNotPresent(
      '//table[contains(@class, "group-members-table")]//td[contains(text(), "Security server owners")]',
    );
    browser.waitForElementNotPresent(
      '//table[contains(@class, "group-members-table")]//td[contains(text(), "Group1")]',
    );
  },
  'Security server client edit wsdl service': async (browser) => {
    var startTime, startTimestamp;

    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);

    clientServices.expandServiceDetails();
    clientServices.refreshServiceData();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Refreshed'
    mainPage.closeSnackbar();

    browser.getText(
      clientServices.elements.refreshTimestamp,
      function (result) {
        startTimestamp = result.value;
        startTime = new Date().getTime();
      },
    );

    // Verify enabling
    browser.logMessage('enabling service...');
    clientServices.toggleEnabled();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Service description enabled'
    mainPage.closeSnackbar();
    browser.logMessage('snackbar closed');
    browser.logMessage('enabling service done, snackbar should be closed now');

    // Verify disabling and canceling disable
    browser.logMessage('disabling service');
    clientServices.toggleEnabled();
    browser.logMessage('waiting for disable message popup');
    browser.waitForElementVisible(
      '//div[@data-test="dialog-simple" and .//span[@data-test="dialog-title"]]',
    );
    clientServices.initDisableNotice('Message1');
    browser.logMessage('entered disable notice, cancelling');
    clientServices.cancelDisable();
    clientServices.toggleEnabled();
    browser.waitForElementVisible(
      '//div[@data-test="dialog-simple" and .//span[@data-test="dialog-title"]]',
    );
    browser.assert.value(clientServices.elements.disableNotice, '');
    clientServices.initDisableNotice('Notice1');
    clientServices.confirmDisable();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Service description disabled'
    mainPage.closeSnackbar();

    clientServices.toggleEnabled();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Service description enabled'
    mainPage.closeSnackbar();

    // Verify editing, malformed URL
    clientServices.openServiceDetails();
    serviceDetails.modifyServiceUrl('');
    // Verify there's an error message, something like 'The URL field is required'
    browser.waitForElementVisible(serviceDetails.elements.URLMessage);
    serviceDetails.initServiceUrl('foobar');
    // Verify there's an error message, something like 'URL is not valid'
    browser.waitForElementVisible(serviceDetails.elements.URLMessage);

    // verify missing file
    serviceDetails.modifyServiceUrl('https://www.niis.org/nosuch.wsdl');
    serviceDetails.confirmDialog();
    browser.waitForElementVisible(mainPage.elements.alertMessage, 20000); //  'WSDL download failed', loading a missing file can sometimes take more time before failing
    mainPage.closeAlertMessage();

    // Part 1 wait until at least 1 min has passed since refresh at the start of the test
    // Split this wait into two parts to not cause timeouts
    browser.logMessage(
      'Starting (part 1) artificial wait to make refresh timestamps differ',
    );

    await browser.perform(function () {
      const endTime = new Date().getTime();
      const passedTime = endTime - startTime;
      if (passedTime < 30000) {
        console.log('Waiting', 30000 - passedTime, 'ms');
        browser.pause(30000 - passedTime);
      }
    });

    // Verify cancel
    browser.logMessage('Edit and cancel nosuch.wsdl -> testservice2.wsdl');
    serviceDetails.modifyServiceUrl(
      browser.globals.testdata + '/' + browser.globals.wsdl_url_2,
    );
    serviceDetails.cancelDialog();
    browser.assert.containsText(
      clientServices.elements.serviceDescription,
      'WSDL (' +
        browser.globals.testdata +
        '/' +
        browser.globals.wsdl_url_1 +
        ')',
    );

    // Verify succesful edit
    clientServices.openServiceDetails();
    browser.logMessage('Edit and confirm nosuch.wsdl -> testservice2.wsdl');
    serviceDetails.modifyServiceUrl(
      browser.globals.testdata + '/' + browser.globals.wsdl_url_2,
    );
    serviceDetails.confirmDialog();
    browser.waitForElementVisible(servicesPopup);

    // Part 2 wait until at least 1 min has passed since refresh at the start of the test
    browser.logMessage(
      'Starting (part 2) artificial wait to make refresh timestamps differ',
    );
    await browser.perform(function () {
      const endTime = new Date().getTime();
      const passedTime = endTime - startTime;
      if (passedTime < 60000) {
        console.log('Waiting', 60000 - passedTime, 'ms');
        browser.pause(60000 - passedTime);
      }
    });

    servicesPopup.accept();

    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Description saved'
    mainPage.closeSnackbar();
    browser.assert.containsText(
      clientServices.elements.serviceDescription,
      'WSDL (' +
        browser.globals.testdata +
        '/' +
        browser.globals.wsdl_url_2 +
        ')',
    );
    browser.waitForElementNotPresent(
      '//td[@data-test="service-link" and contains(text(),"testOp1")]',
    );
    browser.waitForElementVisible(
      '//td[@data-test="service-link" and contains(text(),"testOp2")]',
    );

    // Verify that the refresh time has been updated
    browser.perform(function () {
      browser.expect
        .element(clientServices.elements.refreshTimestamp)
        .text.to.not.contain(startTimestamp);
    });

  },
  'Security server client delete wsdl service': (browser) => {
    // Open TestGov Internal Servers
    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);

    // Verify cancel delete
    clientServices.openServiceDetails();
    browser.waitForElementVisible(serviceDetails);
    serviceDetails.deleteService();
    serviceDetails.cancelDelete();

    serviceDetails.closeServiceDetails();
    browser.assert.containsText(
      clientServices.elements.serviceDescription,
      'WSDL (' +
        browser.globals.testdata +
        '/' +
        browser.globals.wsdl_url_2 +
        ')',
    );

    // Verify successful delete
    clientServices.openServiceDetails();
    serviceDetails.deleteService();
    serviceDetails.confirmDelete();
    browser.logMessage('delete was confirmed');

    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Service description deleted'
    mainPage.closeSnackbar();

    browser.waitForElementNotPresent(
      clientServices.elements.serviceDescription,
    );
  },
  'Security server client refresh wsdl service': (browser) => {
    var startTime, startTimestamp;

    // Open SUT and check that page is loaded
    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);

    // Verify successfull URL open
    clientServices.openAddWSDL();
    clientServices.initServiceUrl(
      browser.globals.testdata + '/' + browser.globals.wsdl_url_x,
    );
    clientServices.confirmAddDialog();
    browser.assert.containsText(
      mainPage.elements.snackBarMessage,
      'WSDL added',
    );
    mainPage.closeSnackbar();

    browser.assert.containsText(
      clientServices.elements.serviceDescription,
      browser.globals.testdata + '/' + browser.globals.wsdl_url_x,
    );

    clientServices.expandServiceDetails();
    browser.waitForElementVisible(
      '//td[contains(@data-test, "service-link") and contains(text(),"testOp1")]',
    );
    browser.waitForElementVisible(
      '//td[contains(@data-test, "service-link") and contains(text(),"testOpA")]',
    );

    browser.getText(
      clientServices.elements.refreshTimestamp,
      function (result) {
        startTimestamp = result.value;
        startTime = new Date().getTime();
      },
    );

    clientServices.openOperation('testOpA');
    browser.waitForElementVisible(operationDetails);
    operationDetails.toggleUrlApplyToAll();
    operationDetails.toggleTimeoutApplyToAll();
    operationDetails.toggleVerifyCertApplyToAll();
    operationDetails.modifyUrl('https://www.niis.org/nosuch3/');
    operationDetails.modifyTimeout('30');
    operationDetails.saveParameters();
    browser.assert.containsText(
      mainPage.elements.snackBarMessage,
      'Service saved',
    );
    browser.logMessage('closing snackbar');
    mainPage.closeSnackbar();

    // Part 1 wait until at least 1 min has passed since refresh at the start of the test
    // Split this wait into two parts to not cause timeouts
    browser.logMessage(
      'Part 1 wait until at least 1 min has passed since refresh at the start of the test',
    );
    browser.perform(function () {
      const endTime = new Date().getTime();
      const passedTime = endTime - startTime;
      if (passedTime < 30000) {
        console.log('Waiting', 30000 - passedTime, 'ms');
        browser.pause(30000 - passedTime);
      }
    });
    operationDetails.close();

    clientServices.verifyServiceURL('testOp1', 'https://www.niis.org/nosuch3/');
    clientServices.verifyServiceURL('testOpA', 'https://www.niis.org/nosuch3/');

    clientServices.verifyServiceSSL(
      'testOp1',
      browser.globals.service_ssl_auth_off_style,
    );
    clientServices.verifyServiceSSL(
      'testOpA',
      browser.globals.service_ssl_auth_off_style,
    );

    // change the wsdl and refresh
    browser.logMessage(
      'copy remote wsdl testservice3.wsdl -> testserviceX.wsdl',
    );
    browser.perform(function () {
      browser.page.ssMainPage().updateWSDLFileTo('testservice3.wsdl');
    });

    // Part 2 wait until at least 1 min has passed since refresh at the start of the test
    browser.logMessage(
      'Part 2 wait until at least 1 min has passed since refresh at the start of the test',
    );
    browser.perform(function () {
      const endTime = new Date().getTime();
      const passedTime = endTime - startTime;
      if (passedTime < 60000) {
        console.log('Waiting', 60000 - passedTime, 'ms');
        browser.pause(60000 - passedTime);
      }
    });

    // test cancel
    clientServices.refreshServiceData();
    browser.waitForElementVisible(serviceChangePopup);
    browser.assert.containsText(
      serviceChangePopup.elements.addedServices,
      'testOpZ',
    );
    browser.assert.containsText(
      serviceChangePopup.elements.deletedServices,
      'testOp1',
    );

    serviceChangePopup.cancel();

    clientServices.verifyServiceURL('testOp1', 'https://www.niis.org/nosuch3/');
    clientServices.verifyServiceURL('testOpA', 'https://www.niis.org/nosuch3/');
    clientServices.verifyServiceSSL(
      'testOp1',
      browser.globals.service_ssl_auth_off_style,
    );
    clientServices.verifyServiceSSL(
      'testOpA',
      browser.globals.service_ssl_auth_off_style,
    );

    // Verify that the refresh time has not been updated
    browser.perform(function () {
      browser.expect
        .element(clientServices.elements.refreshTimestamp)
        .text.to.contain(startTimestamp);
    });

    // test success
    clientServices.refreshServiceData();
    browser.waitForElementVisible(serviceChangePopup);
    browser.assert.containsText(
      serviceChangePopup.elements.addedServices,
      'testOpZ',
    );
    browser.assert.containsText(
      serviceChangePopup.elements.deletedServices,
      'testOp1',
    );

    serviceChangePopup.accept();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Refreshed'
    mainPage.closeSnackbar();

    // Verify that displayed services have changed
    browser.waitForElementVisible(
      '//td[contains(@data-test, "service-link") and contains(text(),"testOpA")]',
    );
    browser.waitForElementVisible(
      '//td[contains(@data-test, "service-link") and contains(text(),"testOpZ")]',
    );
    browser.waitForElementNotPresent(
      '//td[contains(@data-test, "service-link") and contains(text(),"testOp1")]',
    );

    // check that values have not changed when service remains
    clientServices.verifyServiceURL('testOpA', 'https://www.niis.org/nosuch3/');
    clientServices.verifyServiceURL('testOpZ', 'https://www.niis.org/nosuch1/');
    clientServices.verifyServiceSSL(
      'testOpA',
      browser.globals.service_ssl_auth_off_style,
    );
    clientServices.verifyServiceSSL(
      'testOpZ',
      browser.globals.service_ssl_auth_on_style,
    );

    // Verify that the refresh time has been updated
    browser.perform(function () {
      browser.expect
        .element(clientServices.elements.refreshTimestamp)
        .text.to.not.contain(startTimestamp);
    });
  },
};
