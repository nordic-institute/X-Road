
var navigateCommands = {
  openClientsTab: function() {
    this.click('@clientsTab');
    return this;
  },
  openKeysTab: function() {
    this.click('@keysTab');
    return this;
  },
  openDiagnosticsTab: function() {
    this.click('@diagnosticsTab');
    return this;
  },
  openSettingsTab: function() {
    this.click('@settingsTab');
    return this;
  }
};

var clientTabCommands = {};

module.exports = {
  url: process.env.VUE_DEV_SERVER_URL,
  commands: [navigateCommands],
  elements: {
    clientsTab: { 
      selector: '//div[contains(@class, "v-tabs__bar")]//a[text()="clients"]', 
      locateStrategy: 'xpath'},
    keysTab: { 
      selector: '//div[contains(@class, "v-tabs__bar")]//a[text()="keys and certificates"]', 
      locateStrategy: 'xpath'},
    diagnosticsTab: { 
      selector: '//div[contains(@class, "v-tabs__bar")]//a[text()="diagnostics"]', 
      locateStrategy: 'xpath'},
    settingsTab: { 
      selector: '//div[contains(@class, "v-tabs__bar")]//a[text()="settings"]', 
      locateStrategy: 'xpath' }
  },
  
  sections: {
    clientsTab: {
      selector: '//div[contains(@class, "main-content") and .//a[contains(@class, "v-tabs__item--active") and text()="clients"]]//div[contains(@class, "mt-5")]',
      commands: [clientTabCommands],
      elements: {
        addClientButton: { selector: '//div[contains(@class, "v-btn__content") and text()="Add client"]', locateStrategy: 'xpath' },
      }
    }
  }
};
