/*
 * The MIT License
 *
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

const { navigateAndMakeTestable } = require('./csCommonObjectsPage');
const globalResourceCommands = {
  globalGroupsResourceIsVisible() {
    this.assert.visible('@globalResourcesView');
    return this;
  },
  verifyGroupListRow: function (row, code) {
    this.api.waitForElementVisible(
      `(//*[contains(@data-test, "global-groups-table")]//tr)[${row}]//*[contains(text(),"${code}")]`,
    );
    return this;
  },
  openAddGlobalGroupDialog: function () {
    this.click('@addGlobalGroupButton');
    return this;
  },
  initCode: function (code) {
    this.setValue('@globalGroupCode', code);
    return this;
  },
  initDescription: function (description) {
    this.setValue('@globalGroupDescription', description);
    return this;
  },
  confirmAddDialog: function () {
    this.click('@confirmAddButton');
    return this;
  },
  navigateAndMakeTestable,
};

module.exports = {
  url: `${process.env.VUE_DEV_SERVER_URL}/#/settings/global-resources`,
  commands: [globalResourceCommands],
  elements: {
    globalResourcesView: {
      selector: '//div[@data-test="global-resources-view"]',
      locateStrategy: 'xpath',
    },
    addGlobalGroupButton: {
      selector: '//button[@data-test="add-global-group-button"]',
      locateStrategy: 'xpath',
    },
    globalGroupCode: {
      selector: '//input[@data-test="add-global-group-code-input"]',
      locateStrategy: 'xpath',
    },
    globalGroupDescription: {
      selector: '//input[@data-test="add-global-group-description-input"]',
      locateStrategy: 'xpath',
    },
    confirmAddButton: {
      selector: '//button[@data-test="dialog-save-button"]',
      locateStrategy: 'xpath',
    },
  },
};
