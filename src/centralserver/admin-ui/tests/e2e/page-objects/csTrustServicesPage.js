/*
 * The MIT License
 *
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
  navigateAndMakeTestable,
  verifyCurrentUser,
  successSnackBar,
} = require('./csCommonObjectsPage');
const path = require('path');
const trustServicesCommands = {
  trustServicesViewIsVisible() {
    this.assert.visible('@trustServicesView');
    return this;
  },
  verifyCurrentUser,
  navigateAndMakeTestable,
};

const certificationServicesCommands = {
  getCertificationServices() {
    return this.api.elements(
      'xpath',
      '//div[@data-test="certification-services"]//table/tbody/tr',
    );
  },
};

const uploadCertificationServiceCertificateCommands = {
  addCertificateToInput: function (filepath) {
    this.api.setValue('//input[@type="file"]', path.resolve(filepath));
    return this;
  },
};

const addCertificationServiceCommands = {
  addCertificateProfileToInput: function (profileType) {
    this.api.setValue('//input[@data-test="cert-profile-input"]', profileType);
    return this;
  },
};

module.exports = {
  url: `${process.env.VUE_DEV_SERVER_URL}/#/trust-services`,
  commands: [trustServicesCommands],
  elements: {
    trustServicesView: '//div[@data-test="trust-services-view"]',
    successSnackBar,
  },
  sections: {
    certificationServices: {
      selector: '//div[@data-test="certification-services"]',
      commands: [certificationServicesCommands],
      elements: {
        addCertificationServiceButton:
          '//button[@data-test="add-certification-service"]',
      },
      sections: {
        uploadCertificationServiceCertificateDialog: {
          selector:
            '//div[@data-test="dialog-simple"][//*[@data-test="dialog-title"][contains(text(), "Add certification service")]]',
          commands: [uploadCertificationServiceCertificateCommands],
          elements: {
            uploadCertificateButton:
              '//button[@data-test="dialog-save-button"]',
          },
        },
        addCertificationServiceDialog: {
          selector:
            '//div[@data-test="dialog-simple"][//*[@data-test="dialog-title"][contains(text(), "CA Settings")]]',
          commands: [addCertificationServiceCommands],
          elements: {
            saveCertificationServiceButton:
              '//button[@data-test="dialog-save-button"]',
          },
        },
      },
    },
  },
};
