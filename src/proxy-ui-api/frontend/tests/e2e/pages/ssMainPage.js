
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

var clientTabCommands = {
  clickNameHeader: function() {
    this.click('@listNameHeader');
    return this;
  },
  clickIDHeader: function() {
    this.click('@listIDHeader');
    return this;
  },
  clickStatusHeader: function() {
    this.click('@listStatusHeader');
    return this;
  }
};

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
      selector: '//div[contains(@class, "data-table-wrapper") and .//button[.//*[contains(text(), "add client")]]]',
      locateStrategy: 'xpath',
      commands: [clientTabCommands],
      elements: {
        addClientButton: { 
          selector: '//div[contains(@class, "v-btn__content") and text()="Add client"]',
          locateStrategy: 'xpath' },
        listNameHeader: { 
          selector: '//th[span[contains(text(),"Name")]]', 
          locateStrategy: 'xpath' },
        listIDHeader: { 
          selector: '//th[span[contains(text(),"ID")]]', 
          locateStrategy: 'xpath' },
        listStatusHeader: { 
          selector: '//th[span[contains(text(),"Status")]]', 
          locateStrategy: 'xpath' }
      }
    }
  }
};
