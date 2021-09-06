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

var assert = require('assert');

let mainPage, settingsTab, systemParametersTab;

module.exports = {
  tags: ['ss', 'settings', 'timestamping'],
  before: function (browser) {
    // Populate pageObjects for whole test suite
    mainPage = browser.page.ssMainPage();
    settingsTab = mainPage.section.settingsTab;
    systemParametersTab = settingsTab.sections.systemParametersTab;
    browser.LoginCommand(browser.globals.login_usr, browser.globals.login_pwd);
    mainPage.openSettingsTab();
    browser.waitForElementVisible(settingsTab);
  },

  after: function (browser) {
    browser.end();
  },

  'User can add new service': (browser) => {
    // when add is clicked, dialog showing available items are being shown
    systemParametersTab.openTimeStampingAddDialog();

    // browser.click(systemParametersTab.elements.timestampingAddButton);
    // browser.waitForElementVisible('//input[@value="X-Road Test TSA CN"]/../../label');

    // only one selection is possible, eg. selection is radio-button
    browser.click('//input[@value="X-Road Test TSA CN"]/../../label');

    // adding can be cancelled
    browser.click(
      systemParametersTab.elements.timestampingDeleteDialogCancelButton,
    );
    browser.waitForElementNotVisible(
      systemParametersTab.elements.timestampingDeleteDialogCancelButton,
    );

    browser.waitForElementNotPresent(
      systemParametersTab.elements.timestampingServiceTableRow,
    );

    // cancelling after selecting doesn't yet add server
    browser.click(systemParametersTab.elements.timestampingAddButton);
    browser.click('//input[@value="X-Road Test TSA CN"]/../../label');

    browser.click(
      systemParametersTab.elements.timestampingDeleteDialogCancelButton,
    );
    browser.waitForElementNotPresent(
      systemParametersTab.elements.timestampingServiceTableRow,
    );
    // adding can be cancelled

    // clicking add updates table, closes dialog and gives success message.
    browser.click(systemParametersTab.elements.timestampingAddButton);
    browser.click('//input[@value="X-Road Test TSA CN"]/../../label');
    browser.click(systemParametersTab.elements.timestampingAddDialogAddButton);
    browser.getText(
      '//tr[@data-test="system.parameters-timestamping-service-row"]/td[1]',
      function (text) {
        assert.equal(text.value, 'X-Road Test TSA CN');
      },
    );
    browser.getText(
      '//tr[@data-test="system.parameters-timestamping-service-row"]/td[2]',
      function (text) {
        assert.equal(text.value, 'http://cs:8899');
      },
    );
    browser.click(systemParametersTab.elements.timestampingAddDialogAddButton);

    // add button is disabled if there is no new services
    browser.waitForElementVisible(
      systemParametersTab.elements.timestampingServiceTableRow,
    );
    browser.waitForElementPresent(
      '//button[@data-test="system-parameters-timestamping-services-add-button" and @disabled="disabled"]',
    );
  },

  'Timestamp-table is visible': (browser) => {
    // Service table row is visible
    browser.waitForElementVisible(
      systemParametersTab.elements.timestampingServiceTableRow,
    );
    // Delete button is visible in row
    browser.waitForElementVisible(
      systemParametersTab.elements.timestampingDeleteButton,
    );
    // list shows name and url of the service
    browser.getText(
      '//tr[@data-test="system.parameters-timestamping-service-row"]/td[1]',
      function (text) {
        assert.equal(text.value, 'X-Road Test TSA CN');
      },
    );
    browser.getText(
      '//tr[@data-test="system.parameters-timestamping-service-row"]/td[2]',
      function (text) {
        assert.equal(text.value, 'http://cs:8899');
      },
    );
  },
  'service deletion can be cancelled': (browser) => {
    browser.waitForElementVisible(
      systemParametersTab.elements.timestampingDeleteButton,
    );
    // When delete is pressed, confirmation dialog is shown
    browser.click(systemParametersTab.elements.timestampingDeleteButton);

    // When delete is cancelled in dialog, nothing happens
    browser.waitForElementVisible(
      systemParametersTab.elements.timestampingDeleteDialog,
    );
    browser.click(
      systemParametersTab.elements.timestampingDeleteDialogCancelButton,
    );
  },
  'timestamping service can be deleted': (browser) => {
    // when user confirms:
    browser.click(systemParametersTab.elements.timestampingDeleteButton);
    browser.click(
      systemParametersTab.elements.timestampingDeleteDialogSaveButton,
    );
    // dialog is closed
    browser.waitForElementNotPresent(
      systemParametersTab.elements.timestampingDeleteDialog,
    );

    // table is emptied
    browser.waitForElementNotPresent(
      systemParametersTab.elements.timestampingServiceTableRow,
    );

    // "timestamping service is no longer added"
    // success message is shown
  },

  /*

Acceptance criteria:
 * Tests are added to the subpage Settings > System Parameters
 * It is checked that the page contains the timestamping services table
    ** The table lists timestamping services that have been added to the Security Server
    ** The table rows include information about the timestamping service name and URL
    ** The table rows include the "Delete" action for each timestamping service
 * When the user clicks "Delete" on a row, a confirmation is asked if they want to remove the timestamping service
    ** If the user confirms, the confirmation is closed, the table updated to not include the service anymore and the timestamping service is no longer added to the server and a success message is shown
    ** If the user cancels, the confirmation is closed and the service remains added to the server and the table
 * There is a button to add a timestamping service to the server
   ** If there are no additional services available in the global configuration, the add button is disabled
   ** If the add button is clicked, a dialog is opened showing the user a list of timestamping services not yet added to the server
   ** The "Add" button is disabled if no service is chosen, but the user can still cancel
   ** The user is able to select only one service at a time from the list
   ** If the user clicks "Add" after choosing a service
     *** The dialog is closed
     *** The table is updated to include the newly added service
     *** The service is added to the server
     *** A success message is shown
   ** If the user cancels, the service is not added and the dialog is closed
*/
};
