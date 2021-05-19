
/*
 * This file contains logic for user navigating
 *
 *
 */


const toRestoreAndBackup = (browser) => {
  const mainPage = browser.page.ssMainPage();
  const settingsTab = mainPage.section.settingsTab;

  const backupButton =
    settingsTab.section.backupAndRestoreTab.elements.backupButton;

  mainPage.openSettingsTab();
  browser.waitForElementVisible(settingsTab);
  settingsTab.openBackupAndRestore();
  browser.waitForElementVisible(backupButton);
}

module.exports = {
  toRestoreAndBackup
};
