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

/**
 * On backup & restore page delete backup with the given name and make sure it is removed
 *
 * @param browser
 * @param backupFilename
 */
const deleteBackup = function (browser, backupFilename) {
  const mainPage = browser.page.ssMainPage();
  const backupAndRestoreTab =
    mainPage.section.settingsTab.section.backupAndRestoreTab;
  const deleteBackupConfirmationDialog =
    backupAndRestoreTab.section.deleteBackupConfirmationDialog;

  browser.waitForElementVisible(
    `//table[contains(@class, "xrd-table")]//tr//td[text() = "${backupFilename}"]`,
  );
  backupAndRestoreTab.clickDeleteForBackup(backupFilename);
  browser.waitForElementVisible(deleteBackupConfirmationDialog);
  deleteBackupConfirmationDialog.confirm();
  browser.waitForElementVisible(mainPage.elements.snackBarMessage);

  // Make sure backup was successfully deleted
  browser.assert.containsText(
    mainPage.elements.snackBarMessage,
    `${backupFilename}`,
  );
  mainPage.closeSnackbar();
};

/**
 * Navigate to backup and restore page when browser is started
 *
 * @param browser
 */
const navigateToBackupAndRestorePage = function (browser) {
  const frontPage = browser.page.ssFrontPage();
  const mainPage = browser.page.ssMainPage();
  const settingsTab = mainPage.section.settingsTab;
  const backupButton =
    settingsTab.section.backupAndRestoreTab.elements.backupButton;

  // Open SUT and check that page is loaded
  frontPage.navigate();
  browser.waitForElementVisible('//*[@id="app"]');

  // Enter valid credentials
  frontPage.signinDefaultUser();

  // Navigate to settings tab
  mainPage.openSettingsTab();
  browser.waitForElementVisible(settingsTab);
  settingsTab.openBackupAndRestore();
  browser.waitForElementVisible(backupButton);
};

/**
 * Create backup and verify it was created
 *
 * @param browser
 * @return filename of the created backup
 */
const createBackup = async (browser) => {
  const mainPage = browser.page.ssMainPage();
  const settingsTab = mainPage.section.settingsTab;
  const backupAndRestoreTab = settingsTab.section.backupAndRestoreTab;

  // Create backup
  backupAndRestoreTab.clickCreateBackup();
  browser.waitForElementVisible(mainPage.elements.snackBarMessage);

  // Get the backend-generated name of the backup and close snackbar
  const createdBackupFileNameTextObject = await browser.getText(
    'xpath',
    mainPage.elements.snackBarMessage,
  );
  const createdBackupFileName = createdBackupFileNameTextObject.value.split(
    ' ',
  )[1];
  console.log('Created backup: ', createdBackupFileName);
  mainPage.closeSnackbar();

  return createdBackupFileName;
};

module.exports = {
  tags: ['ss', 'backupandrestore'],
  'Security server backups can be created, listed, filtered and removed': async (
    browser,
  ) => {
    const mainPage = browser.page.ssMainPage();
    const settingsTab = mainPage.section.settingsTab;
    const backupAndRestoreTab = settingsTab.section.backupAndRestoreTab;
    const deleteBackupConfirmationDialog =
      backupAndRestoreTab.section.deleteBackupConfirmationDialog;

    navigateToBackupAndRestorePage(browser);

    const createdBackupFileName = await createBackup(browser);

    // Filtering backup list with the name of created backup there should be only one backup in the list
    backupAndRestoreTab.enterFilterInput(createdBackupFileName);
    browser.expect
      .elements('//table[contains(@class, "xrd-table")]/tbody/tr')
      .count.to.equal(1);
    backupAndRestoreTab.clearFilterInput();

    // Delete created backup (click cancel first time)
    browser.waitForElementVisible(
      `//table[contains(@class, "xrd-table")]//td[text() = "${createdBackupFileName}"]/following-sibling::td//button[contains(@data-test, "backup-delete")]`,
    );
    backupAndRestoreTab.clickDeleteForBackup(createdBackupFileName);
    browser.waitForElementVisible(deleteBackupConfirmationDialog);
    deleteBackupConfirmationDialog.cancel();
    browser.waitForElementNotVisible(deleteBackupConfirmationDialog);

    deleteBackup(browser, createdBackupFileName);
    browser.end();
  },

  'Download and import backup': async (browser) => {
    const mainPage = browser.page.ssMainPage();
    const settingsTab = mainPage.section.settingsTab;
    const backupAndRestoreTab = settingsTab.section.backupAndRestoreTab;
    const backupFileAlreadyExistsDialog =
      backupAndRestoreTab.section.backupFileAlreadyExistsDialog;
    const deleteBackupConfirmationDialog =
      backupAndRestoreTab.section.deleteBackupConfirmationDialog;

    // delete existing backups from test dir
    const testDataDir = __dirname + browser.globals.e2etest_testdata + '/';
    const regex = /^conf_backup/;

    fs.readdirSync(testDataDir)
      .filter((file) => regex.test(file))
      .map((file) => fs.unlinkSync(testDataDir + file));

    // Navigate to backup and restore page
    navigateToBackupAndRestorePage(browser);

    const createdBackupFileName = await createBackup(browser);

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
    backupAndRestoreTab.addBackupToInput(testDataDir + createdBackupFileName);
    browser.waitForElementVisible(backupFileAlreadyExistsDialog);
    backupFileAlreadyExistsDialog.cancel();
    browser.waitForElementNotVisible(backupFileAlreadyExistsDialog);

    backupAndRestoreTab.addBackupToInput(testDataDir + createdBackupFileName);
    browser.waitForElementVisible(backupFileAlreadyExistsDialog);
    backupFileAlreadyExistsDialog.confirm();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage);

    browser.assert.containsText(
      mainPage.elements.snackBarMessage,
      `${createdBackupFileName}`,
    );
    mainPage.closeSnackbar();
    browser.waitForElementNotVisible(backupFileAlreadyExistsDialog);

    // Remove created backup from local filesystem
    browser.perform(() =>
      fs.unlinkSync(`${testDataDir}/${createdBackupFileName}`),
    );

    // Open the delete backup dialog but click cancel
    browser.waitForElementVisible(
      `//table[contains(@class, "xrd-table")]//tr//td[text() = "${createdBackupFileName}"]`,
    );
    backupAndRestoreTab.clickDeleteForBackup(createdBackupFileName);
    browser.waitForElementVisible(deleteBackupConfirmationDialog);
    deleteBackupConfirmationDialog.cancel();
    browser.waitForElementNotVisible(deleteBackupConfirmationDialog);

    deleteBackup(browser, createdBackupFileName);
    browser.end();
  },
  'Restore backup': async (browser) => {
    const mainPage = browser.page.ssMainPage();
    const settingsTab = mainPage.section.settingsTab;
    const backupAndRestoreTab = settingsTab.section.backupAndRestoreTab;
    const restoreConfirmationDialog =
      backupAndRestoreTab.section.restoreConfirmationDialog;

    // Navigate to backup and restore page
    navigateToBackupAndRestorePage(browser);

    const createdBackupFileName = await createBackup(browser);

    // Click restore for created backup and close the dialog
    backupAndRestoreTab.clickRestoreForBackup(createdBackupFileName);
    browser.waitForElementVisible(restoreConfirmationDialog);
    restoreConfirmationDialog.cancel();
    browser.waitForElementNotVisible(restoreConfirmationDialog);

    // Not doing actual restore
    // backupAndRestoreTab.clickRestoreForBackup(createdBackupFileName);
    // browser.waitForElementVisible(restoreConfirmationDialog);
    // restoreConfirmationDialog.confirm();
    // browser.waitForElementNotVisible(restoreConfirmationDialog);

    deleteBackup(browser, createdBackupFileName);
    browser.end();
  },
};
