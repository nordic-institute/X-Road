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

const navigateToServiceClientsTab = (pages) => {
  const {
    browser,
    mainPage,
    clientInfo,
    clientsTab,
    serviceClientsPage,
  } = pages;

  mainPage.openClientsTab();
  browser.waitForElementVisible(clientsTab);
  clientsTab.openClient('TestClient');
  browser.waitForElementVisible(clientInfo);
  browser.expect.element(clientInfo.elements.serviceClientsTab).to.be.visible;
  clientInfo.openServiceClientsTab();
  browser.waitForElementVisible(serviceClientsPage.section.serviceClientsTab);
};

module.exports = {
  tags: ['ss', 'clients', 'serviceclients'],
  'Security server service clients check services': (browser) => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const serviceClientsPage = browser.page.serviceClients.serviceClientsPage();
    const serviceClientDetails = browser.page.serviceClients.serviceClientDetails();
    const pages = {
      browser,
      frontPage,
      mainPage,
      clientsTab,
      clientInfo,
      serviceClientsPage,
      serviceClientDetails,
    };

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    navigateToServiceClientsTab(pages);
    serviceClientsPage.openServiceClient('TestCom');

    // check displayed info
    browser.waitForElementVisible(
      '//table[@data-test="service-clients-table"]',
    );

    browser.assert.containsText(
      '//table[@data-test="service-clients-table"]//td[contains(@class, "identifier-wrap")]', 'TestCom',
    );
    browser.waitForElementVisible(
      '//table[@data-test="service-clients-table"]//td[contains(@class, "identifier-wrap") and contains(text(), "TestClient")]',
    );

    browser.waitForElementVisible(
      '//table[@data-test="service-client-access-rights-table"]',
    );

    serviceClientDetails.verifyAccessRightVisible('testOp1');

    browser.end();
  },
  'Security server service clients add access rights': (browser) => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const serviceClientsPage = browser.page.serviceClients.serviceClientsPage();
    const serviceClientDetails = browser.page.serviceClients.serviceClientDetails();
    const addServicesPopup = serviceClientDetails.section.addServicesPopup;
    const pages = {
      browser,
      frontPage,
      mainPage,
      clientsTab,
      clientInfo,
      serviceClientsPage,
      serviceClientDetails,
    };

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    navigateToServiceClientsTab(pages);
    serviceClientsPage.openServiceClient('TestCom');

    serviceClientDetails.verifyAccessRightVisible('testOp1');

    // add service, filter and cancel

    serviceClientDetails.addService();
    browser.waitForElementVisible(addServicesPopup);
    browser.waitForElementVisible(
      '//tr[contains(@class, "service-row")]//td[contains(text(), "testOp2")]',
    );
    browser.waitForElementVisible(
      '//tr[contains(@class, "service-row")]//td[contains(text(), "testOpA")]',
    );
    browser.waitForElementVisible(
      '//tr[contains(@class, "service-row")]//td[contains(text(), "testOpX")]',
    );
    addServicesPopup.setSearch('testOpX');
    browser.waitForElementVisible(
      '//tr[contains(@class, "service-row")]//td[contains(text(), "testOpX")]',
    );
    browser.waitForElementNotPresent(
      '//tr[contains(@class, "service-row")]//td[contains(text(), "testOp2")]',
    );
    browser.waitForElementNotPresent(
      '//tr[contains(@class, "service-row")]//td[contains(text(), "testOpA")]',
    );
    addServicesPopup.selectService('testOpX');
    addServicesPopup.cancel();

    serviceClientDetails.verifyAccessRightNotPresent('testOpX');

    // add service, success

    serviceClientDetails.addService();
    browser.waitForElementVisible(addServicesPopup);
    addServicesPopup.selectService('testOpX');
    addServicesPopup.addSelected();

    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Access rights successfully added'
    mainPage.closeSnackbar();

    serviceClientDetails.verifyAccessRightVisible('testOpX');

    // add multiple services

    serviceClientDetails.addService();
    browser.waitForElementVisible(addServicesPopup);
    addServicesPopup.selectService('testOpA');
    addServicesPopup.selectService('testOp2');
    addServicesPopup.addSelected();

    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Access rights successfully added'
    mainPage.closeSnackbar();

    serviceClientDetails.verifyAccessRightVisible('testOp2');
    serviceClientDetails.verifyAccessRightVisible('testOpA');

    browser.end();
  },
  'Security server service clients remove access rights': (browser) => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const serviceClientsPage = browser.page.serviceClients.serviceClientsPage();
    const serviceClientDetails = browser.page.serviceClients.serviceClientDetails();
    const removeAccessRightPopup = mainPage.section.removeAccessRightPopup;
    const removeAllAccessRightsPopup =
      mainPage.section.removeAllAccessRightsPopup;
    const pages = {
      browser,
      frontPage,
      mainPage,
      clientsTab,
      clientInfo,
      serviceClientsPage,
      serviceClientDetails,
    };

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    navigateToServiceClientsTab(pages);
    serviceClientsPage.openServiceClient('TestCom');

    // Remove access right, cancel

    serviceClientDetails.removeAccessRight('testOp2');
    browser.waitForElementVisible(removeAccessRightPopup);
    removeAccessRightPopup.cancel();
    serviceClientDetails.verifyAccessRightVisible('testOp2');

    // Remove access right, confirm

    serviceClientDetails.removeAccessRight('testOp2');
    browser.waitForElementVisible(removeAccessRightPopup);
    removeAccessRightPopup.confirm();

    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Access rights successfully removed'
    mainPage.closeSnackbar();

    serviceClientDetails.verifyAccessRightNotPresent('testOp2');
    serviceClientDetails.verifyAccessRightVisible('testOp1');
    serviceClientDetails.verifyAccessRightVisible('testOpA');
    serviceClientDetails.verifyAccessRightVisible('testOpX');

    // Remove all, cancel

    serviceClientDetails.removeAll();
    browser.waitForElementVisible(removeAllAccessRightsPopup);
    removeAllAccessRightsPopup.cancel();
    serviceClientDetails.verifyAccessRightVisible('testOp1');
    serviceClientDetails.verifyAccessRightVisible('testOpA');
    serviceClientDetails.verifyAccessRightVisible('testOpX');
    // Remove all, success

    serviceClientDetails.removeAll();
    browser.waitForElementVisible(removeAllAccessRightsPopup);
    removeAllAccessRightsPopup.confirm();

    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Access Rights successfully removed'
    mainPage.closeSnackbar();

    serviceClientDetails.verifyAccessRightNotPresent('testOp1');
    serviceClientDetails.verifyAccessRightNotPresent('testOpA');
    serviceClientDetails.verifyAccessRightNotPresent('testOpX');

    browser.end();
  },
  'Security server service clients list shows wsdl service with access rights': (
    browser,
  ) => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const serviceClientsPage = browser.page.serviceClients.serviceClientsPage();

    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const operationDetails = mainPage.section.wsdlOperationDetails;
    const addSubjectsPopup = mainPage.section.wsdlAddSubjectsPopup;
    const serviceDetails = mainPage.section.serviceDetails;
    const clientServices = clientInfo.section.services;

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Navigate to service clients -tab
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openTestService();
    browser.waitForElementVisible(clientInfo);
    browser.expect.element(clientInfo.elements.serviceClientsTab).to.be.visible;
    clientInfo.openServiceClientsTab();
    browser.waitForElementVisible(serviceClientsPage.section.serviceClientsTab);

    // Verify buttons are visible
    browser.expect.element(serviceClientsPage.elements.addServiceClientButton)
      .to.be.visible;
    browser.expect.element(serviceClientsPage.elements.unregisterButton).to.be
      .visible;

    // Add wsdl
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);
    clientServices.openAddWSDL();
    clientServices.enterServiceUrl(
      browser.globals.testdata + '/' + browser.globals.wsdl_url_1,
    );
    clientServices.confirmAddDialog();
    browser.assert.containsText(
      clientServices.elements.serviceDescription,
      browser.globals.testdata + '/' + browser.globals.wsdl_url_1,
    );
    mainPage.closeSnackbar();

    // Add access right to wsdl service
    clientServices.expandServiceDetails();
    clientServices.openOperation('testOp1');
    browser.waitForElementVisible(operationDetails);
    operationDetails.openAddAccessRights();
    browser.waitForElementVisible(addSubjectsPopup);
    addSubjectsPopup.startSearch();
    addSubjectsPopup.selectSubject('TestOrg');
    addSubjectsPopup.addSelected();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Access rights added successfully'
    mainPage.closeSnackbar();
    operationDetails.close();

    // Verify SOAP service client when it has access permissions
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openTestService();
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServiceClientsTab();
    browser.waitForElementVisible(serviceClientsPage.section.serviceClientsTab);
    browser.waitForElementVisible('//tr[td[contains(text(),"TestOrg")]]');

    // Remove WSDL service description
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);
    clientServices.openServiceDetails();
    browser.waitForElementVisible(serviceDetails);
    serviceDetails.deleteService();
    serviceDetails.confirmDelete();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Service description deleted'
    mainPage.closeSnackbar();

    browser.end();
  },
  'Security server service clients list shows rest service with service level access right': (
    browser,
  ) => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const serviceClientsPage = browser.page.serviceClients.serviceClientsPage();
    const endpointAccessRightsPage = browser.page.endpoints.accessRightsPage();

    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const restOperationDetails = mainPage.section.restOperationDetails;
    const addSubjectsPopup = mainPage.section.wsdlAddSubjectsPopup;
    const restEndpoints = mainPage.section.restServiceEndpoints;
    const clientServices = clientInfo.section.services;
    const addEndpointPopup = mainPage.section.addEndpointPopup;
    const addEndpointAccessRightPopup =
      endpointAccessRightsPage.section.addSubjectsPopup;
    const removeAllAccessRightsPopup =
      mainPage.section.removeAllAccessRightsPopup;
    const serviceDetails = mainPage.section.restServiceDetails;

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Navigate to service clients -tab
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openTestService();
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);

    // Add first rest service to be used
    clientServices.openAddREST();
    clientServices.enterServiceUrl(
      browser.globals.testdata + '/' + browser.globals.rest_url_1,
    );
    clientServices.selectRESTPath();
    clientServices.enterServiceCode('s1c1');
    clientServices.confirmAddDialog();

    // Add service level access right for rest
    clientServices.expandServiceDetails();
    clientServices.openOperation('s1c1');
    browser.waitForElementVisible(restOperationDetails);
    restOperationDetails.openAddAccessRights();
    browser.waitForElementVisible(addSubjectsPopup);
    addSubjectsPopup.startSearch();
    addSubjectsPopup.selectSubject('TestOrg');
    addSubjectsPopup.addSelected();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Access rights added successfully'
    mainPage.closeSnackbar();
    restOperationDetails.close();

    // Verify REST service client when it has access permissions
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openTestService();
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServiceClientsTab();
    browser.waitForElementVisible(serviceClientsPage.section.serviceClientsTab);
    browser.waitForElementVisible('//tr[td[contains(text(),"TestOrg")]]');

    // Add endpoint for the rest service
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);
    clientServices.expandServiceDetails();
    clientServices.openOperation('s1c1');
    browser.waitForElementVisible(restOperationDetails);
    restOperationDetails.openEndpointsTab();
    browser.waitForElementVisible(restEndpoints);
    restEndpoints.openAddDialog();
    browser.waitForElementVisible(addEndpointPopup);
    addEndpointPopup.enterPath('/test');
    addEndpointPopup.selectRequestMethod('POST');
    addEndpointPopup.addSelected();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'New endpoint created successfully'
    mainPage.closeSnackbar();
    browser.waitForElementVisible(restEndpoints);
    restEndpoints.close();

    // Verify REST service client when it has access permissions and endpoint without access permission
    clientInfo.openServiceClientsTab();
    browser.waitForElementVisible(serviceClientsPage.section.serviceClientsTab);
    browser.waitForElementVisible('//tr[td[contains(text(),"TestOrg")]]');

    // Add access right for the endpoint
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);
    clientServices.openOperation('s1c1');
    browser.waitForElementVisible(restOperationDetails);
    restOperationDetails.openEndpointsTab();
    browser.waitForElementVisible(restEndpoints);
    restEndpoints.openEndpointAccessRights('POST', '/test');
    endpointAccessRightsPage.verifyAccessRightsPage('/test');
    endpointAccessRightsPage.openAddSubjectsDialog();
    browser.waitForElementVisible(addEndpointAccessRightPopup);

    addEndpointAccessRightPopup.startSearch();
    addEndpointAccessRightPopup.selectSubject('TestOrg');
    addEndpointAccessRightPopup.addSelected();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Access rights added successfully'
    mainPage.closeSnackbar();
    endpointAccessRightsPage.close();
    browser.waitForElementVisible(restEndpoints);
    restEndpoints.close();

    // Verify REST service client when it has access permissions and endpoint with access permission
    clientInfo.openServiceClientsTab();
    browser.waitForElementVisible(serviceClientsPage.section.serviceClientsTab);
    browser.waitForElementVisible('//tr[td[contains(text(),"TestOrg")]]');

    // Remove service level access rights from the REST service
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);
    clientServices.openOperation('s1c1');
    browser.waitForElementVisible(restOperationDetails);
    restOperationDetails.removeAllAccessRights();
    browser.waitForElementVisible(removeAllAccessRightsPopup);
    removeAllAccessRightsPopup.confirm();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Access rights removed successfully'
    mainPage.closeSnackbar();

    // Verify service client doesn't exist when REST service has only endpoint level access rights
    restOperationDetails.close();
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openTestService();
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServiceClientsTab();
    browser.waitForElementVisible(serviceClientsPage.section.serviceClientsTab);
    browser.expect
      .elements('//tr[@data-test="open-access-rights"]')
      .count.to.equal(0);

    // Remove REST service description
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);
    clientServices.openServiceDetails();
    browser.waitForElementVisible(serviceDetails);
    serviceDetails.deleteService();
    serviceDetails.confirmDelete();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Service description deleted'
    mainPage.closeSnackbar();

    browser.end();
  },
  'Security server service client view filtering': (browser) => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const serviceClientsPage = browser.page.serviceClients.serviceClientsPage();

    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const restOperationDetails = mainPage.section.restOperationDetails;
    const addSubjectsPopup = mainPage.section.wsdlAddSubjectsPopup;
    const clientServices = clientInfo.section.services;
    const serviceDetails = mainPage.section.restServiceDetails;

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Navigate to service clients -tab
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openTestService();
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);

    // Add first rest service to be used
    clientServices.openAddREST();
    clientServices.enterServiceUrl(
      browser.globals.testdata + '/' + browser.globals.rest_url_1,
    );
    clientServices.selectRESTPath();
    clientServices.enterServiceCode('s1c1');
    clientServices.confirmAddDialog();

    // Add service level access right for rest
    clientServices.expandServiceDetails();
    clientServices.openOperation('s1c1');
    browser.waitForElementVisible(restOperationDetails);
    restOperationDetails.openAddAccessRights();
    browser.waitForElementVisible(addSubjectsPopup);
    addSubjectsPopup.startSearch();
    addSubjectsPopup.selectSubject('TestOrg');
    addSubjectsPopup.selectSubject('TestGov');
    addSubjectsPopup.selectSubject('Group1');
    addSubjectsPopup.selectSubject('Group3');
    addSubjectsPopup.addSelected();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Access rights added successfully'
    mainPage.closeSnackbar();
    restOperationDetails.close();

    // Verify REST service client when it has access permissions
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openTestService();
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServiceClientsTab();
    browser.waitForElementVisible(serviceClientsPage.section.serviceClientsTab);
    browser.waitForElementVisible('//tr[td[contains(text(),"TestOrg")]]');
    browser.waitForElementVisible('//tr[td[contains(text(),"TestGov")]]');
    browser.waitForElementVisible('//tr[td[contains(text(),"Group1")]]');
    browser.waitForElementVisible('//tr[td[contains(text(),"Group3")]]');

    // Verify filtering works
    browser.expect
      .elements('//tr[@data-test="open-access-rights"]')
      .count.to.equal(4);
    serviceClientsPage.enterServiceClientSearchWord('Test');
    browser.expect
      .elements('//tr[@data-test="open-access-rights"]')
      .count.to.equal(2);
    serviceClientsPage.enterServiceClientSearchWord('group');
    browser.expect
      .elements('//tr[@data-test="open-access-rights"]')
      .count.to.equal(2);
    serviceClientsPage.enterServiceClientSearchWord('management');
    browser.expect.element(
      '//tr[@data-test="open-access-rights"]//td[contains(text(), "TestOrg")]',
    ).to.be.visible;

    // Remove REST service description
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);
    clientServices.openServiceDetails();
    browser.waitForElementVisible(serviceDetails);
    serviceDetails.deleteService();
    serviceDetails.confirmDelete();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Service description deleted'
    mainPage.closeSnackbar();

    browser.end();
  },
};
