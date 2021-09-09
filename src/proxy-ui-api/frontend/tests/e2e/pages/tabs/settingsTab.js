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
var assert = require('assert');

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

  assertTimestampingTableContents: function (expectedName, expectedUrl) {
    // Service table row is visible
    this.waitForElementVisible('@timestampingServiceTableRow');
    // Delete button is visible in row
    this.waitForElementVisible('@timestampingDeleteButton');
    this.getText('@timestampingTableFirstCell', function (foundName) {
      assert.equal(foundName.value, expectedName);
    });
    this.getText('@timestampingTableSecondCell', function (foundUrl) {
      console.log(foundUrl.value);
      assert.equal(foundUrl.value, expectedUrl);
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
      `//table[contains(@class, "xrd-table")]/tbody/tr/td[text() = "${backupFilename}"]/..//button[@data-test="backup-download"]`,
    );
    return this;
  },

  clickRestoreForBackup: function (backupFilename) {
    this.click(
      'xpath',
      `//table[contains(@class, "xrd-table")]/tbody/tr/td[text() = "${backupFilename}"]/..//button[@data-test="backup-restore"]`,
    );
    return this;
  },
  clickDeleteForBackup: function (backupFilename) {
    this.click(
      'xpath',
      `//table[contains(@class, "xrd-table")]/tbody/tr/td[text() = "${backupFilename}"]/..//button[@data-test="backup-delete"]`,
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
  selector:
    '//div[.//a[contains(@class, "v-tab--active") and @data-test="settings"]]//div[contains(@class, "base-full-width")]',
  locateStrategy: 'xpath',
  commands: settingsTabCommands,
  elements: {
    systemParametersTab: {
      selector:
        '//div[contains(@class, "v-tabs-bar__content")]//a[contains(@class, "v-tab") and contains(text(), "System Parameters")]',
      locateStrategy: 'xpath',
    },
    backupAndRestoreTab: {
      selector:
        '//div[contains(@class, "v-tabs-bar__content")]//a[contains(@class, "v-tab") and contains(text(), "Backup And Restore")]',
      locateStrategy: 'xpath',
    },
  },
  sections: {
    systemParametersTab: {
      selector:
        '//div[.//a[contains(@class, "v-tab--active") and @data-test="settings"]]//div[contains(@class, "base-full-width")]',
      locateStrategy: 'xpath',
      commands: systemParametersTabCommands,
      elements: {
        timestampingServiceTableRow: {
          selector:
            '//tr[@data-test="system.parameters-timestamping-service-row"]',
          locateStrategy: 'xpath',
        },
        timestampingDeleteButton: {
          selector:
            '//button[@data-test="system-parameters-timestamping-service-delete-button"]',
          locateStrategy: 'xpath',
        },
        timestampingAddButton: {
          selector:
            '//button[@data-test="system-parameters-timestamping-services-add-button"]',
          locateStrategy: 'xpath',
        },
        timestampingAddButtonDisabled: {
          selector:
            '//button[@data-test="system-parameters-timestamping-services-add-button" and @disabled="disabled"]',
          locateStrategy: 'xpath',
        },
        timestampingDeleteDialog: {
          selector: '//div[@data-test="dialog-simple"]',
          locateStrategy: 'xpath',
        },
        timestampingDeleteDialogCancelButton: {
          selector:
            '//button[@data-test="dialog-cancel-button"]',
          locateStrategy: 'xpath',
        },
        timestampingDeleteDialogSaveButton: {
          selector: '//button[@data-test="dialog-save-button"]',
          locateStrategy: 'xpath',
        },
        timestampingAddDialogAddButton: {
          selector:
            '//button[@data-test="system-parameters-add-timestamping-service-dialog-add-button"]',
          locateStrategy: 'xpath',
        },
        timestampingAddDialogCancelButton: {
          selector:
            '//button[@data-test="system-parameters-add-timestamping-service-dialog-cancel-button"]',
          locateStrategy: 'xpath',
        },
        timestampingAddDialogServiceSelection: {
          selector:
            '//input[@value="X-Road Test TSA CN"]/../../label',
          locateStrategy: 'xpath',
        },
        timestampingTableFirstCell: {
          selector:
            '//tr[@data-test="system.parameters-timestamping-service-row"]/td[1]',
          locateStrategy: 'xpath',
        },
        timestampingTableSecondCell: {
          selector:
            '//tr[@data-test="system.parameters-timestamping-service-row"]/td[2]',
          locateStrategy: 'xpath',
        },
      },
    },
    backupAndRestoreTab: {
      selector:
        '//div[contains(@class, "v-tabs-bar__content")]//a[contains(@class, "v-tab") and contains(text(), "Backup And Restore")]',
      locateStrategy: 'xpath',
      commands: backupAndRestoreCommands,
      elements: {
        anchorDownloadButton: {
          selector:
            '//*[@data-test="system-parameters-configuration-anchor-download-button"]',
          locateStrategy: 'xpath',
        },
        backupButton: {
          selector: '//*[@data-test="backup-create-configuration"]',
          locateStrategy: 'xpath',
        },
        searchButton: {
          selector: '//button[contains(@class, "mdi-magnify")]',
          locateStrategy: 'xpath',
        },
        searchField: {
          selector: '//input[@data-test="search-input"]',
          locateStrategy: 'xpath',
        },
      },
      sections: {
        deleteBackupConfirmationDialog: {
          selector:
            '//div[@data-test="dialog-simple" and .//div[@data-test="dialog-content-text" and contains(text(), "Are you sure you want to delete")]]',
          locateStrategy: 'xpath',
          commands: confirmationDialog,
          elements: {
            confirmation: {
              selector:
                '//div[@data-test="dialog-simple" and .//div[@data-test="dialog-content-text" and contains(text(), "Are you sure you want to delete")]]//button[@data-test="dialog-save-button"]',
              locateStrategy: 'xpath',
            },
            cancel: {
              selector:
                '//div[@data-test="dialog-simple" and .//div[@data-test="dialog-content-text" and contains(text(), "Are you sure you want to delete")]]//button[@data-test="dialog-cancel-button"]',
              locateStrategy: 'xpath',
            },
          },
        },
        backupFileAlreadyExistsDialog: {
          selector:
            '//div[@data-test="dialog-simple" and .//div[@data-test="dialog-content-text"]]',
          locateStrategy: 'xpath',
          commands: confirmationDialog,
          elements: {
            confirmation: {
              selector: '//button[@data-test="dialog-save-button"]',
              locateStrategy: 'xpath',
            },
            cancel: {
              selector: '//button[@data-test="dialog-cancel-button"]',
              locateStrategy: 'xpath',
            },
          },
        },
        restoreConfirmationDialog: {
          selector:
            '//div[@data-test="dialog-simple" and .//div[@data-test="dialog-content-text"]]',
          locateStrategy: 'xpath',
          commands: confirmationDialog,
          elements: {
            confirmation: {
              selector: '//button[@data-test, "dialog-save-button"]',
              locateStrategy: 'xpath',
            },
            cancel: {
              selector: '//button[@data-test="dialog-cancel-button"]',
              locateStrategy: 'xpath',
            },
          },
        },
      },
    },
  },
};

module.exports = settingsTab;
