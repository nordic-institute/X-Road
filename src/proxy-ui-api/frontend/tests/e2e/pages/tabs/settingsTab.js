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

const systemParametersCommands = {};

const backupAndRestoreCommands = {
  enterFilterInput: function (input) {
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
      `//table[contains(@class, "xrd-table")]/tbody/tr/td[text() = "${backupFilename}"]/..//button[contains(@data-test, "backup-download")]`,
    );

    return this;
  },
  clickRestoreForBackup: function (backupFilename) {
    this.click(
      'xpath',
      `//table[contains(@class, "xrd-table")]/tbody/tr/td[text() = "${backupFilename}"]/..//button[contains(@data-test, "backup-restore")]`,
    );
    return this;
  },
  clickDeleteForBackup: function (backupFilename) {
    this.click(
      'xpath',
      `//table[contains(@class, "xrd-table")]/tbody/tr/td[text() = "${backupFilename}"]/..//button[contains(@data-test, "backup-delete")]`,
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
    '//div[.//a[contains(@class, "v-tab--active") and contains(@data-test, "settings")]]//div[contains(@class, "base-full-width")]',
  locateStrategy: 'xpath',
  commands: settingsTabCommands,
  elements: {
    systemParametersTab: {
      selector:
        '//div[contains(@class, "v-tabs-bar__content")]//a[@data-test="system"]',
      locateStrategy: 'xpath',
    },
    backupAndRestoreTab: {
      selector:
        '//div[contains(@class, "v-tabs-bar__content")]//a[@data-test="backup"]',
      locateStrategy: 'xpath',
    },
  },
  sections: {
    systemParametersTab: {
      selector:
        '//div[contains(@class, "v-tabs-bar__content")]//a[@data-test="system"]',
      locateStrategy: 'xpath',
      commands: systemParametersCommands,
    },
    backupAndRestoreTab: {
      selector:
        '//div[contains(@class, "v-tabs-bar__content")]//a[@data-test="backup"]',
      locateStrategy: 'xpath',
      commands: backupAndRestoreCommands,
      elements: {
        anchorDownloadButton: {
          selector:
            '//*[contains(@data-test, "system-parameters-configuration-anchor-download-button")]',
          locateStrategy: 'xpath',
        },
        backupButton: {
          selector: '//*[contains(@data-test, "backup-create-configuration")]',
          locateStrategy: 'xpath',
        },
        searchField: {
          selector: '//*[contains(@data-test, "backup-search")]',
          locateStrategy: 'xpath',
        },
      },
      sections: {
        deleteBackupConfirmationDialog: {
          selector:
            '//div[@data-test="dialog-simple" and .//div[@data-test="dialog-content-text"]]',
          locateStrategy: 'xpath',
          commands: confirmationDialog,
          elements: {
            confirmation: {
              selector:
                '//button[@data-test, "dialog-save-button"]',
              locateStrategy: 'xpath',
            },
            cancel: {
              selector:
                '//button[@data-test, "dialog-cancel-button"]',
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
              selector:
                '//button[@data-test, "dialog-save-button"]',
              locateStrategy: 'xpath',
            },
            cancel: {
              selector:
                '//button[@data-test="dialog-cancel-button"]',
              locateStrategy: 'xpath',
            },
          },
        },
      },
    },
  },
};

module.exports = settingsTab;
