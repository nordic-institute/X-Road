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

const serviceDetailsCommands = {
  closeServiceDetails: function () {
    this.click('@serviceDetailsCloseButton');
    return this;
  },
  deleteService: function () {
    this.click('@deleteServiceButton');
    return this;
  },
  initServiceUrl: function (url) {
    this.assert.value('@serviceURL', '');
    this.setValue('@serviceURL', url);
    return this;
  },
  modifyServiceUrl: function (url) {
    this.waitForNonEmpty('@serviceURL');
    this.clearValue2('@serviceURL');
    this.setValue('@serviceURL', url);
    return this;
  },
  initServiceCode: function (code) {
    this.assert.value('@serviceCode', '');
    this.clearValue2('@serviceCode');
    this.setValue('@serviceCode', code);
    return this;
  },
  modifyServiceCode: function (code) {
    this.waitForNonEmpty('@serviceCode');
    this.clearValue2('@serviceCode');
    this.setValue('@serviceCode', code);
    return this;
  },
  confirmDelete: function () {
    this.click('@confirmDeleteButton');
    return this;
  },
  cancelDelete: function () {
    this.click('@cancelDeleteButton');
    return this;
  },
  confirmDialog: function () {
    this.click('@confirmDialogButton');
    return this;
  },
  cancelDialog: function () {
    this.click('@cancelDialogButton');
    return this;
  },
};

const serviceDetailsElements = {
  serviceDetailsCloseButton:
    '//*[contains(@class, "cert-dialog-header")]//*[contains(@id, "close-x")]',
  deleteServiceButton:
    '//button[@data-test="service-description-details-delete-button"]',
  confirmDeleteButton: '//button[@data-test="dialog-save-button"]',
  cancelDeleteButton: '//button[@data-test="dialog-cancel-button"]',
  serviceURL: '//*[contains(@class, "url-input")]//input',
  serviceCode:
    '//*[contains(@class, "code-input")]//input[@name="code_field"]',
  serviceType: '//div[@data-test="service-description-details-url-type-value"]',
  URLMessage:
    '//*[contains(@class, "validation-provider")]//*[contains(@class, "v-messages__message")]',
  codeMessage:
    '//*[contains(@class, "code-input")]//*[contains(@class, "v-messages__message")]',
  confirmDialogButton:
    '//button[@data-test="service-description-details-save-button"]',
  cancelDialogButton:
    '//button[@data-test="service-description-details-cancel-button"]',
};

module.exports = {
  serviceDetailsElements,
  serviceDetailsCommands,
};
