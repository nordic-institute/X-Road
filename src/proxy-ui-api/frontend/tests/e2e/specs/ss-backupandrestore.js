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
const deleteBackup = async function(browser, backupFilename) {
  const mainPage = browser.page.ssMainPage();
  const backupAndRestoreTab = mainPage.section.settingsTab.section.backupAndRestoreTab;
  const deleteBackupConfirmationDialog = backupAndRestoreTab.section.deleteBackupConfirmationDialog;

  backupAndRestoreTab.clickDeleteForBackup(backupFilename);
  browser.waitForElementVisible(deleteBackupConfirmationDialog);
  deleteBackupConfirmationDialog.confirm();
  browser.waitForElementVisible(mainPage.elements.snackBarMessage)

  // Make sure backup was successfully deleted
  browser.assert.containsText(
    mainPage.elements.snackBarMessage,
    `Backup ${backupFilename} deleted`,
  );
  mainPage.closeSnackbar();
};

const navigateToBackupAndRestorePage = async function(browser) {
  const frontPage = browser.page.ssFrontPage();
  const mainPage = browser.page.ssMainPage();
  const settingsTab = mainPage.section.settingsTab;
  const backupButton = settingsTab.section.backupAndRestoreTab.elements.backupButton;

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
}

module.exports = {
  tags: ['ss', 'backupandrestore'],

  'Security server backups can be created, listed, filtered and removed': async (browser) => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const settingsTab = mainPage.section.settingsTab;
    const backupAndRestoreTab = settingsTab.section.backupAndRestoreTab;
    const backupButton = backupAndRestoreTab.elements.backupButton;
    const deleteBackupConfirmationDialog = backupAndRestoreTab.section.deleteBackupConfirmationDialog;

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

    // Create backup
    backupAndRestoreTab.clickCreateBackup();

    browser.waitForElementVisible(mainPage.elements.snackBarMessage);
    // Make sure backup was successfully created
    browser.assert.containsText(
      mainPage.elements.snackBarMessage,
      'successfully created',
    );

    // Get the backend-generated name of the backup and close snackbar
    const createdBackupFileNameTextObject = await browser.getText('xpath', mainPage.elements.snackBarMessage);
    const createdBackupFileName = createdBackupFileNameTextObject.value.split(" ")[1]
    console.log('Created backup: ', createdBackupFileName);
    mainPage.closeSnackbar();

    // Filtering backup list with the name of created backup there should be only one backup in the list
    backupAndRestoreTab.enterFilterInput(createdBackupFileName);
    browser.expect
      // xrd-table has thead but no tbody so use '/tr' to get only the row elements that are direct children of table
      .elements('//table[contains(@class, "xrd-table")]/tr')
      .count.to.equal(1);
    backupAndRestoreTab.clearFilterInput();

    // backupAndRestoreTab.clickDeleteForBackup(createdBackupFileName);
    // browser.waitForElementVisible(deleteBackupConfirmationDialog);
    // deleteBackupConfirmationDialog.cancel();
    // browser.expect.element(deleteBackupConfirmationDialog).to.not.be.visible;

    await deleteBackup(browser, createdBackupFileName);
    browser.end();

  },
  'Download and import backup': async (browser) => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const settingsTab = mainPage.section.settingsTab;
    const backupAndRestoreTab = settingsTab.section.backupAndRestoreTab;
    const backupButton = settingsTab.section.backupAndRestoreTab.elements.backupButton;
    const backupFileAlreadyExistsDialog = backupAndRestoreTab.section.backupFileAlreadyExistsDialog;

    // delete existing backups from test dir
    const testDataDir = __dirname + browser.globals.e2etest_testdata + '/';
    const regex = /^conf_backup/;

    fs.readdirSync(testDataDir)
      .filter((file) => regex.test(file))
      .map((file) => fs.unlinkSync(testDataDir + file));

    // navigate to backup page

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

    // Create backup
    backupAndRestoreTab.clickCreateBackup();

    // Make sure backup was successfully created
    browser.assert.containsText(
      mainPage.elements.snackBarMessage,
      'successfully created',
    );

    // Get the backend-generated name of the backup and close snackbar
    const createdBackupFileNameTextObject = await browser.getText('xpath', mainPage.elements.snackBarMessage);
    const createdBackupFileName = createdBackupFileNameTextObject.value.split(" ")[1];
    console.log('Created backup: ', createdBackupFileName);
    mainPage.closeSnackbar();

    // Download backupfile and make sure it's in the filesystem
    await backupAndRestoreTab.clickDownloadForBackup(createdBackupFileName);
    browser.pause(5000);

    browser.perform( async () =>
      browser.assert.equal(
        fs.existsSync(`${testDataDir}${createdBackupFileName}`),
        true,
        `Backup file should exist in: ${testDataDir}${createdBackupFileName}`,
    ));

    // Import the created backup from local filesystem (first cancel the operation)
    backupAndRestoreTab.addBackupToInput(testDataDir + createdBackupFileName);
    browser.waitForElementVisible(backupFileAlreadyExistsDialog);
    backupFileAlreadyExistsDialog.cancel();
    browser.expect.element(backupFileAlreadyExistsDialog).to.not.be.visible;

    backupAndRestoreTab.addBackupToInput(testDataDir + createdBackupFileName);
    browser.waitForElementVisible(backupFileAlreadyExistsDialog);
    backupFileAlreadyExistsDialog.confirm();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage);
    browser.assert.containsText(
      mainPage.elements.snackBarMessage,
      `Backup ${createdBackupFileName} uploaded successfully`,
    );
    mainPage.closeSnackbar();
    browser.waitForElementNotVisible(backupFileAlreadyExistsDialog);

    // Remove created backup from local filesystem
    browser.perform( () => fs.unlinkSync(`${testDataDir}/${createdBackupFileName}`));

    browser.end();
  },
  'Restore backup': async (browser) => {
    const frontPage = browser.page.ssFrontPage();
    const mainPage = browser.page.ssMainPage();
    const settingsTab = mainPage.section.settingsTab;
    const backupAndRestoreTab = settingsTab.section.backupAndRestoreTab;
    const restoreConfirmationDialog = backupAndRestoreTab.section.restoreConfirmationDialog;
    const backupButton = backupAndRestoreTab.elements.backupButton;
    const deleteBackupConfirmationDialog = backupAndRestoreTab.section.deleteBackupConfirmationDialog;

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

    // Create backup
    backupAndRestoreTab.clickCreateBackup();

    // Make sure backup was successfully created
    browser.assert.containsText(
      mainPage.elements.snackBarMessage,
      'successfully created',
    );

    // Get the backend-generated name of the backup and close snackbar
    const createdBackupFileNameTextObject = await browser.getText('xpath', mainPage.elements.snackBarMessage);
    const createdBackupFileName = createdBackupFileNameTextObject.value.split(" ")[1]

    mainPage.closeSnackbar();
    backupAndRestoreTab.clickRestoreForBackup(createdBackupFileName);
    browser.waitForElementVisible(restoreConfirmationDialog);
    restoreConfirmationDialog.cancel();
    browser.waitForElementNotVisible(restoreConfirmationDialog);

    /*
    backupAndRestoreTab.clickRestoreForBackup(createdBackupFileName);
    browser.waitForElementVisible(restoreConfirmationDialog);
    restoreConfirmationDialog.confirm();
    browser.waitForElementNotVisible(restoreConfirmationDialog);
    backupAndRestoreTab.clickDeleteForBackup(createdBackupFileName);

    browser.waitForElementVisible(deleteBackupConfirmationDialog);
    deleteBackupConfirmationDialog.confirm();
    browser.waitForElementVisible(mainPage.elements.snackBarMessage)

    // Make sure backup was successfully deleted
    browser.assert.containsText(
      mainPage.elements.snackBarMessage,
      `Backup ${createdBackupFileName} deleted`,
    );
    mainPage.closeSnackbar();

     */
    console.log('after delete backup');
    browser.end();
    console.log('END');
  },
}
