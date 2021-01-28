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
        '//table[.//th[contains(text(), "Access Rights Given")]]//tr[.//td[contains(text(), "' +
        service +
        '")]]//button[@data-test="access-right-remove"]',
    );
    return this;
  },
  verifyAccessRightVisible: function (service) {
    this.api.waitForElementVisible(`//table[.//th[contains(text(), "Access Rights Given")]]//td[contains(text(),"${service}")]`);
    return this;
  },
  verifyAccessRightNotPresent: function (service) {
    this.api.waitForElementNotPresent(`//table[.//th[contains(text(), "Access Rights Given")]]//td[contains(text(),"${service}")]`);
    return this;
  },
  close: function () {
      this.click('@closeButton');
      return this;
  },
}
];


var addServiceCommands = 
  {
    setSearch: function (text) {
      this.clearValue2('@searchField');
      this.setValue('@searchField', text);
      return this;
    },
    selectService: function (service) {
      this.api.click('//tr[@data-test="access-right-toggle" and .//td[contains(text(), "'+service+'")]]//div[contains(@class, "v-input--selection-controls__ripple")]');
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
    wizardStepIndicator: {
      selector: '//span[contains(@class, "primary") and contains(text(), "1")]',
      locateStrategy: 'xpath',
    },
    addServiceButton: {
      selector: '//button[@data-test="add-subjects-dialog"]',
      locateStrategy: 'xpath',
    },
    removeAllButton: {
      selector: '//button[@data-test="remove-all-access-rights"]',
      locateStrategy: 'xpath',
    },
    closeButton: {
      selector: '//button[@data-test="close"]',
      locateStrategy: 'xpath',
    },
  },
  sections: {
    addServicesPopup: {
      selector: '//div[contains(@class, "v-dialog--active") and .//span[@data-test="dialog-title" and contains(text(), "Add service")]]',
      locateStrategy: 'xpath',
      commands: [addServiceCommands],
      elements: {
        searchField: {
          selector:
            '//input[@data-test="search-service-client"]',
          locateStrategy: 'xpath',
        },
        addButton: {
          selector:
            '//button[@data-test="dialog-save-button"]',
          locateStrategy: 'xpath',
        },
        cancelButton: {
          selector:
            '//button[@data-test="dialog-cancel-button"]',
          locateStrategy: 'xpath',
        },
        closeButton: {
          selector:
            '//div[contains(@class, "v-card__title")]//i[@id="dlg-close-x"]',
          locateStrategy: 'xpath',
        },
      }
    }
  }
};
