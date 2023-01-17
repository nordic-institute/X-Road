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
    cancel: function () {
      this.waitForElementVisible('@cancelButton');
      this.click('@cancelButton');
      return this;
    },
    verifySubjectListRow: function (subject) {
      this.api.waitForElementVisible(
        `(//table[contains(@class, "service-clients-table")]/tbody/tr)//td[contains(text(), "${subject}")]`,
      );
      return this;
    },
    verifyVisibleId: function (subject) {
      this.api.waitForElementVisible(
        `//table[contains(@class, "service-clients-table")]//td[contains(text(), "${subject}")]`,
      );
      return this;
    },
    verifyNotPresentId: function (subject) {
      this.api.waitForElementNotPresent(
        `//table[contains(@class, "service-clients-table")]//td[contains(text(), "${subject}")]`,
      );
      return this;
    },
    selectSubject(subject) {
      const subjectNode = `//table[contains(@class, "service-clients-table")]//td[contains(text(), "${subject}")]/../td[contains(@class, "checkbox-column")]//div[contains(@class, "v-input--selection-controls__ripple")]`;
      this.waitForElementVisible(subjectNode);
      this.click(subjectNode);
      return this;
    },
    assertSelectedSubjectsCount(count) {
      this.expect.elements('@selectedSubjects').count.to.equal(count);
      return this;
    },
    assertNextButtonDisabled() {
      this.assert.cssClassPresent('@nextButton', 'v-btn--disabled');
      this.assert.attributeEquals('@nextButton', 'disabled', 'true');
      return this;
    },
    assertNextButtonEnabled() {
      this.assert.not.cssClassPresent('@nextButton', 'v-btn--disabled');
      this.assert.attributeEquals('@nextButton', 'disabled', null);
      return this;
    },
    assertWizardFirstPage() {
      this.waitForElementVisible('@wizardStepIndicator');
      return this;
    },
    verifyDisabledId(subject) {
      this.waitForElementVisible(
        `//table[contains(@class, "service-clients-table")]//td[contains(text(), "${subject}")]/..//div[contains(@class, "v-radio--is-disabled")]`,
      );
      return this;
    },
    clickNext() {
      this.waitForElementVisible('@nextButton');
      this.click('@nextButton');
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
        '//div[contains(@class, "v-stepper__step--active")]/span[contains(@class, "primary") and contains(text(), "1")]',
    filterField: '//input[@data-test="search-service-client"]',
    cancelButton:
        '//div[@class="v-stepper__wrapper" and .//table[contains(@class, "service-clients-table")]]//button[@data-test="cancel-button"]',
    nextButton:
        '//div[@class="v-stepper__wrapper" and .//table[contains(@class, "service-clients-table")]]//button[@data-test="next-button"]',
    addSubjectWizardHeader:'//div[@data-test="add-subject-title"]',
    selectedSubjects:
        '//div[contains(@class, "checkbox-wrap")]/div[contains(@class, "v-radio") and contains(@class, "v-item--active")]',
    selectedSubjectName:
        '//tr[.//div[contains(@class, "v-radio") and contains(@class, "v-item--active")]]/td[3]',
  },
};
