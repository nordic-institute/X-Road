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
  tags: ['addServiceClientSubject'],
  'Security server service clients Add Subject dialog filters subjects': (
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

    // Add wsdl
    clientsTab.openTestService();
    browser.waitForElementVisible(clientInfo);
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

    // Navigate to service clients -tab
    clientInfo.openServiceClientsTab();
    browser.waitForElementVisible(serviceClientsPage.section.serviceClientsTab);

    // Verify buttons are visible
    browser.expect.element(serviceClientsPage.elements.addServiceClientButton)
      .to.be.visible;
    browser.expect.element(serviceClientsPage.elements.unregisterButton).to.be
      .visible;

    // Filter subjects in Add Subjects dialog
    serviceClientsPage.openAddServiceClient();
    browser.waitForElementVisible(
      serviceClientsPage.elements.addSubjectWizardHeader,
    );
    serviceClientsPage.cancel();

    // Remove WSDL service description
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);
    clientServices.openServiceDetails();
    browser.waitForElementVisible(serviceDetails);
    serviceDetails.deleteService();
    serviceDetails.confirmDelete();
    browser.assert.containsText(
      mainPage.elements.snackBarMessage,
      'Service description deleted',
    );
    mainPage.closeSnackbar();
    browser.end();
  },
};
