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

const restEndpointCommands = {
  openAddDialog: function () {
    this.click('@addButton');
    return this;
  },
  openParametersTab: function () {
    this.click('@parametersTab');
    return this;
  },
  verifyEndpointRow: function (row, method, path) {
    //this.api.waitForElementVisible('(//table[.//*[contains(text(),"HTTP Request Method")]]/tbody/tr)['+row+']//td[contains(./descendant-or-self::*/text(),"'+method+'") and ..//td[contains(./descendant-or-self::*/text(),"'+path+'")]]');
    //TODO: Sorting is not currently functional, so check only that the row exists
    this.api.waitForElementVisible(
      '//tbody/tr//td[contains(./descendant-or-self::*/text(),"' +
        method +
        '") and ..//td[contains(./descendant-or-self::*/text(),"' +
        path +
        '")]]',
    );
    return this;
  },
  openEndpointAccessRights: function (method, path) {
    this.api.click(
      `//td[contains(@class, "wrap-right-tight") and preceding-sibling::td/text() = "${path}" and preceding-sibling::td/span/text() = "${method}"]//button[@data-test="endpoint-edit-accessrights"]`,
    );
    return this;
  },
  openEndpoint: function (method, path) {
    this.api.click(
      '//tr[.//*[contains(text(),"' +
        method +
        '")] and .//*[contains(text(),"' +
        path +
        '")]]//button[@data-test="endpoint-edit"]',
    );
    return this;
  },
  close: function () {
    this.click('@closeButton');
    return this;
  },
};

const restServiceEndpointsPage = {
  selector:
    '//div[contains(@class, "base-full-width") and .//*[@data-test="endpoints"]]',
  commands: [restEndpointCommands],
  elements: {
    parametersTab: '//*[@data-test="parameters"]',
    endpointsTab: '//*[@data-test="endpoints"]',
    addButton: '//button[@data-test="endpoint-add"]',
    closeButton:
      '//*[contains(@class, "cert-dialog-header")]//*[@data-test="close-x"]',
    editButton: '//button[@data-test="endpoint-edit"]',
    accessRightsButton: '//button[@data-test="endpoint-edit-accessrights"]',
  },
};

module.exports = restServiceEndpointsPage;
