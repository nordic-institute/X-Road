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

const operationDetailsCommands = {
  openEndpointsTab: function () {
    this.click('@endpointsTab');
    return this;
  },
  saveParameters: function () {
    this.click('@saveButton');
    return this;
  },
  close: function () {
    this.click('@closeButton');
    return this;
  },
  toggleUrlApplyToAll: function () {
    this.click('@urlApplyToAllCheckbox');
    return this;
  },
  toggleTimeoutApplyToAll: function () {
    this.click('@timeoutApplyToAllCheckbox');
    return this;
  },
  toggleVerifyCertApplyToAll: function () {
    this.click('@verifyCertApplyToAllCheckbox');
    return this;
  },
  modifyUrl: function (url) {
    this.waitForNonEmpty('@serviceURL');
    this.clearValue2('@serviceURL');
    this.setValue('@serviceURL', url);
    return this;
  },
  modifyTimeout: function (timeout) {
    this.waitForNonEmpty('@timeout');
    this.clearValue2('@timeout');
    this.setValue('@timeout', timeout);
    return this;
  },
  toggleCertVerification: function () {
    this.click('@sslAuthClickarea');
    return this;
  },
  openAddAccessRights: function () {
    this.click('@addSubjectsButton');
    return this;
  },
  removeAllAccessRights: function () {
    this.click('@removeAllButton');
    return this;
  },
  removeAccessRight: function (subject) {
    this.api.click(
      this.selector +
        '//table[contains(@class, "group-members-table")]//tr[.//td[contains(text(), "' +
        subject +
        '")]]//button[@data-test="remove-subject"]',
    );
    return this;
  },
};

const operationDetailsElements = {
  parametersTab: '//*[@data-test="parameters"]',
  endpointsTab: '//*[@data-test="endpoints"]',
  urlApplyToAllCheckbox: '//input[@data-test="url-all"]/following-sibling::div',
  timeoutApplyToAllCheckbox:
    '//input[@data-test="timeout-all"]/following-sibling::div',
  verifyCertApplyToAllCheckbox:
    '//input[@data-test="ssl-auth-all"]/following-sibling::div',
  serviceURL: '//input[@data-test="service-url"]',
  timeout: '//input[@data-test="service-timeout"]',
  sslAuth: '//input[@data-test="ssl-auth"]',
  sslAuthClickarea: '//input[@data-test="ssl-auth"]/following-sibling::div',
  save2Button:
    '//button[@data-test="save-service-parameters"]/following-sibling::div',
  saveButton: '//button[@data-test="save-service-parameters"]',
  addButton: '//button[@data-test="endpoint-add"]',
  closeButton: '//button[@data-test="close"]',
  urlHelp: '//div[@data-test="service-parameters-service-url-label"]//i',
  timeoutHelp: '//div[@data-test="service-parameters-timeout-label"]//i',
  verifyCertHelp: '//div[@data-test="service-parameters-verify-tls-label"]//i',
  activeTooltip:
    '//div[contains(@class, "v-tooltip__content") and contains(@class,"menuable__content__active")]//span',
  addSubjectsButton: '//button[@data-test="show-add-subjects"]',
  removeAllButton: '//button[@data-test="remove-subjects"]',
};

module.exports = {
  operationDetailsElements,
  operationDetailsCommands,
};
