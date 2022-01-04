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

var clientsTabCommands = {
  clickNameHeader: function () {
    this.click('@listNameHeader');
    return this;
  },
  clickIDHeader: function () {
    this.click('@listIDHeader');
    return this;
  },
  clickStatusHeader: function () {
    this.click('@listStatusHeader');
    return this;
  },
  clickSearchIcon: function () {
    this.click('@searchIcon');
    return this;
  },
  openClient: function (name) {
    this.api.click(
      this.selector + '//tbody//span[contains(text(),"' + name + '")]',
    );
    return this;
  },
  verifyRowName: function (row, name) {
    this.api.waitForElementVisible(
      '(//tbody/tr)[' + row + ']//span[contains(text(),"' + name + '")]',
    );
    return this;
  },
};

const clientsTab = {
  url: `${process.env.VUE_DEV_SERVER_URL}/clients`,
  selector:
    '//div[.//a[contains(@class, "v-tab--active") and @data-test="clients"]]//div[contains(@class, "base-full-width")]',
  commands: clientsTabCommands,
  elements: {
    searchIcon: '//*[contains(@class, "mdi-magnify")]',
    searchField: '//*[@data-test="search-input"]',
    addClientButton: '//button[@data-test="add-client-button"]',
    listNameHeader: '//th[contains(@class, "xrd-table-header-name")]',
    listIDHeader: '//th[contains(@class, "xrd-table-header-id")]',
    listStatusHeader: '//th[contains(@class, "xrd-table-header-status")]',
    testServiceListItem: '//tbody//span[contains(text(),"TestService")]',
    testGovListItem: '//tbody//span[contains(text(),"TestGov")]',
  },
};

module.exports = clientsTab;
