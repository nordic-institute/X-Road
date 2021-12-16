module.exports = {
  navigateAndMakeTestable: function () {
    this.logMessage('navigateAndMakeTestable()');
    this.navigate();
    this.waitForElementVisible('//*[@id="app"]');
    this.makeTestable();
    this.logMessage('navigateAndMakeTestable() done');
    return this;
  },

  //
  // Top row
  //
  instanceAndAddressIsVisible: function () {
    this.assert.visible('@instanceAndAddress');
    return this;
  },
  verifyCurrentUser: function (user) {
    this.api.assert.containsText(this.elements.userMenuButton, user);
    return this;
  },
  userMenuButton: {
    selector: '//button[@data-test="username-button"]',
    locateStrategy: 'xpath',
  },
  instanceAndAddress: {
    selector: '//div[@data-test="app-toolbar-server-instance-address"]',
    locateStrategy: 'xpath',
  },
  nodeName: {
    selector: '//div[@data-test="app-toolbar-node-name"]',
    locateStrategy: 'xpath',
  },

  //
  // Simple Dialog
  //
  dialogSaveButton: {
    selector: '//button[@data-test="dialog-save-button"]',
    locateStrategy: 'xpath',
  },
  dialogCancelButton: {
    selector: '//button[@data-test="dialog-cancel-button"]',
    locateStrategy: 'xpath',
  },
  dialogTitle: {
    selector: '//*[@data-test="dialog-title"]',
    locateStrategy: 'xpath',
  },
};
