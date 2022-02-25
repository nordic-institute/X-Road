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

let frontPage, mainPage, serviceClientsPage, addSubjectMemberStepPage, addSubjectServiceStepPage, clientInfo, clientsTab;
let clientServices, serviceDetails;

module.exports = {
  tags: ['ss', 'clients', 'serviceclients', 'addserviceclient'],
  before: function (browser){
    frontPage = browser.page.ssLoginPage();
    mainPage = browser.page.ssMainPage();
    serviceClientsPage = browser.page.serviceClients.serviceClientsPage();
    addSubjectMemberStepPage = browser.page.serviceClients.addSubjectMemberStepPage();
    addSubjectServiceStepPage = browser.page.serviceClients.addSubjectServiceStepPage();
    clientInfo = mainPage.section.clientInfo;
    clientsTab =  mainPage.section.clientsTab;
    clientServices = clientInfo.section.services;
    serviceDetails = mainPage.section.wsdlServiceDetails;

    // sign in to clientstab 
    frontPage.navigateAndMakeTestable();
    browser.waitForElementVisible('//*[@id="app"]');
    // Enter valid credentials
    frontPage.signinDefaultUser();
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);

    // Setup services to proceed in the dialog
    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);
    clientServices.openAddWSDL();
    clientServices.initServiceUrl(
      browser.globals.testdata + '/' + browser.globals.wsdl_url_1,
    );
    clientServices.confirmAddDialog();
    browser.assert.containsText(
      clientServices.elements.serviceDescription,
      browser.globals.testdata + '/' + browser.globals.wsdl_url_1,
    );
    mainPage.logout();
    browser.waitForElementVisible(frontPage.elements.usernameInput);
  },

  beforeEach: function (browser){
    browser.LoginCommand();
    frontPage.navigateAndMakeTestable();
    browser.waitForElementVisible('//*[@id="app"]');
    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
    // Navigate to service clients -tab
    clientInfo.openServiceClientsTab();
    browser.waitForElementVisible(serviceClientsPage.section.serviceClientsTab);
    // Verify buttons are visible
    browser.expect.element(serviceClientsPage.elements.addServiceClientButton).to
      .be.visible;
    browser.expect.element(serviceClientsPage.elements.unregisterButton).to.be
      .visible;
    // Navigate to Add Subjects dialog
    serviceClientsPage.openAddServiceClient();
    browser.waitForElementVisible(
      addSubjectMemberStepPage.elements.addSubjectWizardHeader,
    );
  },
  afterEach: function (browser){
    mainPage.logout();
    browser.waitForElementVisible(frontPage.elements.usernameInput);
  },
  after: function (browser){
    browser.LoginCommand();
    // Remove WSDL service description
    frontPage.navigateAndMakeTestable();
    browser.waitForElementVisible('//*[@id="app"]');
    clientsTab.openClient('TestService');
    browser.waitForElementVisible(clientInfo);
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

  'Security server service clients Add Subject dialog filter and select subjects':
    (browser) => {
      // Check that we land on the first step (selecting the subject)
      addSubjectMemberStepPage.assertWizardFirstPage();
      addSubjectMemberStepPage.cancel();
      browser.expect.element(
        '//div[contains(@class, "xrd-view-title")][contains(text(), "TestService")]',
      ).to.be.visible;
      serviceClientsPage.openAddServiceClient();
      browser.waitForElementVisible(
        addSubjectMemberStepPage.elements.addSubjectWizardHeader,
      );

      // Check that all subjects exist
      addSubjectMemberStepPage
        .verifySubjectListRow('1122')
        .verifySubjectListRow('bac')
        .verifySubjectListRow('2233')
        .verifySubjectListRow('abb')
        .verifySubjectListRow('cbb')
        .verifySubjectListRow('1212')
        .verifySubjectListRow('security-server-owners')
        .verifySubjectListRow('TestClient')
        .verifySubjectListRow('TestService')
        .verifySubjectListRow('Management');
      // Filter subjects in Add Subjects dialog
      addSubjectMemberStepPage
        .setFilter('TestSe')
        .verifyVisibleId('TestService')
        .verifyNotPresentId('1122')
        .verifyNotPresentId('bac')
        .verifyNotPresentId('2233')
        .verifyNotPresentId('abb')
        .verifyNotPresentId('cbb')
        .verifyNotPresentId('1212')
        .verifyNotPresentId('security-server-owners')
        .verifyNotPresentId('TestClient')
        .verifyNotPresentId('Management');
      // Clear filtering
      addSubjectMemberStepPage
        .setFilter('')
        .verifySubjectListRow('1122')
        .verifySubjectListRow('bac')
        .verifySubjectListRow('2233')
        .verifySubjectListRow('abb')
        .verifySubjectListRow('cbb')
        .verifySubjectListRow('1212')
        .verifySubjectListRow('security-server-owners')
        .verifySubjectListRow('TestClient')
        .verifySubjectListRow('TestService')
        .verifySubjectListRow('Management');
      addSubjectMemberStepPage.assertNextButtonDisabled();
      addSubjectMemberStepPage.assertSelectedSubjectsCount(0);
      addSubjectMemberStepPage.selectSubject('TestService');
      addSubjectMemberStepPage.assertNextButtonEnabled();
      addSubjectMemberStepPage.assertSelectedSubjectsCount(1);
      addSubjectMemberStepPage.selectSubject('TestClient');
      addSubjectMemberStepPage.assertNextButtonEnabled();
      addSubjectMemberStepPage.assertSelectedSubjectsCount(1);
      addSubjectMemberStepPage.clickNext();
      // Check that we land on the second step (selecting the services)
      addSubjectServiceStepPage.assertWizardSecondPage();
      // Test cancel on second page
      addSubjectServiceStepPage.cancel();
      browser.waitForElementVisible(
        '//div[contains(@class, "xrd-view-title")][contains(text(), "TestService")]',
      );
    },
  'Security server service clients Add Subject service page filter and select services': (browser) => {
    addSubjectMemberStepPage.selectSubject('TestService');
    addSubjectMemberStepPage.clickNext();
    addSubjectServiceStepPage.assertWizardSecondPage().clickPreviousButton();
    addSubjectMemberStepPage
      .assertWizardFirstPage()
      .selectSubject('TestClient')
      .clickNext();
    addSubjectServiceStepPage
      .assertWizardSecondPage()
      .verifyServiceListRow('testOp1')
      .verifyServiceListRow('testOpA');
    addSubjectServiceStepPage
      .setFilter('1')
      .verifyVisibleService('testOp1')
      .verifyNotPresentService('testOpA');
    addSubjectServiceStepPage
      .setFilter('')
      .verifyServiceListRow('testOp1')
      .verifyServiceListRow('testOpA');
    addSubjectServiceStepPage.assertAddSelectedButtonDisabled();
    addSubjectServiceStepPage.selectService('testOp1');
    addSubjectServiceStepPage.assertSelectedServicesCount(1);
    addSubjectServiceStepPage.assertAddSelectedButtonEnabled();
    addSubjectServiceStepPage.selectService('testOpA');
    addSubjectServiceStepPage.assertSelectedServicesCount(2);
    addSubjectServiceStepPage.assertAddSelectedButtonEnabled();
    addSubjectServiceStepPage.clickAddSelectedButton();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage); // 'Access rights successfully added'
    browser.waitForElementVisible(
      '//div[contains(@class, "xrd-view-title")][contains(text(), "TestService")]',
    );
    browser.waitForElementVisible(
      '//*[@data-test="service-clients-main-view-table"]//div[contains(text(), "TestClient")]',
    );
    serviceClientsPage.openAddServiceClient();
    addSubjectMemberStepPage.verifyDisabledId('TestClient');
  },
};
