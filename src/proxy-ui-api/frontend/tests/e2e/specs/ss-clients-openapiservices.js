
module.exports = {
  tags: ['ss', 'clients', 'openapiservices'],
  'Security server client add openapi service': browser => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const clientServices = clientInfo.section.services;

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Navigate
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openTestService();
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);

    // Verify empty and malformed URL and service code error messages and Add button initial state
    clientServices.openAddREST();
    browser.expect.element(clientServices.elements.confirmAddServiceButton).to.not.be.enabled;
    clientServices.enterServiceUrl('a');
    clientServices.enterServiceUrl('');
    browser.assert.containsText(clientServices.elements.serviceUrlMessage, 'The URL field is required');
    clientServices.enterServiceUrl('foobar');
    browser.assert.containsText(clientServices.elements.serviceUrlMessage, 'URL is not valid');
    clientServices.enterServiceCode('a');
    clientServices.enterServiceCode('');
    browser.assert.containsText(clientServices.elements.serviceCodeMessage, 'The Service Code field is required');
    clientServices.enterServiceCode('s3c1');
    clientServices.selectOpenApi();
    clientServices.cancelAddDialog();

    // Verify that fields are empty after reopening
    clientServices.openAddREST();
    browser.assert.value(clientServices.elements.newServiceUrl, '');
    browser.assert.value(clientServices.elements.newServiceCode, '');
    browser.expect.element(clientServices.elements.RESTPathRadioButton).to.not.be.selected;
    browser.expect.element(clientServices.elements.OpenApiRadioButton).to.not.be.selected;
    browser.expect.element(clientServices.elements.confirmAddServiceButton).to.not.be.enabled;

    // Verify opening nonexisting OpenApi URL
    clientServices.selectOpenApi();
    clientServices.enterServiceUrl('https://www.niis.org/nosuchopenapi.yaml');
    clientServices.enterServiceCode('s3c1');
    clientServices.confirmAddDialog();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage, 20000); // loading a missing file can sometimes take more time before failing
    browser.assert.containsText(mainPage.elements.snackBarMessage, 'Parsing OpenApi3 description failed');
    mainPage.closeSnackbar();

    // Verify invalid service code
    clientServices.openAddREST();
    clientServices.enterServiceUrl(browser.globals.testdata + '/' + browser.globals.openapi_url_1);
    clientServices.selectOpenApi();
    clientServices.enterServiceCode('/');
    browser.expect.element(clientServices.elements.confirmAddServiceButton).to.not.be.enabled;
    browser.assert.containsText(clientServices.elements.serviceCodeMessage, 'Identifier value contains illegal characters');
    clientServices.cancelAddDialog();

    // Verify successfull URL open
    clientServices.openAddREST();
    clientServices.enterServiceUrl(browser.globals.testdata + '/' + browser.globals.openapi_url_1);
    clientServices.selectOpenApi();
    clientServices.enterServiceCode('s3c1');
    clientServices.confirmAddDialog();

    browser.assert.containsText(mainPage.elements.snackBarMessage, 'OpenApi3 service added');
    mainPage.closeSnackbar();
    browser.assert.containsText(clientServices.elements.serviceDescription, 'OPENAPI3 (' + browser.globals.testdata + '/' + browser.globals.openapi_url_1 + ')');

    clientServices.expandServiceDetails();
    browser.waitForElementVisible('//td[contains(@data-test, "service-link") and contains(text(),"s3c1")]');

    browser.end();
  },
  'Security server client edit openapi operation': browser => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const clientServices = clientInfo.section.services;
    const operationDetails = mainPage.section.restOperationDetails;


    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Navigate
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openTestService();
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);

    clientServices.expandServiceDetails();
    clientServices.openOperation('s3c1');

    // Verify tooltips
    browser.moveToElement(operationDetails.elements.urlHelp,0,0);
    browser.expect.element(operationDetails.elements.activeTooltip).to.be.visible.and.text.to.equal("The URL where requests targeted at the service are directed");

    browser.moveToElement(operationDetails.elements.timeoutHelp,0,0);
    browser.expect.element(operationDetails.elements.activeTooltip).to.be.visible.and.text.to.equal("The maximum duration of a request to the service, in seconds");

    browser.moveToElement(operationDetails.elements.verifyCertHelp,0,0);
    browser.expect.element(operationDetails.elements.activeTooltip).to.be.visible.and.text.to.equal("Verify TLS certificate when a secure connection is established");

    // Verify cancel
    operationDetails.enterUrl(browser.globals.testdata + '/' + browser.globals.openapi_url_2);
    operationDetails.enterTimeout('40');
    browser.expect.element(operationDetails.elements.sslAuth).to.not.be.selected; //check ssl disable due to new url
    operationDetails.close();

    // Verify that options were not changed
    browser.assert.containsText(clientServices.elements.operationUrl, browser.globals.testdata.slice(0,browser.globals.testdata.indexOf('/', 8)));
    browser.waitForElementVisible('//tr[.//td[@data-test="service-link" and contains(text(), "s3c1")]]//*[contains(@class, "mdi-lock-open-outline")]');
    clientServices.openOperation('s3c1');
    browser.waitForElementVisible(operationDetails);
    browser.assert.valueContains(operationDetails.elements.serviceURL, browser.globals.testdata.slice(0,browser.globals.testdata.indexOf('/', 8)));
    browser.assert.valueContains(operationDetails.elements.timeout, '60');
    browser.expect.element(operationDetails.elements.sslAuth).to.be.selected;


    // Verify change operation
    operationDetails.enterUrl(browser.globals.testdata + '/' + browser.globals.openapi_url_2);
    operationDetails.enterTimeout('40');
    operationDetails.saveParameters();
    browser.assert.containsText(mainPage.elements.snackBarMessage, 'Service saved');
    mainPage.closeSnackbar();
    operationDetails.close();

    browser.waitForElementVisible('//tr[.//td[@data-test="service-link" and contains(text(), "s3c1")]]//*[contains(text(), "' + browser.globals.testdata + '/' + browser.globals.openapi_url_2 + '")]');
    browser.waitForElementVisible('//tr[.//td[@data-test="service-link" and contains(text(), "s3c1")]]//*[contains(@class, "mdi-lock-open-outline")]');
    browser.assert.containsText(clientServices.elements.serviceDescription, 'OPENAPI3 (' + browser.globals.testdata + '/' + browser.globals.openapi_url_1 + ')');

    clientServices.openOperation('s3c1');
    browser.assert.valueContains(operationDetails.elements.serviceURL, browser.globals.testdata + '/' + browser.globals.openapi_url_2);
    browser.assert.valueContains(operationDetails.elements.timeout, '40');
    browser.expect.element(operationDetails.elements.sslAuth).to.be.selected;
    operationDetails.close();

    browser.end();

  },
  'Security server client add openapi operation access rights': browser => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const clientServices = clientInfo.section.services;
    const operationDetails = mainPage.section.restOperationDetails;
    const addSubjectsPopup = mainPage.section.wsdlAddSubjectsPopup;

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Navigate
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openTestService();
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
    addSubjectsPopup
      .startSearch()
      .verifyClientTypeNotPresent('LOCALGROUP')
      .verifyClientTypeNotPresent('GLOBALGROUP')
      .verifyClientTypeVisible('SUBSYSTEM');

    // Verify cancel
    addSubjectsPopup.selectSubject('TestCom');
    addSubjectsPopup.cancel();
    browser.waitForElementNotPresent('//table[contains(@class, "group-members-table")]//td[contains(text(), "TestCom")]');

    // Verify add
    operationDetails.openAddAccessRights();
    browser.waitForElementVisible(addSubjectsPopup);
    addSubjectsPopup
      .startSearch()
      .selectSubject('TestOrg')
      .selectSubject('Security server owners')
      .selectSubject('Group1')
      .addSelected();
    browser.assert.containsText(mainPage.elements.snackBarMessage, 'Access rights added successfully');
    mainPage.closeSnackbar();

    browser.waitForElementVisible('//table[contains(@class, "group-members-table")]//td[contains(text(), "TestOrg")]');
    browser.waitForElementVisible('//table[contains(@class, "group-members-table")]//td[contains(text(), "Security server owners")]');
    browser.waitForElementVisible('//table[contains(@class, "group-members-table")]//td[contains(text(), "Group1")]');
    browser.waitForElementNotPresent('//table[contains(@class, "group-members-table")]//td[contains(text(), "TestCom")]');

    browser.end();
  },
  'Security server client remove openapi operation access rights': browser => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const clientServices = clientInfo.section.services;
    const operationDetails = mainPage.section.restOperationDetails;
    const removeAccessRightPopup = mainPage.section.removeAccessRightPopup;
    const removeAllAccessRightsPopup = mainPage.section.removeAllAccessRightsPopup;


    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Navigate
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openTestService();
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
    browser.waitForElementVisible('//table[contains(@class, "group-members-table")]//td[contains(text(), "TestOrg")]');

    // Verify remove
    operationDetails.removeAccessRight('TestOrg');
    browser.waitForElementVisible(removeAccessRightPopup);
    removeAccessRightPopup.confirm();
    browser.assert.containsText(mainPage.elements.snackBarMessage, 'Access rights removed successfully');
    mainPage.closeSnackbar();
    browser.waitForElementNotPresent(mainPage.elements.snackBarMessage);
    browser.waitForElementNotPresent('//table[contains(@class, "group-members-table")]//td[contains(text(), "TestOrg")]');
    browser.waitForElementVisible('//table[contains(@class, "group-members-table")]//td[contains(text(), "Security server owners")]');
    browser.waitForElementVisible('//table[contains(@class, "group-members-table")]//td[contains(text(), "Group1")]');

    // Verify cancel remove all
    operationDetails.removeAllAccessRights();
    browser.waitForElementVisible(removeAllAccessRightsPopup);
    removeAllAccessRightsPopup.cancel();
    browser.waitForElementVisible('//table[contains(@class, "group-members-table")]//td[contains(text(), "Security server owners")]');
    browser.waitForElementVisible('//table[contains(@class, "group-members-table")]//td[contains(text(), "Group1")]');

    // Verify remove all
    operationDetails.removeAllAccessRights();
    browser.waitForElementVisible(removeAllAccessRightsPopup);
    removeAllAccessRightsPopup.confirm();

    browser.assert.containsText(mainPage.elements.snackBarMessage, 'Access rights removed successfully');
    mainPage.closeSnackbar();
    browser.waitForElementNotPresent('//table[contains(@class, "group-members-table")]//td[contains(text(), "Security server owners")]');
    browser.waitForElementNotPresent('//table[contains(@class, "group-members-table")]//td[contains(text(), "Group1")]');

    browser.end();
  },
  'Security server client add openapi endpoints': browser => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const clientServices = clientInfo.section.services;
    const operationDetails = mainPage.section.restOperationDetails;
    const restEndpoints = mainPage.section.restServiceEndpoints;
    const addEndpointPopup = mainPage.section.addEndpointPopup;

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Navigate
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openTestService();
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
    addEndpointPopup.enterPath('');
    browser.assert.containsText(addEndpointPopup.elements.requestPathMessage, 'The path field is required');

    // test cancel
    addEndpointPopup.enterPath('/noreq1');
    addEndpointPopup.cancel();
    browser.waitForElementVisible(restEndpoints);
    browser.waitForElementNotPresent('//table[.//thead[.//*[contains(text(),"HTTP Request Method")]]]//*[contains(text(),"/noreq1")]');

    // test defaults and data
    restEndpoints.openAddDialog();
    browser.waitForElementVisible(addEndpointPopup);
    browser.assert.value(addEndpointPopup.elements.requestPath, '/');
    browser.assert.containsText(addEndpointPopup.elements.methodDropdown, 'ALL');

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
    addEndpointPopup.enterPath('/testreq2');
    addEndpointPopup.selectRequestMethod('POST');
    addEndpointPopup.addSelected();
    browser.assert.containsText(mainPage.elements.snackBarMessage, 'New endpoint created successfully');
    mainPage.closeSnackbar();
    browser.waitForElementVisible(restEndpoints);
    restEndpoints.verifyEndpointRow(1, 'POST', '/testreq2');

    // Verify uniqueness
    restEndpoints.openAddDialog();
    addEndpointPopup.enterPath('/testreq2');
    addEndpointPopup.selectRequestMethod('POST');
    addEndpointPopup.addSelected();
    browser.assert.containsText(mainPage.elements.snackBarMessage, 'Endpoint with equivalent service code, method and path already exists for this client');
    mainPage.closeSnackbar();

    // verify sorting of added
    restEndpoints.openAddDialog();
    addEndpointPopup.enterPath('/testreq1');
    addEndpointPopup.selectRequestMethod('POST');
    addEndpointPopup.addSelected();
    browser.assert.containsText(mainPage.elements.snackBarMessage, 'New endpoint created successfully');
    mainPage.closeSnackbar();
    browser.waitForElementVisible(restEndpoints);
    restEndpoints.verifyEndpointRow(1, 'POST', '/testreq1');

    restEndpoints.openAddDialog();
    addEndpointPopup.enterPath('/testreq3');
    addEndpointPopup.selectRequestMethod('POST');
    addEndpointPopup.addSelected();
    browser.assert.containsText(mainPage.elements.snackBarMessage, 'New endpoint created successfully');
    mainPage.closeSnackbar();
    browser.waitForElementVisible(restEndpoints);
    restEndpoints.verifyEndpointRow(3, 'POST', '/testreq3');

    restEndpoints.openAddDialog();
    addEndpointPopup.enterPath('/testreq1');
    addEndpointPopup.selectRequestMethod('DELETE');
    addEndpointPopup.addSelected();
    browser.assert.containsText(mainPage.elements.snackBarMessage, 'New endpoint created successfully');
    mainPage.closeSnackbar();
    browser.waitForElementVisible(restEndpoints);
    restEndpoints.verifyEndpointRow(1, 'DELETE', '/testreq1');

    restEndpoints.openAddDialog();
    addEndpointPopup.enterPath('/');
    addEndpointPopup.selectRequestMethod('POST');
    addEndpointPopup.addSelected();
    browser.assert.containsText(mainPage.elements.snackBarMessage, 'New endpoint created successfully');
    mainPage.closeSnackbar();
    browser.waitForElementVisible(restEndpoints);
    restEndpoints.verifyEndpointRow(2, 'POST', '/');

    // verify no edit button on autogenerated endpoint
    browser.waitForElementVisible('//table[.//*[contains(text(),"HTTP Request Method")]]//tr[.//*[contains(text(),"POST")] and .//*[contains(text(),"/testreq3")]]//button[@data-test="endpoint-edit"]');
    browser.waitForElementVisible('//table[.//*[contains(text(),"HTTP Request Method")]]//tr[.//*[contains(text(),"GET")] and .//*[contains(text(),"/autogenerated1")]]');
    browser.waitForElementNotPresent('//table[.//*[contains(text(),"HTTP Request Method")]]//tr[.//*[contains(text(),"GET")] and .//*[contains(text(),"/autogenerated1")]]//button[@data-test="endpoint-edit"]');


    browser.end();
  },
  'Security server client edit openapi endpoints': browser => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const clientServices = clientInfo.section.services;
    const operationDetails = mainPage.section.restOperationDetails;
    const restEndpoints = mainPage.section.restServiceEndpoints;
    const endpointPopup = mainPage.section.editEndpointPopup;

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Navigate
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openTestService();
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
    endpointPopup.enterPath('');
    browser.assert.containsText(endpointPopup.elements.requestPathMessage, 'The path field is required');

    // test cancel
    endpointPopup.enterPath('/newreq1');
    endpointPopup.selectRequestMethod('PUT');
    endpointPopup.cancel();
    browser.waitForElementVisible(restEndpoints);
    browser.waitForElementNotPresent('//table[.//thead[.//*[contains(text(),"HTTP Request Method")]]]//*[contains(text(),"/newreq1")]');
    restEndpoints.verifyEndpointRow(3, 'POST', '/testreq2');

    // Verify uniqueness
    restEndpoints.openEndpoint('POST', '/testreq2');
    endpointPopup.enterPath('/testreq3');
    endpointPopup.addSelected();
    browser.assert.containsText(mainPage.elements.snackBarMessage, 'Endpoint with equivalent service code, method and path already exists for this client');
    mainPage.closeSnackbar();

    // Verify edit
    endpointPopup.enterPath('/newreq1');
    endpointPopup.selectRequestMethod('PUT');
    endpointPopup.addSelected();
    browser.assert.containsText(mainPage.elements.snackBarMessage, 'Changes to endpoint saved successfully');
    mainPage.closeSnackbar();
    browser.waitForElementVisible(restEndpoints);
    restEndpoints.verifyEndpointRow(5, 'PUT', '/newreq1');

    // Verify cancel delete
    restEndpoints.openEndpoint('POST', '/testreq3');
    endpointPopup.deleteEndpoint();
    browser.waitForElementVisible('//*[contains(@data-test, "dialog-title") and contains(text(), "Delete endpoint")]');
    endpointPopup.cancelDelete();
    endpointPopup.cancel();
    browser.waitForElementVisible(restEndpoints);

    // Verify confirm delete
    restEndpoints.openEndpoint('POST', '/testreq3');
    endpointPopup.deleteEndpoint();
    browser.waitForElementVisible('//*[contains(@data-test, "dialog-title") and contains(text(), "Delete endpoint")]');
    endpointPopup.confirmDelete();
    browser.assert.containsText(mainPage.elements.snackBarMessage, 'Endpoint removed successfully');
    mainPage.closeSnackbar();
    browser.waitForElementNotPresent('//table[.//thead[.//*[contains(text(),"HTTP Request Method")]]]//*[contains(text(),"/testreq3")]');

    browser.end();
  },
  'Security server client edit openapi service': browser => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const clientServices = clientInfo.section.services;
    const openApiServiceDetails = mainPage.section.openApiServiceDetails;

    var startTime, startTimestamp;

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Navigate
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openTestService();
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);

    clientServices.expandServiceDetails();

    browser
      .getText(clientServices.elements.refreshTimestamp, function(result) {
        startTimestamp = result.value;
        startTime = new Date().getTime();;
      })

    // Verify enabling
    clientServices.toggleEnabled();
    browser.assert.containsText(mainPage.elements.snackBarMessage, 'Service description enabled');
    mainPage.closeSnackbar();

    // Verify disabling and canceling disable
    clientServices.toggleEnabled();
    browser.waitForElementVisible('//*[contains(@data-test, "dialog-title") and contains(text(),"Disable?")]')
    clientServices.enterDisableNotice('Message1');
    clientServices.cancelDisable();
    clientServices.toggleEnabled();
    browser.waitForElementVisible('//*[contains(@data-test, "dialog-title") and contains(text(),"Disable?")]')
    browser.assert.value(clientServices.elements.disableNotice, '');
    clientServices.enterDisableNotice('Notice1');
    clientServices.confirmDisable();
    browser.assert.containsText(mainPage.elements.snackBarMessage, 'Service description disabled');
    mainPage.closeSnackbar();


    // Verify editing, malformed URL and service code
    clientServices.openServiceDetails();
    browser.assert.containsText(openApiServiceDetails.elements.serviceType, 'OpenAPI 3 Description');
    openApiServiceDetails.enterServiceCode('/');
    browser.expect.element(clientServices.elements.confirmAddServiceButton).to.not.be.enabled;
    browser.assert.containsText(clientServices.elements.serviceCodeMessage, 'Identifier value contains illegal characters');

    openApiServiceDetails.enterServiceCode('');
    browser.assert.containsText(openApiServiceDetails.elements.codeMessage, 'The fields.code_field field is required');
    openApiServiceDetails.enterServiceUrl("foobar")
    browser.assert.containsText(openApiServiceDetails.elements.URLMessage, 'URL is not valid'); //!!! REST message
    openApiServiceDetails.enterServiceUrl('');
    browser.assert.containsText(openApiServiceDetails.elements.URLMessage, 'The URL field is required');
    openApiServiceDetails.cancelDialog();

    // Part 1 wait until at least 1 min has passed since refresh at the start of the test
    // Split this wait into two parts to not cause timeouts
    browser.perform(function () {

      endTime = new Date().getTime();
      passedTime = endTime-startTime;
      if (passedTime < 30000) {
        console.log('Waiting', 30000 - passedTime, 'ms');
        browser.pause(30000 - passedTime);
      }
    });


    // verify missing file
    clientServices.openServiceDetails();
    openApiServiceDetails.enterServiceUrl('https://www.niis.org/nosuch.yaml');
    openApiServiceDetails.confirmDialog();
    browser.assert.containsText(mainPage.elements.snackBarMessage, 'Parsing OpenApi3 description failed');
    mainPage.closeSnackbar();

    // Verify cancel
    openApiServiceDetails.enterServiceUrl(browser.globals.testdata + '/' + browser.globals.openapi_url_2);
    openApiServiceDetails.enterServiceCode('s3c2');
    openApiServiceDetails.cancelDialog();
    browser.assert.containsText(clientServices.elements.serviceDescription,  'OPENAPI3 (' + browser.globals.testdata + '/' + browser.globals.openapi_url_1 + ')');
    browser.waitForElementVisible('//td[contains(@data-test, "service-link") and contains(text(),"s3c1")]');

    // Verify succesfull edit
    clientServices.openServiceDetails();
    openApiServiceDetails.enterServiceUrl(browser.globals.testdata + '/' + browser.globals.openapi_url_2);
    openApiServiceDetails.enterServiceCode('s3c2');

    // Part 2 wait until at least 1 min has passed since refresh at the start of the test
    browser.perform(function () {

      endTime = new Date().getTime();
      passedTime = endTime-startTime;
      if (passedTime < 60000) {
        console.log('Waiting', 60000 - passedTime, 'ms');
        browser.pause(60000 - passedTime);
      }
    });

    openApiServiceDetails.confirmDialog();

    browser.assert.containsText(mainPage.elements.snackBarMessage, 'Description saved');
    mainPage.closeSnackbar();
    browser.assert.containsText(clientServices.elements.serviceDescription, 'OPENAPI3 (' + browser.globals.testdata + '/' + browser.globals.openapi_url_2 + ')');
    browser.waitForElementNotPresent('//td[contains(@data-test, "service-link") and contains(text(),"s3c1")]');
    browser.waitForElementVisible('//td[contains(@data-test, "service-link") and contains(text(),"s3c2")]');

    // Verify that the refresh time has been updated
    browser.perform(function () {
      browser.expect.element(clientServices.elements.refreshTimestamp).text.to.not.contain(startTimestamp);
    });

    browser.end();

  },
  'Security server client delete openapi service': browser => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const clientServices = clientInfo.section.services;
    const openApiServiceDetails = mainPage.section.openApiServiceDetails

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Navigate
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openTestService();
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);

    // Verify cancel delete
    clientServices.openServiceDetails();
    browser.waitForElementVisible(openApiServiceDetails);
    openApiServiceDetails.deleteService();
    openApiServiceDetails.cancelDelete();

    openApiServiceDetails.closeServiceDetails();
    browser.assert.containsText(clientServices.elements.serviceDescription, 'OPENAPI3 (' + browser.globals.testdata + '/' + browser.globals.openapi_url_2 + ')');

    // Verify successful delete
    clientServices.openServiceDetails();
    openApiServiceDetails.deleteService();
    openApiServiceDetails.confirmDelete();

    browser.assert.containsText(mainPage.elements.snackBarMessage, 'Service description deleted');
    mainPage.closeSnackbar();

    browser.waitForElementNotPresent(clientServices.elements.serviceDescription);

    browser.end();

  }
};
