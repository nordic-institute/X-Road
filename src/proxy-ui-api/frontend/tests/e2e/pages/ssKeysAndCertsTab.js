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

var keysAndCertsTabCommands = {
  openSignAuthKeysTab: function() {
    this.click('@signAuthKeysTab');
    return this;
  },
  openApiKeyTab: function() {
    this.click('@apiKeyTab');
    return this;
  },
  opentlsCertificateTab: function() {
    this.click('@tlsCertificateTab');
    return this;
  }
};

var signAuthKeysTabCommands = {
  toggleExpandToken: function() {
    this.click('@expandButton');
    return this;
  },
  openTokenDetails: function() {
    this.click('@tokenLink');
    return this;
  },
  loginToken: function() {
    this.click('@loginButton');
    return this;
  },
  logoutToken: function() {
    this.click('@logoutButton');
    return this;
  },
  generateAuthCSR: function() {
    this.click('@authGenerateCSRButton');
    return this;
  },
  openAuthKeyDetails: function() {
    this.click('@authKeyIcon');
    return this;
  },
  openAuthCertDetails: function() {
    this.click('@authCertIcon');
    return this;
  },
  generateSignCSR: function() {
    this.click('@signGenerateCSRButton');
    return this;
  },
  openSignKeyDetails: function() {
    this.click('@signKeyIcon');
    return this;
  },
  openSignCertDetails: function() {
    this.click('@signCertIcon');
    return this;
  }
};

var confirmationDialogCommands = {
  confirm: function() {
    this.api.keys(this.api.Keys.TAB);
    this.api.keys(this.api.Keys.TAB);
    this.api.keys(this.api.Keys.SPACE);
    //this.click('@yesButton');
    return this;
  },
  cancel: function() {
    this.api.keys(this.api.Keys.TAB);
    this.api.keys(this.api.Keys.SPACE);
    //this.click('@cancelButton');
    return this;
  }
};

var loginDialogCommands = {
  confirm: function() {
    this.api.keys(this.api.Keys.TAB);
    this.api.keys(this.api.Keys.TAB);
    this.api.keys(this.api.Keys.SPACE);
    //this.click('@yesButton');
    return this;
  },
  cancel: function() {
    this.api.keys(this.api.Keys.TAB);
    this.api.keys(this.api.Keys.SPACE);
    //this.click('@cancelButton');
    return this;
  },
  enterPin: function(pinCode) {
    this.clearValue2('@pinCode');
    this.setValue('@pinCode', pinCode);
    return this;
  }
};

var keyDetailsCommands = {
  confirm: function() {
    this.click('@saveButton');
    return this;
  },
  cancel: function() {
    this.click('@cancelButton');
    return this;
  },
  remove: function() {
    this.click('@deleteButton');
    return this;
  },
  enterFriendlyName: function(name) {
    this.clearValue2('@friendlyName');
    this.setValue('@friendlyName', name);
    return this;
  }
};

module.exports = {
  url: process.env.VUE_DEV_SERVER_URL,
  commands: [keysAndCertsTabCommands],
  elements: {
    signAuthKeysTab: {
      selector: '//div[contains(@class, "v-tabs-bar__content")]//a[contains(@class, "v-tab") and contains(text(), "SIGN and AUTH Keys")]',
      locateStrategy: 'xpath' },
    apiKeyTab: {
      selector: '//div[contains(@class, "v-tabs-bar__content")]//a[contains(@class, "v-tab") and contains(text(), "API Key")]',
      locateStrategy: 'xpath' },
    tlsCertificateTab: {
      selector: '//div[contains(@class, "v-tabs-bar__content")]//a[contains(@class, "v-tab") and contains(text(), "Security Server TLS Certificate")]',
      locateStrategy: 'xpath' }
  },
  sections: {
    signAuthKeysTab: {
      selector: '//div[.//a[contains(@class, "v-tab--active") and contains(text(), "SIGN and AUTH Keys")]]//div[contains(@class, "base-full-width")]',
      locateStrategy: 'xpath',
      commands: [signAuthKeysTabCommands],
      elements: {
        expandButton: {
          selector: '//div[contains(@class, "expandable")]//button[contains(@class, "v-btn--icon")]',
          locateStrategy: 'xpath' },
        tokenLink: {
          selector: '//*[@data-test="token-name"]',
          locateStrategy: 'xpath' },
        logoutButton: {
          selector: '//button[@data-test="token-logout-button"]',
          locateStrategy: 'xpath' },
        loginButton: {
          selector: '//button[@data-test="token-login-button"]',
          locateStrategy: 'xpath' },
        addKeyButton: {
          selector: '//button[@data-test="token-add-key-button"]',
          locateStrategy: 'xpath' },
        importCertButton: {
          selector: '//button[@data-test="token-import-cert-button"]',
          locateStrategy: 'xpath' },
        authGenerateCSRButton: {
          selector: '//table[./thead//th[@class="title-col" and contains(text(), "AUTH Key and Certificate")]]//button[.//*[contains(text(), "Generate CSR")]]',
          locateStrategy: 'xpath' },
        authKeyIcon: {
          selector: '//table[./thead//th[@class="title-col" and contains(text(), "AUTH Key and Certificate")]]//i[contains(@class, "icon-xrd_key")]',
          locateStrategy: 'xpath' },
        authKeyLink: {
          selector: '//table[./thead//th[@class="title-col" and contains(text(), "AUTH Key and Certificate")]]//div[contains(@class, "clickable-link")]',
          locateStrategy: 'xpath' },
        authCertIcon: {
          selector: '//table[./thead//th[@class="title-col" and contains(text(), "AUTH Key and Certificate")]]//i[contains(@class, "icon-xrd_certificate")]',
          locateStrategy: 'xpath' },
        signGenerateCSRButton: {
          selector: '//table[./thead//th[@class="title-col" and contains(text(), "SIGN Key and Certificate")]]//button[.//*[contains(text(), "Generate CSR")]]',
          locateStrategy: 'xpath' },
        signKeyIcon: {
          selector: '//table[./thead//th[@class="title-col" and contains(text(), "SIGN Key and Certificate")]]//i[contains(@class, "icon-xrd_key")]',
          locateStrategy: 'xpath' },
        signKeyLink: {
          selector: '//table[./thead//th[@class="title-col" and contains(text(), "SIGN Key and Certificate")]]//div[contains(@class, "clickable-link")]',
          locateStrategy: 'xpath' },
        signCertIcon: {
          selector: '//table[./thead//th[@class="title-col" and contains(text(), "SIGN Key and Certificate")]]//i[contains(@class, "icon-xrd_certificate")]',
          locateStrategy: 'xpath' }
      }
    },
    logoutTokenPopup: {
      selector: '//div[contains(@class, "xrd-card") and .//*[@data-test="dialog-title" and contains(text(),"Log out")]]',
      locateStrategy: 'xpath',
      commands: [confirmationDialogCommands],
      elements: {
        yesButton: {
          selector: '//button[@data-test="dialog-save-button"]',
          locateStrategy: 'xpath' },
        cancelButton: {
          selector: '//button[@data-test="dialog-cancel-button"]',
          locateStrategy: 'xpath' }
      }
    },
    loginTokenPopup: {
      selector: '//div[contains(@class, "xrd-card") and .//*[@data-test="dialog-title" and contains(text(),"Log in")]]',
      locateStrategy: 'xpath',
      commands: [loginDialogCommands], 
      elements: {
        yesButton: {
          selector: '//button[@data-test="dialog-save-button"]',
          locateStrategy: 'xpath' },
        cancelButton: {
          selector: '//button[@data-test="dialog-cancel-button"]',
          locateStrategy: 'xpath' },
        pinCode: {
          selector: '//input[@name="tokenPin"]',
          locateStrategy: 'xpath' },
        pinMessage: {
          selector: '//div[contains(@class, "v-messages__wrapper")]',
          locateStrategy: 'xpath' }
      }
    },
    authKeyDetails: {
      selector: '//div[contains(@class, "xrd-view-common") and .//*[contains(@class, "identifier-wrap") and contains(text(),"AUTH Key details")]]',
      locateStrategy: 'xpath',
      commands: [keyDetailsCommands],
      elements: {
        saveButton: {
          selector: '//button[.//*[contains(text(), "Save")]]',
          locateStrategy: 'xpath' },
        cancelButton: {
          selector: '//button[.//*[contains(text(), "Cancel")]]',
          locateStrategy: 'xpath' },
        deleteButton: {
          selector: '//button[.//*[contains(text(), "Delete")]]',
          locateStrategy: 'xpath' },
        friendlyName: {
          selector: '//input[@name="keys.friendlyName"]',
          locateStrategy: 'xpath' },
        label: {
          selector: '//div[contains(@class, "row-title") and contains(text(), "Label:")]//following-sibling::div[contains(@class, "row-data")]',
          locateStrategy: 'xpath' }
      }
    },
    signKeyDetails: {
      selector: '//div[contains(@class, "xrd-view-common") and .//*[contains(@class, "identifier-wrap") and contains(text(),"SIGN Key details")]]',
      locateStrategy: 'xpath',
      commands: [keyDetailsCommands],
      elements: {
        saveButton: {
          selector: '//button[.//*[contains(text(), "Save")]]',
          locateStrategy: 'xpath' },
        cancelButton: {
          selector: '//button[.//*[contains(text(), "Cancel")]]',
          locateStrategy: 'xpath' },
        deleteButton: {
          selector: '//button[.//*[contains(text(), "Delete")]]',
          locateStrategy: 'xpath' },
        friendlyName: {
          selector: '//input[@name="keys.friendlyName"]',
          locateStrategy: 'xpath' },
        friendlyNameMessage: {
          selector: '//span[contains(@class, "validation-provider")]//div[contains(@class, "v-messages__message")]',
          locateStrategy: 'xpath' },
        label: {
          selector: '//div[contains(@class, "row-title") and contains(text(), "Label:")]//following-sibling::div[contains(@class, "row-data")]',
          locateStrategy: 'xpath' }
      }
    },
    tokenDetails: {
      selector: '//div[contains(@class, "xrd-view-common") and .//*[contains(@class, "identifier-wrap") and contains(text(),"Token details")]]',
      locateStrategy: 'xpath',
      commands: [keyDetailsCommands],
      elements: {
        saveButton: {
          selector: '//button[.//*[contains(text(), "Save")]]',
          locateStrategy: 'xpath' },
        cancelButton: {
          selector: '//button[.//*[contains(text(), "Cancel")]]',
          locateStrategy: 'xpath' },
        friendlyName: {
          selector: '//input[@name="keys.friendlyName"]',
          locateStrategy: 'xpath' },
        type: {
          selector: '//div[contains(@class, "row-title") and contains(text(), "Type:")]//following-sibling::div[contains(@class, "row-data")]',
          locateStrategy: 'xpath' }
      }
    }
  }
};
