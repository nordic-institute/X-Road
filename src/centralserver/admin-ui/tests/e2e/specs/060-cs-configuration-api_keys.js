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
let login;
let settings;
module.exports = {
  tags: ['cs', 'apiKeys'],
  before(browser) {
    login = browser.page.csLoginPage();
    settings = browser.page.csApiKeysPage();
  },
  beforeEach() {
    login.navigateAndMakeTestable();
    login.signInUser();
    settings.navigateAndMakeTestable();
  },
  after(browser) {
    browser.end();
  },
  'Api Key is created and listed': async () => {
    let createdApiKeyId;

    await settings
      .apiKeysViewIsVisible()
      .click('@apiKeysViewCreateKeyButton')
      .verify.elementPresent('@apiKeysWizardView')
      .click('@apiKeysCheckboxRoleRegistrationOfficerButton')
      .pause(1000)
      .click('@apiKeysViewWizardNextButton')
      .waitForElementVisible('@apiKeysViewWizardPreviousButton')
      .click('@apiKeysViewWizardPreviousButton')
      .waitForElementVisible('@apiKeysViewWizardNextButton')
      .click('@apiKeysViewWizardNextButton')
      .waitForElementVisible('@apiKeysViewWizardCreateKeyButton')
      .click('@apiKeysViewWizardCreateKeyButton')
      .waitForElementVisible('@successSnackBar')
      .waitForElementPresent('@apiKeysCreatedKeyId', 1000)
      .getText('@apiKeysCreatedKeyId', function (result) {
        createdApiKeyId = result.value;
      })
      .click('@apiKeysViewWizardFinishButton')
      .apiKeysViewIsVisible();

    await settings
      .apiKeysViewIsVisible()
      .waitForElementVisible(`//div[@data-test="api-keys-view"]//div[text()=" ${createdApiKeyId} "]`);
  },
  'Api Key is revoked and not listed anymore': async () => {
    let firstApiKeyId;

    // Given
    await settings
      .apiKeysViewIsVisible()
      .getText(
        'xpath',
        '(//div[@data-test="api-key-id"])[1]',
        function (result) {
          firstApiKeyId = result.value;
        },
      );

    // When
    await settings
      .apiKeysViewIsVisible()
      .click(
        `//button[@data-test="api-key-row-${firstApiKeyId}-revoke-button"]`,
      )
      .pause(500)
      .click('@dialogSaveButton');

    // Then
    await settings
      .apiKeysViewIsVisible()
      .waitForElementNotPresent(
        `//div[@data-test="api-keys-view"]//div[text()=" ${firstApiKeyId} "]`,
        5 * 1000,
      );
  },
  'Api Key is edited with additional roles': async () => {
    let createdApiKeyId;

    // Given
    await settings
      .apiKeysViewIsVisible()
      .click('@apiKeysViewCreateKeyButton')
      .verify.elementPresent('@apiKeysWizardView')
      .click('@apiKeysCheckboxRoleRegistrationOfficerButton')
      .pause(1000)
      .click('@apiKeysViewWizardNextButton')
      .waitForElementVisible('@apiKeysViewWizardCreateKeyButton', 5 * 1000)
      .click('@apiKeysViewWizardCreateKeyButton')
      .waitForElementVisible('@successSnackBar')
      .waitForElementPresent('@apiKeysCreatedKeyId', 1000)
      .getText('@apiKeysCreatedKeyId', function (result) {
        createdApiKeyId = result.value;
      })
      .click('@apiKeysViewWizardFinishButton')
      .apiKeysViewIsVisible();

    // When
    await settings
      .apiKeysViewIsVisible()
      .click(
        `//button[@data-test="api-key-row-${createdApiKeyId}-edit-button"]`,
      )
      .pause(500)
      .click('@apiKeysCheckboxRoleRegistrationOfficerButton')
      .click('@apiKeysCheckboxRoleSecurityOfficerButton')
      .click('@apiKeysCheckboxRoleSystemAdministratorButton')
      .pause(500)
      .click('@dialogSaveButton');

    // Then
    await settings
      .apiKeysViewIsVisible()
      .verify.not.containsText(
        `//span[@data-test="api-key-row-${createdApiKeyId}-roles"]`,
        'Registration Officer',
      )
      .verify.containsText(
        `//span[@data-test="api-key-row-${createdApiKeyId}-roles"]`,
        'Security Officer',
      )
      .verify.containsText(
        `//span[@data-test="api-key-row-${createdApiKeyId}-roles"]`,
        'System Administrator',
      );
  },
};
