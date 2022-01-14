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
  tags: ['ss', 'clients', 'openapiservices'],
  'Security server client add openapi service': (browser) => {
    const frontPage = browser.page.ssLoginPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const clientServices = clientInfo.section.services;

    // Open SUT and check that page is loaded
    frontPage.navigateAndMakeTestable();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Navigate
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);

    // Verify empty and malformed URL and service code error messages and Add button initial state
    clientServices.openAddREST();
    browser.expect.element(clientServices.elements.confirmAddServiceButton).to
      .not.be.enabled;
    clientServices.initServiceUrl('a');
    // Verify there's an error message, something like 'URL is not valid'
    browser.waitForElementVisible(
      '//div[contains(@class, "v-messages__message")]',
    );
    clientServices.modifyServiceUrl('');
    // Verify there's an error message, something like 'The URL field is required'
    browser.waitForElementVisible(
      '//div[contains(@class, "v-messages__message")]',
    );
    clientServices.initServiceUrl('http://example.com');
    clientServices.initServiceCode('a');
    clientServices.modifyServiceCode('');
    // Verify there's an error message, something like 'The Service Code field is required'
    browser.waitForElementVisible(
      '//div[contains(@class, "v-messages__message")]',
    );
    clientServices.initServiceCode('s3c1');
    clientServices.selectOpenApi();
    clientServices.cancelAddDialog();

    // Verify that the fields are empty after reopening
    clientServices.openAddREST();
    browser.assert.value(clientServices.elements.newServiceUrl, '');
    browser.assert.value(clientServices.elements.newServiceCode, '');
    browser.expect.element(clientServices.elements.RESTPathRadioButton).to.not
      .be.selected;
    browser.expect.element(clientServices.elements.OpenApiRadioButton).to.not.be
      .selected;
    browser.expect.element(clientServices.elements.confirmAddServiceButton).to
      .not.be.enabled;

    // Verify opening non-existing OpenApi URL
    const urlToTest = 'https://www.niis.org/nosuchopenapi.yaml';
    clientServices.selectOpenApi();
    clientServices.initServiceUrl(urlToTest);
    clientServices.initServiceCode('s3c1');
    clientServices.confirmAddDialog();
    browser.waitForElementVisible(mainPage.elements.alertMessage, 20000); // loading a missing file can sometimes take more time before failing
    browser.assert.containsText(mainPage.elements.alertMessage, urlToTest);
    mainPage.closeAlertMessage();

    // Verify invalid service code
    clientServices.openAddREST();
    clientServices.initServiceUrl(
      browser.globals.testdata + '/' + browser.globals.openapi_url_1,
    );
    clientServices.selectOpenApi();
    clientServices.initServiceCode('/');
    browser.expect.element(clientServices.elements.confirmAddServiceButton).to
      .not.be.enabled;

    // 'Identifier value contains illegal characters'
    browser.waitForElementVisible(
      '//div[contains(@class, "v-messages__message")]',
    );

    clientServices.cancelAddDialog();

    // Verify successful URL open
    clientServices.openAddREST();
    clientServices.initServiceUrl(
      browser.globals.testdata + '/' + browser.globals.openapi_url_1,
    );
    clientServices.selectOpenApi();
    clientServices.initServiceCode('s3c1');
    clientServices.confirmAddDialog();

    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'OpenApi3 service added'
    mainPage.closeSnackbar();
    browser.assert.containsText(
      clientServices.elements.serviceDescription,
      'OPENAPI3 (' +
        browser.globals.testdata +
        '/' +
        browser.globals.openapi_url_1 +
        ')',
    );

    clientServices.expandServiceDetails();
    browser.waitForElementVisible(
      '//td[@data-test="service-link" and contains(text(),"s3c1")]',
    );

    browser.end();
  },
  'Security server client edit openapi operation': (browser) => {
    const frontPage = browser.page.ssLoginPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const clientServices = clientInfo.section.services;
    const operationDetails = mainPage.section.restOperationDetails;

    // Open SUT and check that page is loaded
    frontPage.navigateAndMakeTestable();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Navigate
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);

    clientServices.expandServiceDetails();
    clientServices.openOperation('s3c1');

    // Verify tooltips
    /* Tooltips are currently in v7 displayed constantly, thus verification of tooltips is disabled
    browser.moveToElement(operationDetails.elements.urlHelp, 0, 0);
    browser.expect
      .element(operationDetails.elements.activeTooltip)
      .to.be.visible; // 'The URL where requests targeted at the service are directed'

    browser.moveToElement(operationDetails.elements.timeoutHelp, 0, 0);
    browser.expect
      .element(operationDetails.elements.activeTooltip)
      .to.be.visible; // 'The maximum duration of a request to the service, in seconds'

    browser.moveToElement(operationDetails.elements.verifyCertHelp, 0, 0);
    browser.expect
      .element(operationDetails.elements.activeTooltip)
      .to.be.visible; // 'Verify TLS certificate when a secure connection is established'
    */

    // Verify cancel
    operationDetails.modifyUrl('https://niis.org/nosuch.yaml');
    browser.logMessage('changing timeout 60->40');
    operationDetails.modifyTimeout('40');
    browser.logMessage('changed timeout 60->40, toggling verification');
    operationDetails.toggleCertVerification();
    browser.logMessage('verification toggled');
    browser.expect.element(operationDetails.elements.sslAuth).to.be.selected;
    operationDetails.close();

    // Verify that options were not changed
    browser.assert.containsText(
      clientServices.elements.operationUrl,
      browser.globals.testdata.slice(
        0,
        browser.globals.testdata.indexOf('/', 8),
      ),
    );
    browser.waitForElementVisible(
      '//tr[.//td[@data-test="service-link" and contains(text(), "s3c1")]]//*[contains(@class, "mdi-lock-open-outline")]',
    );
    clientServices.openOperation('s3c1');
    browser.waitForElementVisible(operationDetails);
    browser.assert.valueContains(
      operationDetails.elements.serviceURL,
      browser.globals.testdata.slice(
        0,
        browser.globals.testdata.indexOf('/', 8),
      ),
    );
    browser.assert.valueContains(operationDetails.elements.timeout, '60');
    browser.expect.element(operationDetails.elements.sslAuth).to.not.be
      .selected;

    // Verify change operation
    operationDetails.modifyUrl(
      browser.globals.testdata + '/' + browser.globals.openapi_url_2,
    );
    operationDetails.modifyTimeout('40');
    operationDetails.saveParameters();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Service saved'
    mainPage.closeSnackbar();
    operationDetails.close();

    browser.waitForElementVisible(
      '//tr[.//td[@data-test="service-link" and contains(text(), "s3c1")]]//*[contains(text(), "' +
        browser.globals.testdata +
        '/' +
        browser.globals.openapi_url_2 +
        '")]',
    );
    browser.waitForElementVisible(
      '//tr[.//td[@data-test="service-link" and contains(text(), "s3c1")]]//*[contains(@class, "mdi-lock-open-outline")]',
    );
    browser.assert.containsText(
      clientServices.elements.serviceDescription,
      'OPENAPI3 (' +
        browser.globals.testdata +
        '/' +
        browser.globals.openapi_url_1 +
        ')',
    );

    clientServices.openOperation('s3c1');
    browser.assert.valueContains(
      operationDetails.elements.serviceURL,
      browser.globals.testdata + '/' + browser.globals.openapi_url_2,
    );
    browser.assert.valueContains(operationDetails.elements.timeout, '40');
    browser.expect.element(operationDetails.elements.sslAuth).to.not.be
      .selected;
    operationDetails.close();

    browser.end();
  },
  'Security server client add openapi operation access rights': (browser) => {
    const frontPage = browser.page.ssLoginPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const clientServices = clientInfo.section.services;
    const operationDetails = mainPage.section.restOperationDetails;
    const addSubjectsPopup = mainPage.section.wsdlAddSubjectsPopup;

    // Open SUT and check that page is loaded
    frontPage.navigateAndMakeTestable();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Navigate
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);

    clientServices.expandServiceDetails();
    clientServices.openOperation('s3c1');
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
    browser.waitForElementNotVisible('//div[@role="listbox"]');
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

    browser.end();
  },
  'Security server client remove openapi operation access rights': (
    browser,
  ) => {
    const frontPage = browser.page.ssLoginPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const clientServices = clientInfo.section.services;
    const operationDetails = mainPage.section.restOperationDetails;
    const removeAccessRightPopup = mainPage.section.removeAccessRightPopup;
    const removeAllAccessRightsPopup =
      mainPage.section.removeAllAccessRightsPopup;

    // Open SUT and check that page is loaded
    frontPage.navigateAndMakeTestable();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Navigate
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);

    clientServices.expandServiceDetails();
    clientServices.openOperation('s3c1');
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

    browser.end();
  },
  'Security server client add openapi endpoints': (browser) => {
    const frontPage = browser.page.ssLoginPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const clientServices = clientInfo.section.services;
    const operationDetails = mainPage.section.restOperationDetails;
    const restEndpoints = mainPage.section.restServiceEndpoints;
    const addEndpointPopup = mainPage.section.addEndpointPopup;

    // Open SUT and check that page is loaded
    frontPage.navigateAndMakeTestable();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Navigate
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);

    clientServices.expandServiceDetails();
    clientServices.openOperation('s3c1');
    browser.waitForElementVisible(operationDetails);
    operationDetails.openEndpointsTab();
    browser.waitForElementVisible(restEndpoints);
    restEndpoints.openAddDialog();
    browser.waitForElementVisible(addEndpointPopup);

    // Verify validation rules
    addEndpointPopup.selectRequestMethod('GET');
    addEndpointPopup.modifyPath('');
    // Verify there's an error message, something like 'The path field is required'
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
    addEndpointPopup.verifyMethodExists('PATCH');
    addEndpointPopup.verifyMethodExists('DELETE');
    addEndpointPopup.verifyMethodExists('HEAD');
    addEndpointPopup.verifyMethodExists('OPTIONS');
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

    // verify no edit button on autogenerated endpoint
    browser.waitForElementVisible(
      '//table[.//*[contains(text(),"HTTP Request Method")]]//tr[.//*[contains(text(),"POST")] and .//*[contains(text(),"/testreq3")]]//button[@data-test="endpoint-edit"]',
    );
    browser.waitForElementVisible(
      '//table[.//*[contains(text(),"HTTP Request Method")]]//tr[.//*[contains(text(),"GET")] and .//*[contains(text(),"/autogenerated1")]]',
    );
    browser.waitForElementNotPresent(
      '//table[.//*[contains(text(),"HTTP Request Method")]]//tr[.//*[contains(text(),"GET")] and .//*[contains(text(),"/autogenerated1")]]//button[@data-test="endpoint-edit"]',
    );

    browser.end();
  },
  'Security server client edit openapi endpoints': (browser) => {
    const frontPage = browser.page.ssLoginPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const clientServices = clientInfo.section.services;
    const operationDetails = mainPage.section.restOperationDetails;
    const restEndpoints = mainPage.section.restServiceEndpoints;
    const endpointPopup = mainPage.section.editEndpointPopup;

    // Open SUT and check that page is loaded
    frontPage.navigateAndMakeTestable();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Navigate
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);

    clientServices.expandServiceDetails();
    clientServices.openOperation('s3c1');
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
    browser.waitForElementVisible(
      '//div[contains(@class, "v-messages__message")]',
    );

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
    browser.waitForElementVisible('//div[@data-test="dialog-simple"]');
    endpointPopup.confirmDelete();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Endpoint removed successfully'
    mainPage.closeSnackbar();
    browser.waitForElementNotPresent(
      '//table[.//thead[.//*[contains(text(),"HTTP Request Method")]]]//*[contains(text(),"/testreq3")]',
    );

    browser.end();
  },
  'Security server client edit openapi service': async (browser) => {
    const frontPage = browser.page.ssLoginPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const clientServices = clientInfo.section.services;
    const openApiServiceDetails = mainPage.section.openApiServiceDetails;

    var startTime, startTimestamp;

    // Open SUT and check that page is loaded
    frontPage.navigateAndMakeTestable();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Navigate
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
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
    browser.waitForElementVisible('//div[@data-test="dialog-simple"]');
    clientServices.initDisableNotice('Message1');
    clientServices.cancelDisable();
    clientServices.toggleEnabled();
    browser.waitForElementVisible('//div[@data-test="dialog-simple"]');
    browser.assert.value(clientServices.elements.disableNotice, '');
    clientServices.initDisableNotice('Notice1');
    clientServices.confirmDisable();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Service description disabled'
    mainPage.closeSnackbar();

    // Verify editing, malformed URL and service code
    clientServices.openServiceDetails();
    browser.assert.containsText(
      openApiServiceDetails.elements.serviceType,
      'OpenAPI 3',
    );
    openApiServiceDetails.modifyServiceCode('/');
    browser.expect.element(openApiServiceDetails.elements.confirmDialogButton)
      .to.not.be.enabled;

    // Verify there's an error message, something like 'Identifier value contains illegal characters'
    browser.waitForElementVisible(openApiServiceDetails.elements.codeMessage);

    openApiServiceDetails.modifyServiceCode('');
    // Verify there's an error message, something like 'The fields.code_field field is required'
    browser.waitForElementVisible(openApiServiceDetails.elements.codeMessage);

    openApiServiceDetails.modifyServiceUrl('foobar');
    // Verify there's an error message, something like 'URL is not valid'
    browser.waitForElementVisible(openApiServiceDetails.elements.URLMessage);

    openApiServiceDetails.modifyServiceUrl('');
    // Verify there's an error message, something like 'URL is not valid'
    browser.waitForElementVisible(openApiServiceDetails.elements.URLMessage);
    openApiServiceDetails.cancelDialog();

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

    // verify missing file
    clientServices.openServiceDetails();
    openApiServiceDetails.modifyServiceUrl('https://www.niis.org/nosuch.yaml');
    openApiServiceDetails.confirmDialog();
    browser.waitForElementVisible(mainPage.elements.alertMessage); // 'Parsing OpenApi3 description failed'
    mainPage.closeAlertMessage();

    // Verify cancel
    openApiServiceDetails.modifyServiceUrl(
      browser.globals.testdata + '/' + browser.globals.openapi_url_2,
    );
    openApiServiceDetails.modifyServiceCode('s3c2');
    openApiServiceDetails.cancelDialog();
    browser.assert.containsText(
      clientServices.elements.serviceDescription,
      'OPENAPI3 (' +
        browser.globals.testdata +
        '/' +
        browser.globals.openapi_url_1 +
        ')',
    );
    browser.waitForElementVisible(
      '//td[@data-test="service-link" and contains(text(),"s3c1")]',
    );

    // Verify succesful edit
    clientServices.openServiceDetails();
    openApiServiceDetails.modifyServiceUrl(
      browser.globals.testdata + '/' + browser.globals.openapi_url_2,
    );
    openApiServiceDetails.modifyServiceCode('s3c2');

    // Part 2 wait until at least 1 min has passed since refresh at the start of the test
    await browser.perform(function () {
      const endTime = new Date().getTime();
      const passedTime = endTime - startTime;
      if (passedTime < 60000) {
        console.log('Waiting', 60000 - passedTime, 'ms');
        browser.pause(60000 - passedTime);
      }
    });

    openApiServiceDetails.confirmDialog();

    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Description saved'
    mainPage.closeSnackbar();
    browser.assert.containsText(
      clientServices.elements.serviceDescription,
      'OPENAPI3 (' +
        browser.globals.testdata +
        '/' +
        browser.globals.openapi_url_2 +
        ')',
    );
    browser.waitForElementNotPresent(
      '//td[@data-test="service-link" and contains(text(),"s3c1")]',
    );
    browser.waitForElementVisible(
      '//td[@data-test="service-link" and contains(text(),"s3c2")]',
    );

    // Verify that the refresh time has been updated
    browser.perform(function () {
      browser.expect
        .element(clientServices.elements.refreshTimestamp)
        .text.to.not.contain(startTimestamp);
    });

    browser.end();
  },
  'Security server client delete openapi service': (browser) => {
    const frontPage = browser.page.ssLoginPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const clientServices = clientInfo.section.services;
    const openApiServiceDetails = mainPage.section.openApiServiceDetails;

    // Open SUT and check that page is loaded
    frontPage.navigateAndMakeTestable();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Navigate
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);

    // Verify cancel delete
    clientServices.openServiceDetails();
    browser.waitForElementVisible(openApiServiceDetails);
    openApiServiceDetails.deleteService();
    openApiServiceDetails.cancelDelete();

    openApiServiceDetails.closeServiceDetails();
    browser.assert.containsText(
      clientServices.elements.serviceDescription,
      'OPENAPI3 (' +
        browser.globals.testdata +
        '/' +
        browser.globals.openapi_url_2 +
        ')',
    );

    // Verify successful delete
    clientServices.openServiceDetails();
    openApiServiceDetails.deleteService();
    openApiServiceDetails.confirmDelete();

    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Service description deleted'
    mainPage.closeSnackbar();

    browser.waitForElementNotPresent(
      clientServices.elements.serviceDescription,
    );

    browser.end();
  },
};
