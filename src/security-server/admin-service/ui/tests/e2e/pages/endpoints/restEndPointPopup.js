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

const restEndPointPopupCommands = {
  // previous value must be empty
  initPath: function (path) {
    this.assert.valueContains('@requestPath', '');
    this.setValue('@requestPath', path);
    return this;
  },
  // previous value must be non-empty
  modifyPath: function (path) {
    this.waitForNonEmpty('@requestPath');
    this.clearValue2('@requestPath');
    this.setValue('@requestPath', path);
    return this;
  },
  selectRequestMethod: function (method) {
    this.click('@methodDropdown');

    this.api.pause(1000);
    // The picker menu is attached to the main app dom tree, not the dialog
    this.api.click(
      '//div[@role="listbox"]//div[@role="option" and contains(./descendant-or-self::*/text(),"' +
        method +
        '")]',
    );

    return this;
  },
  clickMethodMenu: function () {
    this.click('@methodDropdown');
    return this;
  },
  addSelected: function () {
    this.click('@addButton');
    return this;
  },
  cancel: function () {
    this.click('@cancelButton');
    return this;
  },
  deleteEndpoint: function () {
    this.click('@deleteButton');
    return this;
  },
  verifyMethodExists: function (method) {
    this.api.waitForElementVisible(
      '//div[@role="listbox"]//div[@role="option" and contains(./descendant-or-self::*/text(),"' +
        method +
        '")]',
    );
    return this;
  },
  confirmDelete: function () {
    this.click('@deleteYesButton');
    return this;
  },
  cancelDelete: function () {
    this.click('@deleteCancelButton');
    return this;
  },
};

const addRestEndPointPopupElements = {
  addButton: '//button[@data-test="dialog-save-button"]',
  cancelButton: '//button[@data-test="dialog-cancel-button"]',
  requestPath: '//input[@data-test="endpoint-path"]',
  requestPathMessage:
    '//div[contains(@class, "dlg-edit-row") and .//*[@data-test="endpoint-path"]]//*[contains(@class, "v-messages__message")]',
  methodDropdown: '//input[@data-test="endpoint-method"]/parent::*',
};

const editRestEndPointPopupElements = {
  addButton: '//button[.//*[contains(text(), "Save")]]',
  cancelButton: '//button[.//*[contains(text(), "Cancel")]]',
  deleteButton: '//button[.//*[contains(text(), "Delete")]]',
  requestPath: '//input[@data-test="endpoint-path"]',
  requestPathMessage:
    '//div[contains(@class, "dlg-edit-row") and .//*[@data-test="endpoint-path"]]//*[contains(@class, "v-messages__message")]',
  methodDropdown: '//input[@data-test="endpoint-method"]/parent::*',
  deleteYesButton: '//button[@data-test="dialog-save-button"]',
  deleteCancelButton: '//button[@data-test="dialog-cancel-button"]',
};
module.exports = {
  restEndPointPopupCommands,
  addRestEndPointPopupElements,
  editRestEndPointPopupElements,
};
