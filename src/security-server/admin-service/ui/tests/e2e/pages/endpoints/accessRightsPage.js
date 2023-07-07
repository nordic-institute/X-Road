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

const addSubjectsPopup = require('../common/addSubjectsPopup');

const commands = [
  {
    openAddSubjectsDialog: function () {
      this.click('@addSubjectsButton');
      return this;
    },
    close: function () {
      this.click('@closeButton');
      return this;
    },
    verifyAccessRightsPage: function (path) {
      this.api.waitForElementVisible(
        `//div[contains(@class, "group-members-row")]//div[contains(@class, "row-title")]`,
      );
      this.api.waitForElementVisible(
        `//div[contains(@class, "cert-dialog-header")]//span[contains(@class, "identifier-wrap") and contains(text(), ${path})]`,
      );
      return this;
    },
  },
];

module.exports = {
  url: (clientId, serviceId, endpointId) =>
    `${process.env.VUE_DEV_SERVER_URL}/#/service/${clientId}/${serviceId}/endpoints/${endpointId}/accessrights`,
  commands: commands,
  elements: {
    removeAllButton: '//button[@data-test="remove-all-access-rights"]',
    addSubjectsButton:  '//button[@data-test="add-subjects-dialog"]',
    closeButton:
        '//*[contains(@class, "cert-dialog-header")]//*[@data-test="close-x"]',
    pageHeader:
        '//div[contains(@class, "group-members-row)]//div[contains(@class, "row-title")]/text()',
  },
  sections: {
    addSubjectsPopup,
  },
};
