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

const commands = [
  {
    setFilter: function (text) {
      this.clearValue2('@filterField');
      this.setValue('@filterField', text);
      return this;
    },
    addService: function () {
      this.click('@addServiceButton');
      return this;
    },
    removeAll: function () {
      this.click('@removeAllButton');
      return this;
    },
    removeAccessRight: function (service) {
      this.api.click(
        '//tr[.//td[contains(text(), "' +
          service +
          '")]]//button[@data-test="access-right-remove"]',
      );
      return this;
    },
    verifyAccessRightVisible: function (service) {
      this.api.waitForElementVisible(
        `//table[@data-test="service-client-access-rights-table"]//td[contains(text(),"${service}")]`,
      );
      return this;
    },
    verifyAccessRightNotPresent: function (service) {
      this.api.waitForElementNotPresent(
        `//table[@data-test="service-client-access-rights-table"]//td[contains(text(),"${service}")]`,
      );
      return this;
    },
    close: function () {
      this.click('@closeButton');
      return this;
    },
  },
];

var addServiceCommands = {
  setSearch: function (text) {
    this.clearValue2('@searchField');
    this.setValue('@searchField', text);
    return this;
  },
  selectService: function (service) {
    this.api.click(
      '//tr[@data-test="access-right-toggle" and .//td[contains(text(), "' +
        service +
        '")]]//div[contains(@class, "v-input--selection-controls__ripple")]',
    );
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
  close: function () {
    this.click('@closeButton');
    return this;
  },
};

module.exports = {
  url: (subsystemId) =>
    `${process.env.VUE_DEV_SERVER_URL}/#/subsystem/${subsystemId}/serviceclients/`,
  commands: commands,
  elements: {
    wizardStepIndicator:
      '//span[contains(@class, "primary") and contains(text(), "1")]',
    addServiceButton: '//button[@data-test="add-subjects-dialog"]',
    removeAllButton: '//button[@data-test="remove-all-access-rights"]',
    closeButton: '//button[@data-test="close"]',
  },
  sections: {
    addServicesPopup: {
      selector: '//div[@data-test="dialog-simple"]',
      commands: [addServiceCommands],
      elements: {
        searchField: '//input[@data-test="search-service-client"]',
        addButton: '//button[@data-test="dialog-save-button"]',
        cancelButton: '//button[@data-test="dialog-cancel-button"]',
        closeButton:
          '//*[contains(@class, "cert-dialog-header")]//*[@data-test="close-x"]',
      },
    },
  },
};
