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
      '//table[./thead//th[@class="title-col" and contains(text(), "AUTH Key and Certificate")]]//tbody[.//div[contains(@class, "clickable-link") and .//*[contains(text(), "' +
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
  enterFriendlyName: function (name) {
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
    this.click('@csrUsage');

    this.api.pause(1000);
    // The picker menu is attached to the main app dom tree, not the dialog
    this.api.click(
      '//div[@role="listbox"]//div[@role="option" and contains(./descendant-or-self::*/text(),"' +
        method +
        '")]',
    );

    return this;
  },
  selectService: function (service) {
    this.click('@csrService');

    this.api.pause(1000);
    // The picker menu is attached to the main app dom tree, not the dialog
    this.api.click(
      '//div[@role="listbox"]//div[@role="option" and contains(./descendant-or-self::*/text(),"' +
        service +
        '")]',
    );

    return this;
  },
  selectFormat: function (format) {
    this.click('@csrFormat');

    this.api.pause(1000);
    // The picker menu is attached to the main app dom tree, not the dialog
    this.api.click(
      '//div[@role="listbox"]//div[@role="option" and contains(./descendant-or-self::*/text(),"' +
        format +
        '")]',
    );

    return this;
  },
  selectClient: function (client) {
    this.click('@csrClient');

    this.api.pause(1000);
    // The picker menu is attached to the main app dom tree, not the dialog
    this.api.click(
      '//div[@role="listbox"]//div[@role="option" and contains(./descendant-or-self::*/text(),"' +
        client +
        '")]',
    );

    return this;
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
  locateStrategy: 'xpath',
  commands: keysTabCommands,
  elements: {
    signAndAuthKeysTab: {
      selector:
        '//div[contains(@class, "v-tabs-bar__content")]//*[contains(@class, "v-tab") and contains(text(), "SIGN and AUTH Keys")]',
      locateStrategy: 'xpath',
    },
    APIKeysTab: {
      selector:
        '//div[contains(@class, "v-tabs-bar__content")]//*[contains(@class, "v-tab") and contains(text(), "API Keys")]',
      locateStrategy: 'xpath',
    },
    securityServerTLSKeyTab: {
      selector:
        '//div[contains(@class, "v-tabs-bar__content")]//*[contains(@class, "v-tab") and contains(text(), "Security Server TLS Key")]',
      locateStrategy: 'xpath',
    },
    tokenName: {
      selector: '//*[@data-test="token-name"]',
      locateStrategy: 'xpath',
    },
    createAPIKeyButton: {
      selector: '//*[@data-test="api-key-create-key-button"]',
      locateStrategy: 'xpath',
    },
    generateKeyButton: {
      selector:
        '//*[@data-test="security-server-tls-certificate-generate-key-button"]',
      locateStrategy: 'xpath',
    },
    exportCertButton: {
      selector:
        '//*[@data-test="security-server-tls-certificate-export-certificate-button"]',
      locateStrategy: 'xpath',
    },
  },
  sections: {
    signAuthKeysTab: {
      selector:
        '//div[.//a[contains(@class, "v-tab--active") and contains(text(), "SIGN and AUTH Keys")]]//div[contains(@class, "base-full-width")]',
      locateStrategy: 'xpath',
      commands: [signAuthKeysTabCommands],
      elements: {
        expandButton: {
          selector:
            '//div[contains(@class, "expandable")]//button[contains(@class, "v-btn--icon")]',
          locateStrategy: 'xpath',
        },
        tokenLink: {
          selector: '//*[@data-test="token-name"]',
          locateStrategy: 'xpath',
        },
        logoutButton: {
          selector: '//button[@data-test="token-logout-button"]',
          locateStrategy: 'xpath',
        },
        loginButton: {
          selector: '//button[@data-test="token-login-button"]',
          locateStrategy: 'xpath',
        },
        addTokenKeyButton: {
          selector: '//button[@data-test="token-add-key-button"]',
          locateStrategy: 'xpath',
        },
        importCertButton: {
          selector: '//button[@data-test="token-import-cert-button"]',
          locateStrategy: 'xpath',
        },
        authGenerateCSRButton: {
          selector:
            '//table[./thead//th[@class="title-col" and contains(text(), "AUTH Key and Certificate")]]//button[.//*[contains(text(), "Generate CSR")]]',
          locateStrategy: 'xpath',
        },
        authKeyIcon: {
          selector:
            '//table[./thead//th[@class="title-col" and contains(text(), "AUTH Key and Certificate")]]//i[contains(@class, "icon-xrd_key")]',
          locateStrategy: 'xpath',
        },
        authKeyLink: {
          selector:
            '//table[./thead//th[@class="title-col" and contains(text(), "AUTH Key and Certificate")]]//div[contains(@class, "clickable-link")]',
          locateStrategy: 'xpath',
        },
        authCertIcon: {
          selector:
            '//table[./thead//th[@class="title-col" and contains(text(), "AUTH Key and Certificate")]]//i[contains(@class, "icon-xrd_certificate")]',
          locateStrategy: 'xpath',
        },
        signGenerateCSRButton: {
          selector:
            '//table[./thead//th[@class="title-col" and contains(text(), "SIGN Key and Certificate")]]//button[.//*[contains(text(), "Generate CSR")]]',
          locateStrategy: 'xpath',
        },
        signKeyIcon: {
          selector:
            '//table[./thead//th[@class="title-col" and contains(text(), "SIGN Key and Certificate")]]//i[contains(@class, "icon-xrd_key")]',
          locateStrategy: 'xpath',
        },
        signKeyLink: {
          selector:
            '//table[./thead//th[@class="title-col" and contains(text(), "SIGN Key and Certificate")]]//div[contains(@class, "clickable-link")]',
          locateStrategy: 'xpath',
        },
        signCertIcon: {
          selector:
            '//table[./thead//th[@class="title-col" and contains(text(), "SIGN Key and Certificate")]]//i[contains(@class, "icon-xrd_certificate")]',
          locateStrategy: 'xpath',
        },
      },
    },
    logoutTokenPopup: {
      selector:
        '//div[contains(@class, "xrd-card") and .//*[@data-test="dialog-title" and contains(text(),"Log out")]]',
      locateStrategy: 'xpath',
      commands: [confirmationDialogCommands],
      elements: {
        yesButton: {
          selector: '//button[@data-test="dialog-save-button"]',
          locateStrategy: 'xpath',
        },
        cancelButton: {
          selector: '//button[@data-test="dialog-cancel-button"]',
          locateStrategy: 'xpath',
        },
      },
    },
    loginTokenPopup: {
      selector:
        '//div[contains(@class, "xrd-card") and .//*[@data-test="dialog-title" and contains(text(),"Log in")]]',
      locateStrategy: 'xpath',
      commands: [loginDialogCommands],
      elements: {
        yesButton: {
          selector: '//button[@data-test="dialog-save-button"]',
          locateStrategy: 'xpath',
        },
        cancelButton: {
          selector: '//button[@data-test="dialog-cancel-button"]',
          locateStrategy: 'xpath',
        },
        pinCode: {
          selector: '//input[@name="tokenPin"]',
          locateStrategy: 'xpath',
        },
        pinMessage: {
          selector: '//div[contains(@class, "v-messages__wrapper")]',
          locateStrategy: 'xpath',
        },
      },
    },
    authKeyDetails: {
      selector:
        '//div[contains(@class, "xrd-view-common") and .//*[contains(@class, "identifier-wrap") and contains(text(),"AUTH Key details")]]',
      locateStrategy: 'xpath',
      commands: [keyDetailsCommands],
      elements: {
        saveButton: {
          selector: '//button[.//*[contains(text(), "Save")]]',
          locateStrategy: 'xpath',
        },
        cancelButton: {
          selector: '//button[.//*[contains(text(), "Cancel")]]',
          locateStrategy: 'xpath',
        },
        deleteButton: {
          selector: '//button[.//*[contains(text(), "Delete")]]',
          locateStrategy: 'xpath',
        },
        friendlyName: {
          selector: '//input[@name="keys.friendlyName"]',
          locateStrategy: 'xpath',
        },
        label: {
          selector:
            '//div[contains(@class, "row-title") and contains(text(), "Label:")]//following-sibling::div[contains(@class, "row-data")]',
          locateStrategy: 'xpath',
        },
      },
    },
    signKeyDetails: {
      selector:
        '//div[contains(@class, "xrd-view-common") and .//*[contains(@class, "identifier-wrap") and contains(text(),"SIGN Key details")]]',
      locateStrategy: 'xpath',
      commands: [keyDetailsCommands],
      elements: {
        saveButton: {
          selector: '//button[.//*[contains(text(), "Save")]]',
          locateStrategy: 'xpath',
        },
        cancelButton: {
          selector: '//button[.//*[contains(text(), "Cancel")]]',
          locateStrategy: 'xpath',
        },
        deleteButton: {
          selector: '//button[.//*[contains(text(), "Delete")]]',
          locateStrategy: 'xpath',
        },
        friendlyName: {
          selector: '//input[@name="keys.friendlyName"]',
          locateStrategy: 'xpath',
        },
        friendlyNameMessage: {
          selector:
            '//span[contains(@class, "validation-provider")]//div[contains(@class, "v-messages__message")]',
          locateStrategy: 'xpath',
        },
        label: {
          selector:
            '//div[contains(@class, "row-title") and contains(text(), "Label:")]//following-sibling::div[contains(@class, "row-data")]',
          locateStrategy: 'xpath',
        },
      },
    },
    tokenDetails: {
      selector:
        '//div[contains(@class, "xrd-view-common") and .//*[contains(@class, "identifier-wrap") and contains(text(),"Token details")]]',
      locateStrategy: 'xpath',
      commands: [keyDetailsCommands],
      elements: {
        saveButton: {
          selector: '//button[.//*[contains(text(), "Save")]]',
          locateStrategy: 'xpath',
        },
        cancelButton: {
          selector: '//button[.//*[contains(text(), "Cancel")]]',
          locateStrategy: 'xpath',
        },
        friendlyName: {
          selector: '//input[@name="keys.friendlyName"]',
          locateStrategy: 'xpath',
        },
        type: {
          selector:
            '//div[contains(@class, "row-title") and contains(text(), "Type:")]//following-sibling::div[contains(@class, "row-data")]',
          locateStrategy: 'xpath',
        },
      },
    },
    addKeyWizardDetails: {
      selector:
        '//div[contains(@class, "v-stepper__step--active") and .//*[contains(text(), "1")]]',
      locateStrategy: 'xpath',
      commands: [addKeywizardDetailCommands],
      elements: {
        nextButton: {
          selector: '//button[@data-test="next-button"]',
          locateStrategy: 'xpath',
        },
        cancelButton: {
          selector: '//button[@data-test="cancel-button"]',
          locateStrategy: 'xpath',
        },
        keyLabel: {
          selector: '//input[@data-test="key-label-button"]',
          locateStrategy: 'xpath',
        },
      },
    },
    addKeyWizardCSR: {
      selector:
        '//div[contains(@class, "v-stepper__step--active") and .//*[contains(text(), "2")]]',
      locateStrategy: 'xpath',
      commands: [addKeywizardCSRCommands],
      elements: {
        continueButton: {
          selector: '//button[@data-test="save-button"]',
          locateStrategy: 'xpath',
        },
        previousButton: {
          selector: '//button[@data-test="previous-button"]',
          locateStrategy: 'xpath',
        },
        cancelButton: {
          selector: '//button[@data-test="cancel-button"]',
          locateStrategy: 'xpath',
        },
        csrUsage: {
          selector:
            '//div[contains(@class, "v-select__selections") and ./input[@data-test="csr-usage-select"]]',
          locateStrategy: 'xpath',
        },
        csrService: {
          selector:
            '//div[contains(@class, "v-select__selections") and input[@data-test="csr-certification-service-select"]]',
          locateStrategy: 'xpath',
        },
        csrFormat: {
          selector:
            '//div[contains(@class, "v-select__selections") and input[@data-test="csr-format-select"]]',
          locateStrategy: 'xpath',
        },
        csrClient: {
          selector:
            '//div[contains(@class, "v-select__selections") and input[@data-test="csr-client-select"]]',
          locateStrategy: 'xpath',
        },
      },
    },
    addKeyWizardGenerate: {
      selector:
        '//div[contains(@class, "v-stepper__step--active") and .//*[contains(text(), "3")]]',
      locateStrategy: 'xpath',
      commands: [addKeywizardGenerateCommands],
      elements: {
        doneButton: {
          selector: '(//button[@data-test="save-button"])[2]',
          locateStrategy: 'xpath',
        },
        previousButton: {
          selector: '//button[@data-test="previous-button"]',
          locateStrategy: 'xpath',
        },
        cancelButton: {
          selector: '(//button[@data-test="cancel-button"])[2]',
          locateStrategy: 'xpath',
        },
        generateButton: {
          selector: '//button[@data-test="generate-csr-button"]',
          locateStrategy: 'xpath',
        },
        organizationName: {
          selector: '//input[@name="O" and @data-test="dynamic-csr-input"]',
          locateStrategy: 'xpath',
        },
        serverDNS: {
          selector: '//input[@name="CN" and @data-test="dynamic-csr-input"]',
          locateStrategy: 'xpath',
        },
      },
    },
    generateKeyCsrWizardCsr: {
      // Generate csr for existing sign key. Page 1, CSR details
      selector:
        '//div[contains(@class, "v-stepper__step--active") and .//*[contains(text(), "1")]]',
      locateStrategy: 'xpath',
      commands: [addKeywizardCSRCommands],
      elements: {
        continueButton: {
          selector: '//button[@data-test="save-button"]',
          locateStrategy: 'xpath',
        },
        previousButton: {
          selector: '//button[@data-test="previous-button"]',
          locateStrategy: 'xpath',
        },
        cancelButton: {
          selector: '//button[@data-test="cancel-button"]',
          locateStrategy: 'xpath',
        },
        csrUsage: {
          selector:
            '//div[@role="button" and .//div[contains(@class, "v-select__selections") and input[@data-test="csr-usage-select"]]]',
          locateStrategy: 'xpath',
        },
        csrService: {
          selector:
            '//div[contains(@class, "v-select__selections") and input[@data-test="csr-certification-service-select"]]',
          locateStrategy: 'xpath',
        },
        csrFormat: {
          selector:
            '//div[contains(@class, "v-select__selections") and input[@data-test="csr-format-select"]]',
          locateStrategy: 'xpath',
        },
        csrClient: {
          selector:
            '//div[contains(@class, "v-select__selections") and input[@data-test="csr-client-select"]]',
          locateStrategy: 'xpath',
        },
      },
    },
    generateKeyCsrWizardGenerate: {
      // Generate csr for existing sign key. Page 2, generate
      selector:
        '//div[contains(@class, "v-stepper__step--active") and .//*[contains(text(), "2")]]',
      locateStrategy: 'xpath',
      commands: [addKeywizardGenerateCommands],
      elements: {
        doneButton: {
          selector: '(//button[@data-test="save-button"])[2]',
          locateStrategy: 'xpath',
        },
        previousButton: {
          selector: '//button[@data-test="previous-button"]',
          locateStrategy: 'xpath',
        },
        cancelButton: {
          selector: '(//button[@data-test="cancel-button"])[2]',
          locateStrategy: 'xpath',
        },
        generateButton: {
          selector: '//button[@data-test="generate-csr-button"]',
          locateStrategy: 'xpath',
        },
        organizationName: {
          selector: '//input[@name="O" and @data-test="dynamic-csr-input"]',
          locateStrategy: 'xpath',
        },
        serverDNS: {
          selector: '//input[@name="CN" and @data-test="dynamic-csr-input"]',
          locateStrategy: 'xpath',
        },
      },
    },
  },
};

module.exports = keysTab;
