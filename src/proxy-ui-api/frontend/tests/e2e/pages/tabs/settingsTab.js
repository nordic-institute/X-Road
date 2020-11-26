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

var settingsTabCommands = {
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

const settingsTab = {
  url: `${process.env.VUE_DEV_SERVER_URL}/settings`,
  selector: '//div[.//a[contains(@class, "v-tab--active") and contains(text(), "Settings")]]//div[contains(@class, "base-full-width")]',
  commands: settingsTabCommands,
  elements: {
    systemParametersTab: {
      selector: '//div[contains(@class, "v-tabs-bar__content")]//a[text()=" System Parameters "]',
      locateStrategy: 'xpath',
    },
    backupAndRestoreTab: {
      selector: '//div[contains(@class, "v-tabs-bar__content")]//a[text()=" Backup And Restore "]',
      locateStrategy: 'xpath',
    },
    anchorDownloadButton: {
      selector: '//*[contains(@data-test, "system-parameters-configuration-anchor-download-button")]',
      locateStrategy: 'xpath',
    },
    backupButton: {
      selector: '//*[contains(@data-test, "backup-create-configuration")]',
      locateStrategy: 'xpath',
    },
  },
};

module.exports = settingsTab;
