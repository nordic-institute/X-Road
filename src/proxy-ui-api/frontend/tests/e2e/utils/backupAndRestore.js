
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
  const createdBackupFileName = createdBackupFileNameTextObject.value.split(
    ' ',
  )[1];

  console.log('Created backup: ', createdBackupFileName);
  mainPage.closeSnackbar();
  return createdBackupFileName;
};

module.exports = {
  deleteBackup, createBackup
}


