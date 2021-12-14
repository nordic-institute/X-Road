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

const {
  verifyCurrentUser,
  navigateAndMakeTestable,
  instanceAndAddressIsVisible,
  dialogCancelButton,
} = require('./csCommonObjectsPage');
const systemSettingsCommands = {
  systemSettingsViewIsVisible() {
    this.assert.visible('@systemSettingsView');
    return this;
  },
  serverAddressEditFieldIsVisible() {
    this.assert.visible('@systemSettingsServerAddressEditField');
  },

  initServerAddress(value) {
    this.assert.value('@systemSettingsServerAddressEditField', '');
    this.setValue('@systemSettingsServerAddressEditField', value);
    return this;
  },
  modifyServerAddress(value) {
    this.waitForNonEmpty('@systemSettingsServerAddressEditField');
    this.clearWithBackSpace('@systemSettingsServerAddressEditField');
    this.setValue('@systemSettingsServerAddressEditField', value);
    return this;
  },
  instanceAndAddressIsVisible,
  verifyCurrentUser,
  navigateAndMakeTestable,
};

module.exports = {
  url: `${process.env.VUE_DEV_SERVER_URL}/#/settings/systemsettings`,
  commands: [systemSettingsCommands],
  elements: {
    systemSettingsView: {
      selector: '//div[@data-test="system-settings-view"]',
      locateStrategy: 'xpath',
    },
    systemSettingsParametersCard: {
      selector: '//div[@data-test="system-settings-system-parameters-card"]',
      locateStrategy: 'xpath',
    },
    systemSettingsParametersTitle: {
      selector: '//div[@data-test="system-settings-system-parameters-title"]',
      locateStrategy: 'xpath',
    },
    systemSettingsInstanceIdentifierField: {
      selector: '//div[@data-test="system-settings-instance-identifier-field"]',
      locateStrategy: 'xpath',
    },
    systemSettingsServerAddressField: {
      selector:
        '//div[@data-test="system-settings-central-server-address-field"]',
      locateStrategy: 'xpath',
    },
    systemSettingsServerAddressEditButton: {
      selector:
        '//button[@data-test="system-settings-central-server-address-edit-button"][1]',
      locateStrategy: 'xpath',
    },
    systemSettingsServerAddressEditField: {
      selector:
        '//input[@data-test="system-settings-central-server-address-edit-field"]',
      locateStrategy: 'xpath',
    },
    dialogCancelButton,
  },
};
