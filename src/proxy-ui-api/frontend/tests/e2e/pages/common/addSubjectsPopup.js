
/*
 * Popup dialog pageobject for adding subjects to services or endpoints
 */
const commands = {
  startSearch: function() {
    this.click('@searchButton');
    return this;
  },
  selectServiceClientType: function(type) {
    this.click('@serviceClientTypeDropdown');
    // The picker menu is attached to the main app dom tree, not the dialog
    this.api.click('//div[@role="listbox"]//div[contains(@class,"v-list-item__title") and contains(text(),"'+type+'")]');
    return this;
  },
  addSelected: function() {
    this.click('@addButton');
    return this;
  },
  cancel: function() {
    this.click('@cancelButton');
    return this;
  },
  selectSubject: function(subject) {
    this.api.click(this.selector + '//tr[.//td[contains(text(  ),"'+subject+'")]]//input[contains(@data-test, "sc-checkbox")]/following-sibling::div');
    return this;
  },
  verifyClientTypeVisible: function(type) {
    this.api.waitForElementVisible('//table[contains(@class, "members-table")]//td[contains(text(), "'+type+'")]');
    return this;
  },
  verifyClientTypeNotPresent: function(type) {
    this.api.waitForElementNotPresent('//table[contains(@class, "members-table")]//td[contains(text(), "'+type+'")]');
    return this;
  }
};

const addSubjectsPopup = {
  selector: '//div[contains(@class, "xrd-card") and .//span[contains(@class, "headline") and contains(text(),"Add Subjects")]]',
  locateStrategy: 'xpath',
  commands: [commands],
  elements: {
    searchButton: {
      selector: '//button[@data-test="search-button"]',
      locateStrategy: 'xpath' },
    addButton: {
      selector: '//button[@data-test="save"]',
      locateStrategy: 'xpath' },
    cancelButton: {
      selector: '//button[@data-test="cancel-button"]',
      locateStrategy: 'xpath' },
    serviceClientTypeDropdown: {
      selector: '//input[@data-test="serviceClientType"]/parent::*',
      locateStrategy: 'xpath' },
    timeoutApplyToAllCheckbox: {
      selector: '//input[@data-test="timeout-all"]/following-sibling::div',
      locateStrategy: 'xpath' }
  }
};

module.exports = addSubjectsPopup;
