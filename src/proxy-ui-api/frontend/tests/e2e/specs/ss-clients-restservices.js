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

let addEndpointPopup, addSubjectsPopup, clientInfo, clientServices, clientsTab, endpointPopup, frontPage, mainPage;
let operationDetails, removeAccessRightPopup, removeAllAccessRightsPopup, restEndpoints, restServiceDetails, sslCheckFail;

module.exports = {
  tags: ['ss', 'clients', 'restservices'],

  before: function (browser) {
    frontPage = browser.page.ssLoginPage();
    mainPage = browser.page.ssMainPage();
    addEndpointPopup = mainPage.section.addEndpointPopup;
    addSubjectsPopup = mainPage.section.wsdlAddSubjectsPopup;
    clientInfo = mainPage.section.clientInfo;
    clientServices = clientInfo.section.services;
    clientsTab = mainPage.section.clientsTab;
    endpointPopup = mainPage.section.editEndpointPopup;
    operationDetails = mainPage.section.restOperationDetails;
    removeAccessRightPopup = mainPage.section.removeAccessRightPopup;
    removeAllAccessRightsPopup = mainPage.section.removeAllAccessRightsPopup;
    restEndpoints = mainPage.section.restServiceEndpoints;
    restServiceDetails = mainPage.section.restServiceDetails;
    sslCheckFail = mainPage.section.sslCheckFailDialog;
  },

  beforeEach: function (browser) {
    browser.LoginCommand();
  },

  afterEach: function (browser) {
    mainPage.logout()
  },
  after: function (browser) {
    browser.end();
  },
  'Security server client add rest service': (browser) => {
    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);
    // Verify empty and malformed URL and service code error messages and Add button initial state
    clientServices.openAddREST();
    browser.expect.element(clientServices.elements.confirmAddServiceButton).to
      .not.be.enabled;
    clientServices.initServiceUrl('a');
    clientServices.modifyServiceUrl('');
    // Verify there's an error message, something like 'The URL field is required'
    browser.waitForElementVisible(
      '//div[contains(@class, "v-messages__message")]',
    );
    clientServices.initServiceUrl('foobar');
    // Verify there's an error message, something like 'URL is not valid'
    browser.waitForElementVisible(
      '//div[contains(@class, "v-messages__message")]',
    );
    clientServices.initServiceCode('a');
    clientServices.modifyServiceCode('');
    // Verify there's an error message, something like 'The Service Code field is required'
    browser.waitForElementVisible(
      '//div[contains(@class, "v-messages__message")]',
    );
    clientServices.initServiceCode('s1c1');
    clientServices.selectRESTPath();
    clientServices.cancelAddDialog();

    // Verify that fields are empty after reopening
    clientServices.openAddREST();
    browser.assert.value(clientServices.elements.newServiceUrl, '');
    browser.assert.value(clientServices.elements.newServiceCode, '');
    browser.expect.element(clientServices.elements.RESTPathRadioButton).to.not
      .be.selected;
    browser.expect.element(clientServices.elements.OpenApiRadioButton).to.not.be
      .selected;
    browser.expect.element(clientServices.elements.confirmAddServiceButton).to
      .not.be.enabled;

    // Verify invalid service code
    clientServices.selectRESTPath();
    clientServices.initServiceUrl(
      browser.globals.testdata + '/' + browser.globals.rest_url_1,
    );
    clientServices.initServiceCode('/');
    browser.expect.element(clientServices.elements.confirmAddServiceButton).to
      .not.be.enabled;
    // Verify there's an error message, something like 'Identifier value contains illegal characters'
    browser.waitForElementVisible(
      '//div[contains(@class, "v-messages__message")]',
    );
    clientServices.cancelAddDialog();

    // Verify successful URL open
    clientServices.openAddREST();
    clientServices.initServiceUrl(
      browser.globals.testdata + '/' + browser.globals.rest_url_1,
    );
    clientServices.selectRESTPath();
    clientServices.initServiceCode('s1c1');
    clientServices.confirmAddDialog();

    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'REST service added'
    mainPage.closeSnackbar();
    browser.assert.containsText(
      clientServices.elements.serviceDescription,
      'REST (' +
        browser.globals.testdata +
        '/' +
        browser.globals.rest_url_1 +
        ')',
    );

    clientServices.expandServiceDetails();
    browser.waitForElementVisible(
      '//td[@data-test="service-link" and contains(text(),"s1c1")]',
    );
  },
  'Security server client edit rest operation': (browser) => {
    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);

    clientServices.expandServiceDetails();
    clientServices.openOperation('s1c1');


    // Verify cancel
    browser.expect.element(operationDetails.elements.sslAuth).to.not.be
      .selected;
    operationDetails.modifyUrl('https://niis.org/nosuch/api/');
    operationDetails.modifyTimeout('40');
    operationDetails.toggleCertVerification();
    browser.expect.element(operationDetails.elements.sslAuth).to.be.selected;
    operationDetails.close();

    // Verify that options were not changed
    browser.assert.containsText(
      clientServices.elements.operationUrl,
      browser.globals.testdata + '/' + browser.globals.rest_url_1,
    );
    browser.waitForElementVisible(
      '//tr[.//td[@data-test="service-link" and contains(text(), "s1c1")]]//*[contains(@class, "mdi-lock-open-outline")]',
    );
    clientServices.openOperation('s1c1');
    browser.waitForElementVisible(operationDetails);
    browser.assert.valueContains(
      operationDetails.elements.serviceURL,
      browser.globals.testdata + '/' + browser.globals.rest_url_1,
    );
    browser.assert.valueContains(operationDetails.elements.timeout, '60');
    browser.expect.element(operationDetails.elements.sslAuth).to.not.be
      .selected;

    // verify SSL states
    operationDetails.modifyUrl('https://nosuchresttestservice.exists');
    browser.expect.element(operationDetails.elements.sslAuth).to.be.not
      .selected;
    operationDetails.saveParameters();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Service saved'
    mainPage.closeSnackbar();
    operationDetails.close();
    browser.waitForElementVisible(
      '//tr[.//td[@data-test="service-link" and contains(text(), "s1c1")]]//*[contains(@class, "mdi-lock") and contains(@style, "' +
        browser.globals.service_ssl_auth_off_style +
        '")]',
    );

    clientServices.openOperation('s1c1');
    browser.waitForElementVisible(operationDetails);
    browser.expect.element(operationDetails.elements.sslAuth).to.be.not
      .selected;
    operationDetails.toggleCertVerification();
    browser.expect.element(operationDetails.elements.sslAuth).to.be.selected;
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
      '//tr[.//td[@data-test="service-link" and contains(text(), "s1c1")]]//*[contains(@class, "mdi-lock") and contains(@style, "' +
        browser.globals.service_ssl_auth_on_style +
        '")]',
    );
    clientServices.openOperation('s1c1');
    browser.waitForElementVisible(operationDetails);
    browser.expect.element(operationDetails.elements.sslAuth).to.be.selected;

    // Verify change operation
    operationDetails.modifyUrl(
      browser.globals.testdata + '/' + browser.globals.rest_url_2,
    );
    operationDetails.modifyTimeout('40');
    operationDetails.saveParameters();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Service saved'
    mainPage.closeSnackbar();
    operationDetails.close();

    browser.waitForElementVisible(
      '//tr[.//td[@data-test="service-link" and contains(text(), "s1c1")]]//*[contains(text(), "' +
        browser.globals.testdata +
        '/' +
        browser.globals.rest_url_2 +
        '")]',
    );
    browser.waitForElementVisible(
      '//tr[.//td[@data-test="service-link" and contains(text(), "s1c1")]]//*[contains(@class, "mdi-lock-open-outline")]',
    );
    browser.assert.containsText(
      clientServices.elements.serviceDescription,
      'REST (' +
        browser.globals.testdata +
        '/' +
        browser.globals.rest_url_2 +
        ')',
    );

    clientServices.openOperation('s1c1');
    browser.assert.valueContains(
      operationDetails.elements.serviceURL,
      browser.globals.testdata + '/' + browser.globals.rest_url_2,
    );
    browser.assert.valueContains(operationDetails.elements.timeout, '40');
    browser.expect.element(operationDetails.elements.sslAuth).to.not.be
      .selected;
    operationDetails.close();
  },
  'Security server client add rest operation access rights': (browser) => {
    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);

    clientServices.expandServiceDetails();
    clientServices.openOperation('s1c1');
    browser.waitForElementVisible(operationDetails);

    operationDetails.openAddAccessRights();
    browser.waitForElementVisible(addSubjectsPopup);

    // Verify types and filtering
    addSubjectsPopup
      .startSearch()
      .verifyClientTypeVisible('SUBSYSTEM')
      .verifyClientTypeVisible('GLOBALGROUP')
      .verifyClientTypeVisible('LOCALGROUP');
    addSubjectsPopup
      .selectServiceClientType('SUBSYSTEM')
      .verifyClientTypeVisible('SUBSYSTEM')
      .verifyClientTypeVisible('GLOBALGROUP')
      .verifyClientTypeVisible('LOCALGROUP');
    addSubjectsPopup
      .startSearch()
      .verifyClientTypeNotPresent('LOCALGROUP')
      .verifyClientTypeNotPresent('GLOBALGROUP')
      .verifyClientTypeVisible('SUBSYSTEM');

    // Verify cancel
    addSubjectsPopup.selectSubject('TestCom');
    addSubjectsPopup.cancel();
    browser.waitForElementNotPresent(
      '//table[contains(@class, "group-members-table")]//td[contains(text(), "TestCom")]',
    );

    // Verify add
    operationDetails.openAddAccessRights();
    browser.waitForElementVisible(addSubjectsPopup);
    addSubjectsPopup
      .startSearch()
      .selectSubject('TestOrg')
      .selectSubject('Security server owners')
      .selectSubject('Group1')
      .addSelected();
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
  'Security server client remove rest operation access rights': (browser) => {
    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);

    clientServices.expandServiceDetails();
    clientServices.openOperation('s1c1');
    browser.waitForElementVisible(operationDetails);

    // Verify cancel remove
    operationDetails.removeAccessRight('TestOrg');
    browser.waitForElementVisible(removeAccessRightPopup);
    removeAccessRightPopup.cancel();
    browser.waitForElementVisible(
      '//table[contains(@class, "group-members-table")]//td[contains(text(), "TestOrg")]',
    );

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
    browser.waitForElementVisible(
      '//table[contains(@class, "group-members-table")]//td[contains(text(), "Security server owners")]',
    );
    browser.waitForElementVisible(
      '//table[contains(@class, "group-members-table")]//td[contains(text(), "Group1")]',
    );

    // Verify cancel remove all
    operationDetails.removeAllAccessRights();
    browser.waitForElementVisible(removeAllAccessRightsPopup);
    removeAllAccessRightsPopup.cancel();
    browser.waitForElementVisible(
      '//table[contains(@class, "group-members-table")]//td[contains(text(), "Security server owners")]',
    );
    browser.waitForElementVisible(
      '//table[contains(@class, "group-members-table")]//td[contains(text(), "Group1")]',
    );

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
  'Security server client add rest endpoints': (browser) => {
    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);

    clientServices.expandServiceDetails();
    clientServices.openOperation('s1c1');
    browser.waitForElementVisible(operationDetails);
    operationDetails.openEndpointsTab();
    browser.waitForElementVisible(restEndpoints);
    restEndpoints.openAddDialog();
    browser.waitForElementVisible(addEndpointPopup);

    // Verify validation rules
    addEndpointPopup.selectRequestMethod('GET');
    addEndpointPopup.modifyPath('');

    // 'The path field is required'
    browser.waitForElementVisible(
      '//div[contains(@class, "v-messages__message")]',
    );

    // test cancel
    addEndpointPopup.initPath('/noreq1');
    addEndpointPopup.cancel();
    browser.waitForElementVisible(restEndpoints);
    browser.waitForElementNotPresent(
      '//table[.//thead[.//*[contains(text(),"HTTP Request Method")]]]//*[contains(text(),"/noreq1")]',
    );

    // test defaults and data
    restEndpoints.openAddDialog();
    browser.waitForElementVisible(addEndpointPopup);
    browser.assert.value(addEndpointPopup.elements.requestPath, '/');
    browser.assert.containsText(
      addEndpointPopup.elements.methodDropdown,
      'ALL',
    );

    addEndpointPopup.clickMethodMenu();
    addEndpointPopup.verifyMethodExists('ALL');
    addEndpointPopup.verifyMethodExists('GET');
    addEndpointPopup.verifyMethodExists('POST');
    addEndpointPopup.verifyMethodExists('PUT');
    addEndpointPopup.verifyMethodExists('DELETE');
    addEndpointPopup.verifyMethodExists('HEAD');
    addEndpointPopup.verifyMethodExists('OPTIONS');
    addEndpointPopup.verifyMethodExists('PATCH');
    addEndpointPopup.verifyMethodExists('TRACE');
    browser.keys(browser.Keys.ESCAPE);

    // Verify add
    addEndpointPopup.modifyPath('/testreq2');
    addEndpointPopup.selectRequestMethod('POST');
    addEndpointPopup.addSelected();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'New endpoint created successfully'
    mainPage.closeSnackbar();
    browser.waitForElementVisible(restEndpoints);
    restEndpoints.verifyEndpointRow(1, 'POST', '/testreq2');

    // Verify uniqueness
    restEndpoints.openAddDialog();
    addEndpointPopup.modifyPath('/testreq2');
    addEndpointPopup.selectRequestMethod('POST');
    addEndpointPopup.addSelected();
    browser.waitForElementVisible(mainPage.elements.alertMessage); // 'Endpoint already exists'
    mainPage.closeAlertMessage();

    // verify sorting of added
    restEndpoints.openAddDialog();
    addEndpointPopup.modifyPath('/testreq1');
    addEndpointPopup.selectRequestMethod('POST');
    addEndpointPopup.addSelected();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'New endpoint created successfully'
    mainPage.closeSnackbar();
    browser.waitForElementVisible(restEndpoints);
    restEndpoints.verifyEndpointRow(1, 'POST', '/testreq1');

    restEndpoints.openAddDialog();
    addEndpointPopup.modifyPath('/testreq3');
    addEndpointPopup.selectRequestMethod('POST');
    addEndpointPopup.addSelected();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'New endpoint created successfully'
    mainPage.closeSnackbar();
    browser.waitForElementVisible(restEndpoints);
    restEndpoints.verifyEndpointRow(3, 'POST', '/testreq3');

    restEndpoints.openAddDialog();
    addEndpointPopup.modifyPath('/testreq1');
    addEndpointPopup.selectRequestMethod('DELETE');
    addEndpointPopup.addSelected();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'New endpoint created successfully'
    mainPage.closeSnackbar();
    browser.waitForElementVisible(restEndpoints);
    restEndpoints.verifyEndpointRow(1, 'DELETE', '/testreq1');

    restEndpoints.openAddDialog();
    addEndpointPopup.modifyPath('/');
    addEndpointPopup.selectRequestMethod('POST');
    addEndpointPopup.addSelected();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'New endpoint created successfully'
    mainPage.closeSnackbar();
    browser.waitForElementVisible(restEndpoints);
    restEndpoints.verifyEndpointRow(2, 'POST', '/');

  },
  'Security server client edit rest endpoints': (browser) => {
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);

    clientServices.expandServiceDetails();
    clientServices.openOperation('s1c1');
    browser.waitForElementVisible(operationDetails);
    operationDetails.openEndpointsTab();
    browser.waitForElementVisible(restEndpoints);

    restEndpoints.openEndpoint('POST', '/testreq2');
    browser.waitForElementVisible(endpointPopup);
    browser.assert.value(endpointPopup.elements.requestPath, '/testreq2');
    browser.assert.containsText(endpointPopup.elements.methodDropdown, 'POST');

    // Verify validation rules
    endpointPopup.modifyPath('');
    // Verify there's an error message, something like 'The path field is required'
    browser.waitForElementVisible(endpointPopup.elements.requestPathMessage);

    // test cancel
    endpointPopup.initPath('/newreq1');
    endpointPopup.selectRequestMethod('PUT');
    endpointPopup.cancel();
    browser.waitForElementVisible(restEndpoints);
    browser.waitForElementNotPresent(
      '//table[.//thead[.//*[contains(text(),"HTTP Request Method")]]]//*[contains(text(),"/newreq1")]',
    );
    restEndpoints.verifyEndpointRow(3, 'POST', '/testreq2');

    // Verify uniqueness
    restEndpoints.openEndpoint('POST', '/testreq2');
    endpointPopup.modifyPath('/testreq3');
    endpointPopup.addSelected();
    browser.waitForElementVisible(mainPage.elements.alertMessage); // 'Endpoint already exists'
    mainPage.closeAlertMessage();

    // Verify edit
    endpointPopup.modifyPath('/newreq1');
    endpointPopup.selectRequestMethod('PUT');
    endpointPopup.addSelected();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Changes to endpoint saved successfully'
    mainPage.closeSnackbar();
    browser.waitForElementVisible(restEndpoints);
    restEndpoints.verifyEndpointRow(5, 'PUT', '/newreq1');

    // Verify cancel delete
    restEndpoints.openEndpoint('POST', '/testreq3');
    endpointPopup.deleteEndpoint();
    browser.waitForElementVisible('//div[@data-test="dialog-simple"]');
    endpointPopup.cancelDelete();
    endpointPopup.cancel();
    browser.waitForElementVisible(restEndpoints);

    // Verify confirm delete
    restEndpoints.openEndpoint('POST', '/testreq3');
    endpointPopup.deleteEndpoint();
    browser.waitForElementVisible(
      '//div[@data-test="dialog-simple" and .//span[@data-test="dialog-title"]]',
    );
    endpointPopup.confirmDelete();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Endpoint removed successfully'
    mainPage.closeSnackbar();
    browser.waitForElementNotPresent(
      '//table[.//thead[.//*[contains(text(),"HTTP Request Method")]]]//*[contains(text(),"/testreq3")]',
    );
  },
  'Security server client edit rest service': async (browser) => {
    var startTime, startTimestamp;
    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);

    clientServices.expandServiceDetails();

    browser.getText(
      clientServices.elements.refreshTimestamp,
      function (result) {
        startTimestamp = result.value;
        startTime = new Date().getTime();
      },
    );

    // Verify enabling
    clientServices.toggleEnabled();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Service description enabled'
    mainPage.closeSnackbar();

    // Verify disabling and canceling disable
    clientServices.toggleEnabled();
    browser.waitForElementVisible(
      '//div[@data-test="dialog-simple" and .//span[@data-test="dialog-title"]]',
    );
    clientServices.initDisableNotice('Message1');
    clientServices.cancelDisable();
    clientServices.toggleEnabled();
    browser.waitForElementVisible(
      '//div[@data-test="dialog-simple" and .//span[@data-test="dialog-title"]]',
    );
    browser.assert.value(clientServices.elements.disableNotice, '');
    clientServices.initDisableNotice('Notice1');
    clientServices.confirmDisable();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Service description enabled'
    mainPage.closeSnackbar();

    // Verify editing, malformed URL and service code
    clientServices.openServiceDetails();
    browser.assert.containsText(
      restServiceDetails.elements.serviceType,
      'REST API',
    );
    restServiceDetails.modifyServiceCode('/');
    browser.expect.element(restServiceDetails.elements.confirmDialogButton).to
      .not.be.enabled;
    // Verify there's an error message, something like 'Identifier value contains illegal characters'
    browser.waitForElementVisible(restServiceDetails.elements.codeMessage);

    // Part 1 wait until at least 1 min has passed since refresh at the start of the test
    // Split this wait into two parts to not cause timeouts
    await browser.perform(function () {
      const endTime = new Date().getTime();
      const passedTime = endTime - startTime;
      if (passedTime < 30000) {
        console.log('Waiting', 30000 - passedTime, 'ms');
        browser.pause(30000 - passedTime);
      }
    });

    restServiceDetails.modifyServiceCode('');
    // Verify there's an error message, something like 'The fields.code_field field is required'
    browser.waitForElementVisible(restServiceDetails.elements.codeMessage);
    restServiceDetails.modifyServiceUrl('foobar');
    // Verify there's an error message, something like 'URL is not valid'
    browser.waitForElementVisible(restServiceDetails.elements.URLMessage);
    restServiceDetails.modifyServiceUrl('');
    // Verify there's an error message, something like 'The URL field is required'
    browser.waitForElementVisible(restServiceDetails.elements.URLMessage);

    // Verify cancel
    restServiceDetails.initServiceUrl(
      browser.globals.testdata + '/' + browser.globals.rest_url_1,
    );
    restServiceDetails.initServiceCode('s1c2');
    restServiceDetails.cancelDialog();
    browser.assert.containsText(
      clientServices.elements.serviceDescription,
      'REST (' +
        browser.globals.testdata +
        '/' +
        browser.globals.rest_url_2 +
        ')',
    );
    browser.waitForElementVisible(
      '//td[@data-test="service-link" and contains(text(),"s1c1")]',
    );

    // Verify successful edit
    clientServices.openServiceDetails();
    restServiceDetails.modifyServiceUrl(
      browser.globals.testdata + '/' + browser.globals.rest_url_1,
    );
    restServiceDetails.modifyServiceCode('s1c2');

    // Part 2 wait until at least 1 min has passed since refresh at the start of the test
    await browser.perform(function () {
      const endTime = new Date().getTime();
      const passedTime = endTime - startTime;
      if (passedTime < 60000) {
        console.log('Waiting', 60000 - passedTime, 'ms');
        browser.pause(60000 - passedTime);
      }
    });

    restServiceDetails.confirmDialog();

    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Description saved'
    mainPage.closeSnackbar();
    browser.assert.containsText(
      clientServices.elements.serviceDescription,
      'REST (' +
        browser.globals.testdata +
        '/' +
        browser.globals.rest_url_1 +
        ')',
    );
    browser.waitForElementNotPresent(
      '//td[@data-test="service-link" and contains(text(),"s1c1")]',
    );
    browser.waitForElementVisible(
      '//td[@data-test="service-link" and contains(text(),"s1c2")]',
    );

    // Verify that the refresh time has been updated
    browser.perform(function () {
      browser.expect
        .element(clientServices.elements.refreshTimestamp)
        .text.to.not.contain(startTimestamp);
    });

  },
  'Security server client delete rest service': (browser) => {
    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);

    // Verify cancel delete
    clientServices.openServiceDetails();
    browser.waitForElementVisible(restServiceDetails);
    restServiceDetails.deleteService();
    restServiceDetails.cancelDelete();

    restServiceDetails.closeServiceDetails();
    browser.assert.containsText(
      clientServices.elements.serviceDescription,
      'REST (' +
        browser.globals.testdata +
        '/' +
        browser.globals.rest_url_1 +
        ')',
    );

    // Verify successful delete
    clientServices.openServiceDetails();
    restServiceDetails.deleteService();
    restServiceDetails.confirmDelete();

    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Service description deleted'
    mainPage.closeSnackbar();
    browser.waitForElementNotPresent(
      clientServices.elements.serviceDescription,
    );

  },
};
