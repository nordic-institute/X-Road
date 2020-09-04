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

const commands = [{
  openAddSubjectsDialog: function() {
    this.click('@addSubjectsButton');
    return this;
  },
  close: function() {
    this.click('@closeButton');
    return this;
  },
  verifyAccessRightsPage: function(path) {
    this.api.waitForElementVisible(`//div[contains(@class, "group-members-row")]//div[contains(@class, "row-title") and contains(text(), "Access Rights")]`);
    this.api.waitForElementVisible(`//div[contains(@class, "cert-dialog-header")]//span[contains(@class, "cert-headline") and contains(text(), ${path})]`);
    return this;
  },
}];

var addSubjectCommands = {
  startSearch: function() {
    this.click('@searchButton');
    return this;
  },
  selectServiceClientType: function(type) {
    this.click('@serviceClientTypeDropdown');
    // The picker menu is attached to the main app dom tree, not the dialog
    this.api.click('//div[@role="listbox"]//div[contains(@class,"v-list-item__title") and contains(text(),"'+type+'")]');
    return this;
  },
  addSelected: function() {
    this.click('@addButton');
    return this;
  },
  cancel: function() {
    this.click('@cancelButton');
    return this;
  },
  selectSubject: function(subject) {
    this.api.click(this.selector + '//tr[.//td[contains(text(),"'+subject+'")]]//input[contains(@data-test, "sc-checkbox")]/following-sibling::div');
    return this;
  },
  verifyClientTypeVisible: function(type) {
    this.api.waitForElementVisible('//table[contains(@class, "members-table")]//td[contains(text(), "'+type+'")]');
    return this;
  },
  verifyClientTypeNotPresent: function(type) {
    this.api.waitForElementNotPresent('//table[contains(@class, "members-table")]//td[contains(text(), "'+type+'")]');
    return this;
  }
};

module.exports = {
  url: (clientId, serviceId, endpointId) => `${process.env.VUE_DEV_SERVER_URL}/#/service/${clientId}/${serviceId}/endpoints/${endpointId}/accessrights`,
  commands: commands,
  elements: {
    removeAllButton: {
      selector: '//button[@data-test="remove-all-access-rights"]',
      locateStrategy: 'xpath' },
    addSubjectsButton: {
      selector: '//button[@data-test="add-subjects-dialog"]',
      locateStrategy: 'xpath' },
    closeButton: {
      selector: '//*[contains(@class, "cert-dialog-header")]//*[@id="close-x"]',
      locateStrategy: 'xpath' },
    pageHeader: {
      selector: '//div[contains(@class, "group-members-row)]//div[contains(@class, "row-title")]/text()',
      locateStrategy: 'xpath' },
  },
  sections: {
   addSubjectsPopup: {
      selector: '//div[contains(@class, "xrd-card") and .//span[contains(@class, "headline") and contains(text(),"Add Subjects")]]',
      locateStrategy: 'xpath',
      commands: [addSubjectCommands],
      elements: {
        searchButton: {
          selector: '//button[@data-test="search-button"]',
          locateStrategy: 'xpath' },
        addButton: {
          selector: '//button[@data-test="save"]',
          locateStrategy: 'xpath' },
        cancelButton: {
          selector: '//button[@data-test="cancel-button"]',
          locateStrategy: 'xpath' },
        serviceClientTypeDropdown: {
          selector: '//input[@data-test="serviceClientType"]/parent::*',
          locateStrategy: 'xpath' },
        timeoutApplyToAllCheckbox: {
          selector: '//input[@data-test="timeout-all"]/following-sibling::div',
          locateStrategy: 'xpath' }
      }
    },
  }
};
