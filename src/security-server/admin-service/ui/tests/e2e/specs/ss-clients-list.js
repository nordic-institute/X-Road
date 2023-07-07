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
  tags: ['ss', 'clients', 'clientslist'],
  'Security server clients list': (browser) => {
    const frontPage = browser.page.ssLoginPage();
    const mainPage = browser.page.ssMainPage();
    const clientsTab = mainPage.section.clientsTab;

    // Open SUT and check that page is loaded
    frontPage.navigateAndMakeTestable();
    browser.waitForElementVisible('//*[@id="app"]');

    // Enter valid credentials
    frontPage.signinDefaultUser();

    // Switch to clients tab and verify
    mainPage.openClientsTab();
    browser.waitForElementVisible(clientsTab);

    // Verify default sorting and list items
    browser.waitForElementVisible(
      '//th[contains(@class, "xrd-table-header-name") and contains(@class, "active") and contains(@aria-sort, "ascending")]',
    );
    clientsTab.verifyRowName(1, 'TestGov');
    clientsTab.verifyRowName(2, 'TestSaved');
    clientsTab.verifyRowName(3, 'TestService');
    clientsTab.verifyRowName(4, 'TestSub');
    clientsTab.verifyRowName(5, 'TestCom');
    clientsTab.verifyRowName(6, 'TestClient');
    clientsTab.verifyRowName(7, 'TestOrg');
    clientsTab.verifyRowName(8, 'Management');

    // Re-sort by name and verify re-sorted list item positions
    clientsTab.clickNameHeader();
    browser.waitForElementVisible(
      '//th[contains(@class, "xrd-table-header-name") and contains(@class, "active") and contains(@aria-sort, "descending")]',
    );

    clientsTab.verifyRowName(1, 'TestGov');
    clientsTab.verifyRowName(2, 'TestSub');
    clientsTab.verifyRowName(3, 'TestService');
    clientsTab.verifyRowName(4, 'TestSaved');
    clientsTab.verifyRowName(5, 'TestOrg');
    clientsTab.verifyRowName(6, 'Management');
    clientsTab.verifyRowName(7, 'TestCom');
    clientsTab.verifyRowName(8, 'TestClient');

    // Sort by ID and verify new sorting and list items
    clientsTab.clickIDHeader();
    browser
      .waitForElementVisible(
        '//th[contains(@class, "xrd-table-header-id") and contains(@class, "active") and contains(@aria-sort, "ascending")]',
      )
      .waitForElementVisible(
        '//th[contains(@class, "xrd-table-header-name") and contains(@aria-sort, "none")]',
      );

    clientsTab.verifyRowName(1, 'TestGov');
    clientsTab.verifyRowName(2, 'TestSaved');
    clientsTab.verifyRowName(3, 'TestService');
    clientsTab.verifyRowName(4, 'TestSub');
    clientsTab.verifyRowName(5, 'TestCom');
    clientsTab.verifyRowName(6, 'TestClient');
    clientsTab.verifyRowName(7, 'TestOrg');
    clientsTab.verifyRowName(8, 'Management');

    // Re-sort by ID and verify list items
    clientsTab.clickIDHeader();
    browser.waitForElementVisible(
      '//th[contains(@class, "xrd-table-header-id") and contains(@class, "active") and contains(@aria-sort, "descending")]',
    );

    clientsTab.verifyRowName(1, 'TestGov');
    clientsTab.verifyRowName(2, 'TestSub');
    clientsTab.verifyRowName(3, 'TestService');
    clientsTab.verifyRowName(4, 'TestSaved');
    clientsTab.verifyRowName(5, 'TestCom');
    clientsTab.verifyRowName(6, 'TestClient');
    clientsTab.verifyRowName(7, 'TestOrg');
    clientsTab.verifyRowName(8, 'Management');

    // Sort by Status and verify items
    clientsTab.clickStatusHeader();
    browser.waitForElementVisible(
      '//th[contains(@class, "xrd-table-header-status") and contains(@class, "active") and contains(@aria-sort, "ascending")]',
    );

    clientsTab.verifyRowName(1, 'TestGov');
    clientsTab.verifyRowName(2, 'TestService');
    clientsTab.verifyRowName(3, 'TestSub');
    clientsTab.verifyRowName(4, 'TestSaved');
    clientsTab.verifyRowName(5, 'TestCom');
    clientsTab.verifyRowName(6, 'TestClient');
    clientsTab.verifyRowName(7, 'TestOrg');
    clientsTab.verifyRowName(8, 'Management');

    // Re-sort by Status and verify list items
    clientsTab.clickStatusHeader();
    browser.waitForElementVisible(
      '//th[contains(@class, "xrd-table-header-status") and contains(@class, "active") and contains(@aria-sort, "descending")]',
    );

    clientsTab.verifyRowName(1, 'TestGov');
    clientsTab.verifyRowName(2, 'TestSaved');
    clientsTab.verifyRowName(3, 'TestSub');
    clientsTab.verifyRowName(4, 'TestService');
    clientsTab.verifyRowName(5, 'TestCom');
    clientsTab.verifyRowName(6, 'TestClient');
    clientsTab.verifyRowName(7, 'TestOrg');
    clientsTab.verifyRowName(8, 'Management');

    browser.end();
  },
};
