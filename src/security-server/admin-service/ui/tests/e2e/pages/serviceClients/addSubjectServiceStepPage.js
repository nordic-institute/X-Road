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
    setFilter(text) {
      this.clearValue2('@filterField');
      this.setValue('@filterField', text);
      return this;
    },
    cancel() {
      this.waitForElementVisible('@cancelButton');
      this.click('@cancelButton');
      return this;
    },
    assertAddSelectedButtonDisabled() {
      this.assert.cssClassPresent('@addSelectedButton', 'v-btn--disabled');
      this.assert.attributeEquals('@addSelectedButton', 'disabled', 'true');
      return this;
    },
    assertAddSelectedButtonEnabled() {
      this.assert.not.cssClassPresent('@addSelectedButton', 'v-btn--disabled');
      this.assert.attributeEquals('@addSelectedButton', 'disabled', null);
      return this;
    },
    assertWizardSecondPage() {
      this.waitForElementVisible('@wizardStepIndicator');
      return this;
    },
    clickPreviousButton() {
      this.waitForElementVisible('@previousButton');
      this.click('@previousButton');
      return this;
    },
    clickAddSelectedButton() {
      this.waitForElementVisible('@addSelectedButton');
      this.click('@addSelectedButton');
      return this;
    },
    verifyServiceListRow: function (service) {
      this.waitForElementVisible(
        `(//table[@class="xrd-table"]/tbody/tr)//td[contains(text(), "${service}")]`,
      );
      return this;
    },
    verifyVisibleService: function (service) {
      this.waitForElementVisible(
        `//table[@class="xrd-table"]//td[contains(text(), "${service}")]`,
      );
      return this;
    },
    verifyNotPresentService: function (service) {
      this.api.waitForElementNotPresent(
        `//table[@class="xrd-table"]//td[contains(text(), "${service}")]`,
      );
      return this;
    },
    selectService(service) {
      const serviceNode = `//table[@class="xrd-table"]//td[contains(text(), "${service}")]/../td[@class="selection-checkbox"]//div[contains(@class, "v-input--selection-controls__ripple")]`;
      this.waitForElementVisible(serviceNode);
      this.click(serviceNode);
      return this;
    },
    assertSelectedServicesCount(count) {
      this.expect.elements('@selectedServices').count.to.equal(count);
      return this;
    },
  },
];

module.exports = {
  url: (subsystemId) =>
    `${process.env.VUE_DEV_SERVER_URL}/#/subsystem/serviceclients/${subsystemId}/add`,
  commands: commands,
  elements: {
    wizardStepIndicator:
        '//div[contains(@class, "v-stepper__step--active")]/span[contains(@class, "primary") and contains(text(), "2")]',
    filterField: '//input[@data-test="search-service-client-service"]',
    cancelButton:
        '//div[contains(@class, "button-footer") and .//button[@data-test="previous-button"]]//button[@data-test="cancel-button"]',
    addSelectedButton:
        '//div[contains(@class, "button-footer") and .//button[@data-test="previous-button"]]//button[@data-test="finish-button"]',
    previousButton: '//button[@data-test="previous-button"]',
    selectedServices:
        '//td[@class="selection-checkbox"]//div[contains(@class, "v-input--is-label-active")]',
  },
};
