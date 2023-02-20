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

var keysTabCommands = {
  openSignAndAuthKeys: function () {
    this.click('@signAndAuthKeysTab');
    return this;
  },
  openAPIKeys: function () {
    this.click('@APIKeysTab');
    return this;
  },
  openSecurityServerTLSKey: function () {
    this.click('@securityServerTLSKeyTab');
    return this;
  },
};

var signAuthKeysTabCommands = {
  toggleExpandToken: function () {
    this.click('@expandButton');
    return this;
  },
  openTokenDetails: function () {
    this.click('@tokenLink');
    return this;
  },
  loginToken: function () {
    this.click('@loginButton');
    return this;
  },
  logoutToken: function () {
    this.click('@logoutButton');
    return this;
  },
  importCert: function (certfile) {
    this.api.setValue(
      '//input[@type="file"]',
      require('path').resolve(__dirname + certfile),
    );
    return this;
  },
  openAddKeyWizard: function () {
    this.click('@addTokenKeyButton');
    return this;
  },
  generateAuthCSR: function () {
    this.click('@authGenerateCSRButton');
    return this;
  },
  generateAuthCSRForKey: function (keyname) {
    this.api.click(
      '//table[./thead//th[@class="title-col"]]//tr[.//div[contains(@class, "clickable-link") and ./*[contains(text(), "' +
        keyname +
        '")]]]//button[.//*[contains(text(), "Generate CSR")]]',
    );
    return this;
  },
  deleteAuthCSRForKey: function (keyname) {
    this.api.click(
      '//div[@data-test="auth-keys-table"]//tbody[.//div[contains(@class, "clickable-link") and .//*[contains(text(), "' +
        keyname +
        '")]]]//tr[.//div[contains(@class, "name-wrap")]//div[text()="Request"]]//button[.//*[contains(text(), "Delete CSR")]]',
    );
    return this;
  },
  openAuthKeyDetails: function () {
    this.click('@authKeyIcon');
    return this;
  },
  openAuthCertDetails: function () {
    this.click('@authCertIcon');
    return this;
  },
  generateSignCSR: function () {
    this.click('@signGenerateCSRButton');
    return this;
  },
  generateSignCSRForKey: function (keyname) {
    this.api.click(
      '//table[./thead//th[@class="title-col"]]//tr[.//div[contains(@class, "clickable-link") and ./*[contains(text(), "' +
        keyname +
        '")]]]//button[.//*[contains(text(), "Generate CSR")]]',
    );
    return this;
  },
  deleteSignCSRForKey: function (keyname) {
    this.api.click(
      '//table[./thead//th[@class="title-col"]]//tbody[.//div[contains(@class, "clickable-link") and .//*[contains(text(), "' +
        keyname +
        '")]]]//tr[.//div[contains(@class, "name-wrap")]//div[text()="Request"]]//button[.//*[contains(text(), "Delete CSR")]]',
    );
    return this;
  },
  openSignKeyDetails: function () {
    this.click('@signKeyIcon');
    return this;
  },
  openSignCertDetails: function () {
    this.click('@signCertIcon');
    return this;
  },
};

var confirmationDialogCommands = {
  confirm: function () {
    this.api.keys(this.api.Keys.TAB);
    this.api.keys(this.api.Keys.TAB);
    this.api.keys(this.api.Keys.SPACE);
    //this.click('@yesButton'); // Above is a workaround for occasionally failing click()
    return this;
  },
  cancel: function () {
    this.api.keys(this.api.Keys.TAB);
    this.api.keys(this.api.Keys.SPACE);
    //this.click('@cancelButton'); // Above is a workaround for occasionally failing click()
    return this;
  },
};

var loginDialogCommands = {
  confirm: function () {
    this.api.keys(this.api.Keys.TAB);
    this.api.keys(this.api.Keys.TAB);
    this.api.keys(this.api.Keys.SPACE);
    //this.click('@yesButton'); // Aboce is a workaround for occasionally failing click()
    return this;
  },
  cancel: function () {
    this.api.keys(this.api.Keys.TAB);
    this.api.keys(this.api.Keys.SPACE);
    //this.click('@cancelButton'); // Above is a workaround for occasionally failing click()
    return this;
  },
  enterPin: function (pinCode) {
    this.clearValue2('@pinCode');
    this.setValue('@pinCode', pinCode);
    return this;
  },
};

var keyDetailsCommands = {
  confirm: function () {
    this.click('@saveButton');
    return this;
  },
  cancel: function () {
    this.click('@cancelButton');
    return this;
  },
  remove: function () {
    this.click('@deleteButton');
    return this;
  },
  modifyFriendlyName: function (name) {
    this.waitForNonEmpty('@friendlyName');
    this.clearValue2('@friendlyName');
    this.setValue('@friendlyName', name);
    return this;
  },
};

var addKeywizardDetailCommands = {
  next: function () {
    this.click('@nextButton');
    return this;
  },
  cancel: function () {
    this.click('@cancelButton');
    return this;
  },
  enterLabel: function (label) {
    this.clearValue2('@keyLabel');
    this.setValue('@keyLabel', label);
    return this;
  },
};

var addKeywizardCSRCommands = {
  next: function () {
    this.click('@continueButton');
    return this;
  },
  cancel: function () {
    this.click('@cancelButton');
    return this;
  },
  selectUsageMethod: function (method) {
    this.waitForElementVisible('@csrUsage');
    return this.selectDropdownOption('@csrUsage', method);
  },
  selectService: function (service) {
    this.click('@csrService');

    this.api.pause(1000);
    // The picker menu is attached to the main app dom tree, not the dialog
    let serviceNotPresentMsg = 'Certification service selection does not contain ' + service + ' option.';
    this.api.assert.elementPresent(this.dropdownValueSelector(service), serviceNotPresentMsg);
    this.api.click(this.dropdownValueSelector(service));

    return this;
  },
  selectFormat: function (format) {
    return this.selectDropdownOption('@csrFormat', format);
  },
  selectClient: function (client) {
    return this.selectDropdownOption('@csrClient', client);
  },
  selectDropdownOption: function (dropdown, value) {
    this.click(dropdown);
    this.api.pause(1000);
    // The picker menu is attached to the main app dom tree, not the dialog
    this.api.click(this.dropdownValueSelector(value));
    return this;
  },
  dropdownValueSelector: function (optionName) {
    return (
      '//div[@role="listbox"]//div[@role="option" and contains(./descendant-or-self::*/text(),"' +
      optionName +
      '")]'
    );
  },
};

var addKeywizardGenerateCommands = {
  close: function () {
    this.click('@doneButton');
    return this;
  },
  cancel: function () {
    this.click('@cancelButton');
    return this;
  },
  generateCSR: function () {
    this.click('@generateButton');
    return this;
  },
  enterOrganization: function (name) {
    this.clearValue2('@organizationName');
    this.setValue('@organizationName', name);
    return this;
  },
  enterServerDNS: function (dns) {
    this.clearValue2('@serverDNS');
    this.setValue('@serverDNS', dns);
    return this;
  },
};

const keysTab = {
  url: `${process.env.VUE_DEV_SERVER_URL}/keys`,
  selector:
    '//div[.//a[contains(@class, "v-tab--active") and @data-test="keys"]]//div[contains(@class, "base-full-width")]',
  commands: keysTabCommands,
  elements: {
    signAndAuthKeysTab:
        '//div[contains(@class, "v-tabs-bar__content")]//*[contains(@class, "v-tab") and contains(text(), "SIGN and AUTH Keys")]',
    APIKeysTab:
        '//div[contains(@class, "v-tabs-bar__content")]//*[contains(@class, "v-tab") and contains(text(), "API Keys")]',
    securityServerTLSKeyTab:
        '//div[contains(@class, "v-tabs-bar__content")]//*[contains(@class, "v-tab") and contains(text(), "Security Server TLS Key")]',
    tokenName:  '//*[@data-test="token-name"]',
    createAPIKeyButton:  '//*[@data-test="api-key-create-key-button"]',
    generateKeyButton:
        '//*[@data-test="security-server-tls-certificate-generate-key-button"]',
    exportCertButton:
        '//*[@data-test="security-server-tls-certificate-export-certificate-button"]',
  },
  sections: {
    signAuthKeysTab: {
      selector:
        '//div[.//a[contains(@class, "v-tab--active") and contains(text(), "SIGN and AUTH Keys")]]//div[contains(@class, "base-full-width")]',
      commands: [signAuthKeysTabCommands],
      elements: {
        expandButton:
            '//div[contains(@class, "expandable")]//button[contains(@class, "v-btn--icon")]',
        tokenLink:  '//*[@data-test="token-name"]',
        logoutButton:  '//button[@data-test="token-logout-button"]',
        loginButton:  '//button[@data-test="token-login-button"]',
        addTokenKeyButton: '//button[@data-test="token-add-key-button"]',
        importCertButton: '//button[@data-test="token-import-cert-button"]',
        authGenerateCSRButton:
            '//table[./thead//th[@class="title-col" and contains(text(), "AUTH Key and Certificate")]]//button[.//*[contains(text(), "Generate CSR")]]',
        authKeyIcon:
            '//table[./thead//th[@class="title-col" and contains(text(), "AUTH Key and Certificate")]]//i[contains(@class, "icon-xrd_key")]',
        authKeyLink:
            '//table[./thead//th[@class="title-col" and contains(text(), "AUTH Key and Certificate")]]//div[contains(@class, "clickable-link")]',
        authCertIcon:
            '//table[./thead//th[@class="title-col" and contains(text(), "AUTH Key and Certificate")]]//i[contains(@class, "icon-xrd_certificate")]',
        signGenerateCSRButton:
            '//table[./thead//th[@class="title-col" and contains(text(), "SIGN Key and Certificate")]]//button[.//*[contains(text(), "Generate CSR")]]',
        signKeyIcon:
            '//table[./thead//th[@class="title-col" and contains(text(), "SIGN Key and Certificate")]]//i[contains(@class, "icon-xrd_key")]',
        signKeyLink:
            '//table[./thead//th[@class="title-col" and contains(text(), "SIGN Key and Certificate")]]//div[contains(@class, "clickable-link")]',
        signCertIcon:
            '//table[./thead//th[@class="title-col" and contains(text(), "SIGN Key and Certificate")]]//i[contains(@class, "icon-xrd_certificate")]',
          initializedAuthCert:
            '//tr[.//td[contains(text(), "Disabled")] and .//div[contains(@class, "status-text") and contains(text(), "Saved")]]//div[contains(@class, "clickable-link")]',
      },
    },
    logoutTokenPopup: {
      selector:
        '//div[contains(@class, "xrd-card") and .//*[@data-test="dialog-title" and contains(text(),"Log out")]]',
      commands: [confirmationDialogCommands],
      elements: {
        yesButton: '//button[@data-test="dialog-save-button"]',
        cancelButton:  '//button[@data-test="dialog-cancel-button"]',

      },
    },
    loginTokenPopup: {
      selector:
        '//div[contains(@class, "xrd-card") and .//*[@data-test="dialog-title" and contains(text(),"Log in")]]',
      commands: [loginDialogCommands],
      elements: {
        yesButton: '//button[@data-test="dialog-save-button"]',
        cancelButton:  '//button[@data-test="dialog-cancel-button"]',
        pinCode:  '//input[@name="tokenPin"]',
        pinMessage: '//div[contains(@class, "v-messages__wrapper")]',
      },
    },
    authKeyDetails: {
      selector:
        '//div[contains(@class, "xrd-view-common") and .//*[contains(@class, "identifier-wrap") and contains(text(),"AUTH Key details")]]',
      commands: [keyDetailsCommands],
      elements: {
        saveButton:  '//button[.//*[contains(text(), "Save")]]',
        cancelButton:  '//button[.//*[contains(text(), "Cancel")]]',
        deleteButton: '//button[.//*[contains(text(), "Delete")]]',
        friendlyName:  '//input[@name="keys.friendlyName"]',
        label:
            '//div[contains(@class, "row-title") and contains(text(), "Label:")]//following-sibling::div[contains(@class, "row-data")]',
        },
      },
    signKeyDetails: {
      selector:
        '//div[contains(@class, "xrd-view-common") and .//*[contains(@class, "identifier-wrap") and contains(text(),"SIGN Key details")]]',
      commands: [keyDetailsCommands],
      elements: {
        saveButton:  '//button[.//*[contains(text(), "Save")]]',
        cancelButton: '//button[.//*[contains(text(), "Cancel")]]',
        deleteButton:  '//button[.//*[contains(text(), "Delete")]]',
        friendlyName:  '//input[@name="keys.friendlyName"]',
        friendlyNameMessage:
            '//span[contains(@class, "validation-provider")]//div[contains(@class, "v-messages__message")]',
        label: '//div[contains(@class, "row-title") and contains(text(), "Label:")]//following-sibling::div[contains(@class, "row-data")]',
      },
    },
    tokenDetails: {
      selector:
        '//div[contains(@class, "xrd-view-common") and .//*[contains(@class, "identifier-wrap") and contains(text(),"Token details")]]',
      commands: [keyDetailsCommands],
      elements: {
        saveButton:  '//button[.//*[contains(text(), "Save")]]',
        cancelButton: '//button[.//*[contains(text(), "Cancel")]]',
        friendlyName:
          '//input[@name="keys.friendlyName"]',
        type:
            '//div[contains(@class, "row-title") and contains(text(), "Type:")]//following-sibling::div[contains(@class, "row-data")]',
      },
    },
    addKeyWizardDetails: {
      selector:
        '//div[contains(@class, "v-stepper__step--active") and .//*[contains(text(), "1")]]',
      commands: [addKeywizardDetailCommands],
      elements: {
        nextButton: '//button[@data-test="next-button"]',
        cancelButton: '//button[@data-test="cancel-button"]',
        keyLabel:  '//input[@data-test="key-label-button"]',
      },
    },
    addKeyWizardCSR: {
      selector:
        '//div[contains(@class, "v-stepper__step--active") and .//*[contains(text(), "2")]]',
      commands: [addKeywizardCSRCommands],
      elements: {
        continueButton:'//button[@data-test="save-button"]',
        previousButton: '//button[@data-test="previous-button"]',
        cancelButton:  '//button[@data-test="cancel-button"]',
        csrUsage:
            '//div[contains(@class, "v-select__selections") and ./input[@data-test="csr-usage-select"]]',
        csrService:
            '//div[contains(@class, "v-select__selections") and input[@data-test="csr-certification-service-select"]]',
        csrFormat:
            '//div[contains(@class, "v-select__selections") and input[@data-test="csr-format-select"]]',
        csrClient: '//div[contains(@class, "v-select__selections") and input[@data-test="csr-client-select"]]',
      },
    },
    addKeyWizardGenerate: {
      selector:
        '//div[contains(@class, "v-stepper__step--active") and .//*[contains(text(), "3")]]',
      commands: [addKeywizardGenerateCommands],
      elements: {
        doneButton: '(//button[@data-test="save-button"])[2]',
        previousButton:  '//button[@data-test="previous-button"]',
        cancelButton:  '(//button[@data-test="cancel-button"])[2]',
        generateButton: '//button[@data-test="generate-csr-button"]',
        organizationName: '//input[@name="O" and @data-test="dynamic-csr-input"]',
        serverDNS: '//input[@name="CN" and @data-test="dynamic-csr-input"]',
      },
    },
    generateKeyCsrWizardCsr: {
      // Generate csr for existing sign key. Page 1, CSR details
      selector:
        '//div[contains(@class, "v-stepper__step--active") and .//*[contains(text(), "1")]]',
      commands: [addKeywizardCSRCommands],
      elements: {
        continueButton: '//button[@data-test="save-button"]',
        previousButton: '//button[@data-test="previous-button"]',

        cancelButton: '//button[@data-test="cancel-button"]',
        csrUsage:
            '//div[@role="button" and .//div[contains(@class, "v-select__selections") and input[@data-test="csr-usage-select"]]]',
        csrService:
            '//div[contains(@class, "v-select__selections") and input[@data-test="csr-certification-service-select"]]',
        csrFormat:
            '//div[contains(@class, "v-select__selections") and input[@data-test="csr-format-select"]]',
        csrClient: '//div[contains(@class, "v-select__selections") and input[@data-test="csr-client-select"]]',
      },
    },
    generateKeyCsrWizardGenerate: {
      // Generate csr for existing sign key. Page 2, generate
      selector:
        '//div[contains(@class, "v-stepper__step--active") and .//*[contains(text(), "2")]]',
      commands: [addKeywizardGenerateCommands],
      elements: {
        doneButton: '(//button[@data-test="save-button"])[2]',
        previousButton:  '//button[@data-test="previous-button"]',
        cancelButton: '(//button[@data-test="cancel-button"])[2]',
        generateButton:'//button[@data-test="generate-csr-button"]',
        organizationName: '//input[@name="O" and @data-test="dynamic-csr-input"]',
        serverDNS:  '//input[@name="CN" and @data-test="dynamic-csr-input"]',
      },
    },
  },
};

module.exports = keysTab;
