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

/**
 * On backup & restore page delete backup with the given name and make sure it is removed
 *
 * @param browser
 * @param backupFilename
 */
const deleteBackup = (browser, backupFilename) => {
  const mainPage = browser.page.ssMainPage();
  const backupAndRestoreTab =
    mainPage.section.settingsTab.section.backupAndRestoreTab;
  const deleteBackupConfirmationDialog =
    backupAndRestoreTab.section.deleteBackupConfirmationDialog;

  browser.waitForElementVisible(
    `//div[@data-test='backup-restore-view']//table//tr//td[text() = "${backupFilename}"]`,
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
 * Create backup and verify it was created
 *
 * @param browser
 * @return filename of the created backup
 */
const createBackup = async (browser) => {
  const mainPage = browser.page.ssMainPage();
  const settingsTab = mainPage.section.settingsTab;
  const backupAndRestoreTab = settingsTab.section.backupAndRestoreTab;

  browser.pause(1000); // pause helps webdriver copy the correct backupfilename

  // Create backup
  backupAndRestoreTab.clickCreateBackup();
  browser.waitForElementVisible(mainPage.elements.snackBarMessage);

  // Get the backend-generated name of the backup and close snackbar
  const createdBackupFileNameTextObject = await browser.getText(
    'xpath',
    mainPage.elements.snackBarMessage,
  );
  const createdBackupFileName =
    createdBackupFileNameTextObject.value.split(' ')[1];

  console.log('Created backup: ', createdBackupFileName);
  mainPage.closeSnackbar();
  return createdBackupFileName;
};

module.exports = {
  deleteBackup,
  createBackup,
};
