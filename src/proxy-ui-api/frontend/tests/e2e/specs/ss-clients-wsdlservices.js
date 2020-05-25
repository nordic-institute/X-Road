
module.exports = {
  tags: ['ss', 'clients', 'wsdlservices'],
  'Security server client add wsdl service': browser => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const clientServices = clientInfo.section.services;
    const servicesPopup = mainPage.section.servicesWarningPopup
    const serviceDetails = mainPage.section.serviceDetails

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

    // Verify empty and malformed URL error messages
    clientServices.openAddWSDL();
    clientServices.enterServiceUrl('a');
    clientServices.enterServiceUrl('');
    browser.assert.containsText(clientServices.elements.serviceUrlMessage, 'The URL field is required');
    clientServices.enterServiceUrl('foobar');
    browser.assert.containsText(clientServices.elements.serviceUrlMessage, 'WSDL URL is not valid');
    clientServices.cancelAddDialog();

    // Verify that URL field is empty after reopening
    clientServices.openAddWSDL();
    browser.assert.value(clientServices.elements.newServiceUrl, '');

    // Verify opening nonexisting URL
    clientServices.enterServiceUrl('https://www.niis.org/nosuch.wsdl');
    clientServices.confirmAddDialog();
    browser.waitForElementVisible('//*[contains(@class, "v-snack") and .//*[contains(text(), "Request failed with status code 400")]]');
    mainPage.closeSnackbar();

    // Verify successfull URL open
    clientServices.openAddWSDL();
    clientServices.enterServiceUrl(browser.globals.testdata + '/' + browser.globals.wsdl_url_1);
    clientServices.confirmAddDialog();
    browser.assert.containsText(clientServices.elements.serviceDescription, browser.globals.testdata + '/' + browser.globals.wsdl_url_1);
   
    clientServices.expandServiceDetails();
    browser.waitForElementVisible('//td[contains(@data-test, "service-link") and contains(text(),"testOp1")]');

    browser.end();
  },
  'Security server client edit wsdl service': browser => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const clientServices = clientInfo.section.services;
    const servicesPopup = mainPage.section.servicesWarningPopup
    const serviceDetails = mainPage.section.serviceDetails

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

    clientServices.refreshServiceData();
    browser.waitForElementVisible('//*[contains(@class, "v-snack") and .//*[contains(text(), "Refreshed")]]');
    mainPage.closeSnackbar();

    browser
      .getText(clientServices.elements.refreshTimestamp, function(result) {
        startTimestamp = result.value;
        startTime = new Date().getTime();;
      })

    // Verify enabling
    clientServices.toggleEnabled();
    browser.waitForElementVisible('//*[contains(@class, "v-snack") and .//*[contains(text(), "Service description enabled")]]');
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
    browser.waitForElementVisible('//*[contains(@class, "v-snack") and .//*[contains(text(), "Service description disabled")]]');
    mainPage.closeSnackbar();

    clientServices.toggleEnabled();
    mainPage.closeSnackbar();

    // Verify editing, malformed URL
    clientServices.openServiceDetails();
    serviceDetails.enterServiceUrl("foobar")
    browser.assert.containsText(serviceDetails.elements.URLMessage, 'WSDL URL is not valid');
    serviceDetails.enterServiceUrl('');
    browser.assert.containsText(serviceDetails.elements.URLMessage, 'The URL field is required');


    // verify missing file
    serviceDetails.enterServiceUrl('https://www.niis.org/nosuch.wsdl');
    serviceDetails.confirmDialog();
    browser.waitForElementVisible('//*[contains(@class, "v-snack") and .//*[contains(text(), "Request failed with status code 400")]]');
    mainPage.closeSnackbar();

    // Verify cancel
    serviceDetails.enterServiceUrl(browser.globals.testdata + '/' + browser.globals.wsdl_url_2);
    serviceDetails.cancelDialog();
    browser.assert.containsText(clientServices.elements.serviceDescription, 'WSDL ('+ browser.globals.testdata + '/' + browser.globals.wsdl_url_1+')');

    // Verify succesfull edit
    clientServices.openServiceDetails();
    serviceDetails.enterServiceUrl(browser.globals.testdata + '/' + browser.globals.wsdl_url_2);
    serviceDetails.confirmDialog();
    browser.waitForElementVisible(servicesPopup);
 
    // Wait until at least 1 min has passed since refresh at the start of the test
    browser.perform(function () {

      endTime = new Date().getTime();
      passedTime = endTime-startTime;
      if (passedTime < 60000) {
        console.log('Waiting', 60000 - passedTime, 'ms');
        browser.pause(60000 - passedTime);
      }
    });

    servicesPopup.accept();     
    browser.waitForElementVisible('//*[contains(@class, "v-snack") and .//*[contains(text(), "Description saved")]]');
    mainPage.closeSnackbar();
    browser.assert.containsText(clientServices.elements.serviceDescription, 'WSDL ('+ browser.globals.testdata + '/' + browser.globals.wsdl_url_2+')');
    browser.waitForElementNotPresent('//td[contains(@data-test, "service-link") and contains(text(),"testOp1")]');
    browser.waitForElementVisible('//td[contains(@data-test, "service-link") and contains(text(),"testOp2")]');

    // Verify that the refresh time has been updated
    browser.perform(function () {
      browser.expect.element(clientServices.elements.refreshTimestamp).text.to.not.contain(startTimestamp);
    });

    browser.end();

  },
  'Security server client delete wsdl service': browser => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;
    const clientInfo = mainPage.section.clientInfo;
    const clientServices = clientInfo.section.services;
    const serviceDetails = mainPage.section.serviceDetails

    // Open SUT and check that page is loaded
    frontPage.navigate();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Open TestGov Internal Servers
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);
    clientsTab.openTestService();
    browser.waitForElementVisible(clientInfo);
    clientInfo.openServicesTab();
    browser.waitForElementVisible(clientServices);

    // Verify cancel delete
    clientServices.openServiceDetails();
    browser.waitForElementVisible(serviceDetails);
    serviceDetails.deleteService();
    serviceDetails.cancelDelete();

    serviceDetails.closeServiceDetails();
    browser.assert.containsText(clientServices.elements.serviceDescription, 'WSDL ('+ browser.globals.testdata + '/' + browser.globals.wsdl_url_2+')');

    // Verify successful delete
    clientServices.openServiceDetails();
    serviceDetails.deleteService();
    serviceDetails.confirmDelete();

    browser.waitForElementVisible('//*[contains(@class, "v-snack") and .//*[contains(text(), "Service description deleted")]]');
    mainPage.closeSnackbar();

    browser.waitForElementNotPresent(clientServices.elements.serviceDescription);

    browser.end();

  }
};
