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

const fs = require('fs');
const backupAndRestore = require('../utils/backupAndRestore');
const navigate = require('../utils/navigate');

// page object variables
let mainPage,
  settingsTab,
  backupAndRestoreTab,
  deleteBackupConfirmationDialog,
  backupFileAlreadyExistsDialog,
  restoreConfirmationDialog;

module.exports = {
  tags: ['ss', 'backupandrestore'],
  before: function (browser) {
    // Page object variable declarations
    mainPage = browser.page.ssMainPage();
    settingsTab = mainPage.section.settingsTab;
    backupAndRestoreTab = settingsTab.section.backupAndRestoreTab;
    deleteBackupConfirmationDialog =
      backupAndRestoreTab.section.deleteBackupConfirmationDialog;
    backupFileAlreadyExistsDialog =
      backupAndRestoreTab.section.backupFileAlreadyExistsDialog;
    restoreConfirmationDialog =
      backupAndRestoreTab.section.restoreConfirmationDialog;

    // Navigate to backup and restore page when browser is started
    browser.LoginCommand();
    navigate.toRestoreAndBackup(browser);
  },
  after: (browser) => {
    browser.end();
  },
  'Security server backups can be created, listed, filtered and removed':
    async (browser) => {
      const createdBackupFileName = await backupAndRestore.createBackup(
        browser,
      );

      // Filtering backup list with the name of created backup there should be only one backup in the list
      backupAndRestoreTab.enterFilterInput(createdBackupFileName);
      browser.expect
        .elements('//div[@data-test=\'backup-restore-view\']//table/tbody/tr')
        .count.to.equal(1);
      backupAndRestoreTab.clearFilterInput();

      // Delete created backup (click cancel first time)
      browser.waitForElementVisible(
        `//div[@data-test='backup-restore-view']//table//td[text() = "${createdBackupFileName}"]/following-sibling::td//button[@data-test="backup-delete"]`,
      );
      backupAndRestoreTab.clickDeleteForBackup(createdBackupFileName);
      browser.waitForElementVisible(deleteBackupConfirmationDialog);
      deleteBackupConfirmationDialog.cancel();
      browser.waitForElementNotPresent(deleteBackupConfirmationDialog);

      backupAndRestore.deleteBackup(browser, createdBackupFileName);
    },
  'Download and import backup': async (browser) => {
    // delete existing backups from test dir
    const testDataDir = __dirname + browser.globals.e2etest_testdata + '/';
    const regex = /^conf_backup/;

    if (fs.existsSync(testDataDir)) {
      fs.readdirSync(testDataDir)
        .filter((file) => regex.test(file))
        .map((file) => fs.unlinkSync(testDataDir + file));
    }

    const createdBackupFileName = await backupAndRestore.createBackup(browser);

    // Download backupfile and make sure it's in the filesystem
    backupAndRestoreTab.clickDownloadForBackup(createdBackupFileName);
    browser.pause(5000);

    browser.perform(() =>
      browser.assert.equal(
        fs.existsSync(`${testDataDir}${createdBackupFileName}`),
        true,
        `Backup file should exist in: ${testDataDir}${createdBackupFileName}`,
      ),
    );

    // Import the created backup from local filesystem (first cancel the operation)
    backupAndRestoreTab
      .addBackupToInput(testDataDir + createdBackupFileName)
      .waitForElementVisible(backupFileAlreadyExistsDialog);

    backupFileAlreadyExistsDialog
      .cancel()
      .waitForElementNotPresent(backupFileAlreadyExistsDialog);

    backupAndRestoreTab
      .addBackupToInput(testDataDir + createdBackupFileName)
      .waitForElementVisible(backupFileAlreadyExistsDialog);
    backupFileAlreadyExistsDialog
      .confirm()
      .waitForElementVisible(mainPage.elements.snackBarMessage);

    browser.assert.containsText(
      mainPage.elements.snackBarMessage,
      `${createdBackupFileName}`,
    );

    mainPage
      .closeSnackbar()
      .waitForElementNotPresent(backupFileAlreadyExistsDialog);

    // Remove created backup from local filesystem
    browser.perform(() =>
      fs.unlinkSync(`${testDataDir}/${createdBackupFileName}`),
    );

    // Open the delete backup dialog but click cancel
    browser.waitForElementVisible(
      `//div[@data-test='backup-restore-view']//table//tr//td[text() = "${createdBackupFileName}"]`,
    );
    backupAndRestoreTab
      .clickDeleteForBackup(createdBackupFileName)
      .waitForElementVisible(deleteBackupConfirmationDialog);
    deleteBackupConfirmationDialog
      .cancel()
      .waitForElementNotPresent(deleteBackupConfirmationDialog);

    backupAndRestore.deleteBackup(browser, createdBackupFileName);
  },
  'Restore backup': async (browser) => {
    const createdBackupFileName = await backupAndRestore.createBackup(browser);

    // Click restore for created backup and close the dialog
    backupAndRestoreTab
      .clickRestoreForBackup(createdBackupFileName)
      .waitForElementVisible(restoreConfirmationDialog);
    restoreConfirmationDialog
      .cancel()
      .waitForElementNotPresent(restoreConfirmationDialog);

    // Not doing actual restore
    // backupAndRestoreTab.clickRestoreForBackup(createdBackupFileName);
    // browser.waitForElementVisible(restoreConfirmationDialog);
    // restoreConfirmationDialog.confirm();
    // browser.waitForElementNotVisible(restoreConfirmationDialog);

    backupAndRestore.deleteBackup(browser, createdBackupFileName);
  },
};
