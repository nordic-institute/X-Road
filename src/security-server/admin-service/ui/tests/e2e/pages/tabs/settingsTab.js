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

const path = require('path');
const assert = require('assert');

const settingsTabCommands = {
  openSystemParameters: function () {
    this.click('@systemParametersTab');
    return this;
  },
  openBackupAndRestore: function () {
    this.click('@backupAndRestoreTab');
    return this;
  },
  openSecurityServerTLSKey: function () {
    this.click('@securityServerTLSKeyTab');
    return this;
  },
};

const systemParametersTabCommands = {
  openTimeStampingAddDialog: function () {
    this.click('@timestampingAddButton');
    this.waitForElementVisible('@timestampingAddDialogServiceSelection');
    return this;
  },
  openTimeStampingDeleteDialog: function () {
    this.waitForElementVisible('@timestampingDeleteButton');
    this.click('@timestampingDeleteButton');
    this.waitForElementVisible('@timestampingDeleteDialog');
    return this;
  },
  deleteCurrentTimestampingService: function () {
    this.click('@timestampingDeleteButton');
    this.waitForElementVisible('@timestampingDeleteDialogSaveButton');
    this.click('@timestampingDeleteDialogSaveButton');

    this.waitForElementNotPresent('@timestampingDeleteDialog');
    this.waitForElementNotPresent('@timestampingServiceTableRow');
    return this;
  },

  assertTimestampingTableContents: function (expectedName) {
    // Service table row is visible
    this.waitForElementVisible('@timestampingServiceTableRow');
    // Delete button is visible in row
    this.waitForElementVisible('@timestampingDeleteButton');
    this.getText('@timestampingTableFirstCell', function (foundName) {
      assert.equal(foundName.value, expectedName);
    });
    this.getText('@timestampingTableSecondCell', function (foundUrl) {
      assert(foundUrl.value.startsWith('http'));
    });
    return this;
  },
};

const backupAndRestoreCommands = {
  enterFilterInput: function (input) {
    this.click('@searchButton');
    this.clearValue2('@searchField');
    this.setValue('@searchField', input);
    return this;
  },
  clearFilterInput: function () {
    this.clearValue2('@searchField');
    return this;
  },
  clickCreateBackup: function () {
    this.click('@backupButton');
    return this;
  },
  addBackupToInput: function (filepath) {
    this.api.setValue('//input[@type="file"]', path.resolve(filepath));
    return this;
  },
  clickDownloadForBackup: function (backupFilename) {
    this.click(
      'xpath',
      `//div[@data-test='backup-restore-view']//table/tbody/tr/td[text() = "${backupFilename}"]/..//button[@data-test="backup-download"]`,
    );
    return this;
  },

  clickRestoreForBackup: function (backupFilename) {
    this.click(
      'xpath',
      `//div[@data-test='backup-restore-view']//table/tbody/tr/td[text() = "${backupFilename}"]/..//button[@data-test="backup-restore"]`,
    );
    return this;
  },
  clickDeleteForBackup: function (backupFilename) {
    this.click(
      'xpath',
      `//div[@data-test='backup-restore-view']//table/tbody/tr/td[text() = "${backupFilename}"]/..//button[@data-test="backup-delete"]`,
    );
    return this;
  },
};

const confirmationDialog = {
  confirm: function () {
    this.click('@confirmation');
    return this;
  },
  cancel: function () {
    this.click('@cancel');
    return this;
  },
};

const settingsTab = {
  url: `${process.env.VUE_DEV_SERVER_URL}/settings`,
  selector: '//div[@data-test="settings-tab-view"]',
  commands: settingsTabCommands,
  elements: {
    systemParametersTab: '//a[@data-test="system-parameters-tab-button"]',
    backupAndRestoreTab: '//a[@data-test="backupandrestore-tab-button"]',
  },
  sections: {
    systemParametersTab: {
      selector: '//div[@data-test="system-parameters-tab-view"]',
      commands: systemParametersTabCommands,
      elements: {
        timestampingServiceTableRow: '//tr[@data-test="system.parameters-timestamping-service-row"]',
        timestampingDeleteButton:
            '//button[@data-test="system-parameters-timestamping-service-delete-button"]',
        timestampingAddButton:
            '//button[@data-test="system-parameters-timestamping-services-add-button"]',
        timestampingAddButtonDisabled:
            '//button[@data-test="system-parameters-timestamping-services-add-button" and @disabled="disabled"]',
        timestampingDeleteDialog:  '//div[@data-test="dialog-simple"]',
        timestampingDeleteDialogCancelButton:
          '//button[@data-test="dialog-cancel-button"]',
        timestampingDeleteDialogSaveButton: '//button[@data-test="dialog-save-button"]',
        timestampingAddDialogAddButton: '//button[@data-test="system-parameters-add-timestamping-service-dialog-add-button"]',
        timestampingAddDialogCancelButton:
            '//button[@data-test="system-parameters-add-timestamping-service-dialog-cancel-button"]',
        timestampingAddDialogServiceSelection: '//input[@value="X-Road Test TSA CN"]/../../label',
        timestampingTableFirstCell:
            '//tr[@data-test="system.parameters-timestamping-service-row"]/td[1]',
        timestampingTableSecondCell:
            '//tr[@data-test="system.parameters-timestamping-service-row"]/td[2]',
      },
    },
    backupAndRestoreTab: {
      selector:
        '//div[contains(@class, "v-tabs-bar__content")]//a[contains(@class, "v-tab") and contains(text(), "Backup And Restore")]',
      commands: backupAndRestoreCommands,
      elements: {
        anchorDownloadButton:
            '//*[@data-test="system-parameters-configuration-anchor-download-button"]',
        backupButton: '//*[@data-test="backup-create-configuration"]',
        searchButton: '//button[contains(@class, "mdi-magnify")]',
        searchField: '//input[@data-test="search-input"]',
      },
      sections: {
        deleteBackupConfirmationDialog: {
          selector:
            '//div[@data-test="dialog-simple" and .//div[@data-test="dialog-content-text" and contains(text(), "Are you sure you want to delete")]]',
          commands: confirmationDialog,
          elements: {
            confirmation:
                '//div[@data-test="dialog-simple" and .//div[@data-test="dialog-content-text" and contains(text(), "Are you sure you want to delete")]]//button[@data-test="dialog-save-button"]',
            cancel:
                '//div[@data-test="dialog-simple" and .//div[@data-test="dialog-content-text" and contains(text(), "Are you sure you want to delete")]]//button[@data-test="dialog-cancel-button"]',
          },
        },
        backupFileAlreadyExistsDialog: {
          selector:
            '//div[@data-test="dialog-simple" and .//div[@data-test="dialog-content-text"]]',
          commands: confirmationDialog,
          elements: {
            confirmation:  '//button[@data-test="dialog-save-button"]',
            cancel:  '//button[@data-test="dialog-cancel-button"]',
          },
        },
        restoreConfirmationDialog: {
          selector:
            '//div[@data-test="dialog-simple" and .//div[@data-test="dialog-content-text"]]',
          commands: confirmationDialog,
          elements: {
            confirmation: '//button[@data-test, "dialog-save-button"]',
            cancel: '//button[@data-test="dialog-cancel-button"]',
          },
        },
      },
    },
  },
};

module.exports = settingsTab;
