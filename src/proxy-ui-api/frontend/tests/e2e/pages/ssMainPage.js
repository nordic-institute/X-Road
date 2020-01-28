
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
  },
  logout: function() {
    this.click('@userMenuButton').click('@userMenuitemLogout');
    return this;
  },
  acceptLogout: function() {
    this.click('@logoutOKButton');
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
  },
  openTestGov: function() {
    this.click('@testGovListItem');
    return this;
  },
  openTestService: function() {
    this.click('@testServiceListItem');
    return this;
  }
};

var clientInfoCommands = {
  openDetailsTab: function() {
    this.click('@detailsTab');
    return this;
  },
  openServiceClientsTab: function() {
    this.click('@serviceClientsTab');
    return this;
  },
  openServicesTab: function() {
    this.click('@servicesTab');
    return this;
  },
  openInternalServersTab: function() {
    this.click('@internalServersTab');
    return this;
  },
  openLocalGroupsTab: function() {
    this.click('@localGroupsTab');
    return this;
  }
};

var clientDetailsCommands = {
  openSignCertificateInfo: function() {
    this.click('@clientSignCertificate');
    return this;
  }
};

var certificatePopupCommands = {
  close: function() {
    this.click('@certificateInfoOKButton');
    return this;
  }
};


var clientLocalGroupsCommands = {
  openAddLocalGroupDialog: function() {
    this.click('@addGroupButton');
    return this;
  },
  filterBy: function(filter) {
    this.clearValue2('@filterInput');
    this.setValue('@filterInput', filter);
    return this;
  },
  openAddDialog: function() {
    this.click('@addGroupButton');
    return this;
  },
  confirmAddDialog: function() {
    this.click('@confirmAddButton');
    return this;
  },
  cancelAddDialog: function() {
    this.click('@cancelAddButton');
    return this;
  },
  enterCode: function(code) {
    this.clearValue2('@groupCode');
    this.setValue('@groupCode', code);
    return this;
  },
  enterDescription: function(description) {
    this.clearValue2('@groupDescription');
    this.setValue('@groupDescription', description);
    return this;
  }
};


module.exports = {
  url: process.env.VUE_DEV_SERVER_URL,
  commands: [navigateCommands],
  elements: {
    clientsTab: { 
      selector: '//div[contains(@class, "v-tabs-bar__content")]//a[text()="Clients"]', 
      locateStrategy: 'xpath'},
    keysTab: { 
      selector: '//div[contains(@class, "v-tabs-bar__content")]//a[text()="Keys and certificates"]', 
      locateStrategy: 'xpath'},
    diagnosticsTab: { 
      selector: '//div[contains(@class, "v-tabs-bar__content")]//a[text()="Diagnostics"]', 
      locateStrategy: 'xpath'},
    settingsTab: { 
      selector: '//div[contains(@class, "v-tabs-bar__content")]//a[text()="Settings"]', 
      locateStrategy: 'xpath' },
    userMenuButton: { 
      selector: 'div.v-toolbar__content button .mdi-account-circle', 
      locateStrategy: 'css selector' },
    userMenuitemLogout: { 
      selector: '#logout-list-tile', 
      locateStrategy: 'css selector' },
    logoutOKButton: { 
      selector: '//div[contains(@class, "v-dialog")]//button[.//*[contains(text(), "Ok")]]', 
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
          locateStrategy: 'xpath' },
        testServiceListItem: { 
          selector: '//tbody//span[contains(text(),"TestService")]', 
          locateStrategy: 'xpath' },
        testGovListItem: { 
          selector: '//tbody//span[contains(text(),"TestGov")]', 
          locateStrategy: 'xpath' }
      }
    },
    clientInfo: {
      selector: 'h1.display-1',
      locateStrategy: 'css selector',
      commands: [clientInfoCommands],
      elements: {
        detailsTab: { 
          selector: '//div[contains(@class, "v-tabs-bar__content")]//a[contains(@class, "v-tab") and contains(text(), "details")]',
          locateStrategy: 'xpath' },
        serviceClientsTab: { 
          selector: '//div[contains(@class, "v-tabs-bar__content")]//a[contains(@class, "v-tab") and contains(text(), "service clients")]',
          locateStrategy: 'xpath' },
        servicesTab: { 
          selector: '//div[contains(@class, "v-tabs-bar__content")]//a[contains(@class, "v-tab") and contains(text(), "services")]',
          locateStrategy: 'xpath' },
        internalServersTab: { 
          selector: '//div[contains(@class, "v-tabs-bar__content")]//a[contains(@class, "v-tab") and contains(text(), "internal servers")]',
          locateStrategy: 'xpath' },
        localGroupsTab: { 
          selector: '//div[contains(@class, "v-tabs-bar__content")]//a[contains(@class, "v-tab") and contains(text(), "local groups")]',
          locateStrategy: 'xpath' }
      },
      sections: {
        details: {
          selector: '//div[contains(@class, "xrd-view-common") and .//*[contains(@class, "v-tab--active") and contains(text(), "details")]]',
          locateStrategy: 'xpath',
          commands: [clientDetailsCommands],
          elements: {
            clientSignCertificate: { 
              selector: 'span.cert-name',
              locateStrategy: 'css selector' }
          }      
        },
        localGroups: {
          selector: '//div[contains(@class, "xrd-view-common") and .//*[contains(@class, "v-tab--active") and contains(text(), "local groups")]]',
          locateStrategy: 'xpath',
          commands: [clientLocalGroupsCommands],
          elements: {
            filterInput: { 
              selector: '//input',
              locateStrategy: 'xpath' },
            addGroupButton: { 
              selector: '//button[.//*[contains(text(), "Add group")]]',
              locateStrategy: 'xpath' },
            confirmAddButton: { 
              selector: '//button[.//*[contains(text(), "Add")]]',
              locateStrategy: 'xpath' },
            cancelAddButton: { 
              selector: '//button[.//*[contains(text(), "Cancel")]]',
              locateStrategy: 'xpath' },
            groupCode: { 
              selector: '//div[contains(@class, "dlg-edit-row") and .//*[contains(text(), "Code")]]//input',
              locateStrategy: 'xpath' },
            groupDescription: { 
              selector: '//div[contains(@class, "dlg-edit-row") and .//*[contains(text(), "Description")]]//input',
              locateStrategy: 'xpath' }
          }      
        }
      }     
    },
    certificatePopup: {
      selector: '//*[contains(@class,"v-dialog--active") and .//*[contains(@class, "headline") and contains(text(),"Certificate")]]',
      locateStrategy: 'xpath',
      commands: [certificatePopupCommands],
      elements: {
        certificateInfoOKButton: { 
          selector: '.v-dialog--active button',
          locateStrategy: 'css selector' }
      }
    }
  }
};
