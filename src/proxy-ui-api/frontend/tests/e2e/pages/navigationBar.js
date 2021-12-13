//import {exec} from "child_process";

var navigateCommands = {
  openDetailsTab: function () {
    this.click('@detailsTab');
    return this;
  },
  openServiceClientsTab: function () {
    this.click('@serviceClientsTab');
    return this;
  },
  openServicesTab: function () {
    this.click('@servicesTab');
    return this;
  },
  openInternalServersTab: function () {
    this.click('@internalServersTab');
    return this;
  },
  openLocalGroupsTab: function () {
    this.click('@localGroupsTab');
    return this;
  },

  openDetailsTab: function () {
    this.click('@detailsTab');
    return this;
  },
  openServiceClientsTab: function () {
    this.click('@serviceClientsTab');
    return this;
  },
  openServicesTab: function () {
    this.click('@servicesTab');
    return this;
  },
  openInternalServersTab: function () {
    this.click('@internalServersTab');
    return this;
  },
  openLocalGroupsTab: function () {
    this.click('@localGroupsTab');
    return this;
  },

  openClientsTab: function () {
    this.click('@clientsTab');
    return this;
  },
  openKeysTab: function () {
    this.click('@keysTab');
    return this;
  },
  openDiagnosticsTab: function () {
    this.click('@diagnosticsTab');
    return this;
  },
  openSettingsTab: function () {
    this.click('@settingsTab');
    return this;
  },
  logout: function () {
    this.click('@userMenuButton');
    this.pause(1000);
    this.click('@userMenuitemLogout');
    return this;
  },
  acceptLogout: function () {
    this.click('@logoutOKButton');
    return this;
  },
  closeSnackbar: function () {
    this.click('@snackBarCloseButton');
    return this;
  },
  closeAlertMessage: function () {
    this.click('@alertCloseButton');
    return this;
  },
  closeSessionExpiredPopup: function () {
    this.click('@sessionExpiredPopupOkButton');
    return this;
  },
  verifyCurrentUser: function (user) {
    this.api.assert.containsText(this.elements.userMenuButton, user);
    return this;
  },
  updateWSDLFileTo: function (newfile) {
    var sshScript;
    const { exec } = require('child_process');

    // remove protocol and port data from globals.testdata
    sshScript = `ssh ${this.api.globals.testdata
      .split(':')[1]
      .substring(2)} "cp ${this.api.globals.testfiles_path}/${newfile} ${
      this.api.globals.testfiles_path
    }/testserviceX.wsdl"`;

    exec(sshScript, (err, stdout, stderr) => {
      if (stderr) {
        console.log('SSH error, stderr: ' + stderr);
        console.log('SSH error, script: ' + sshScript);
      }
    });

    return this;
  },
};

module.exports = {
  url: process.env.VUE_DEV_SERVER_URL,
  selector: '//div[contains(@class, "layout main-content align-left")]',
  commands: navigateCommands,
  elements: {
    clientsTab: '//a[@data-test="clients"]',
    keysTab: '//a[@data-test="keys"]',
    detailsTab:
        '//div[contains(@class, "v-tabs-bar__content")]//a[contains(@class, "v-tab") and contains(text(), "Details")]',
    serviceClientsTab: '//div[contains(@class, "v-tabs-bar__content")]//a[contains(@class, "v-tab") and contains(text(), "Service clients")]',
    servicesTab:
        '//div[contains(@class, "v-tabs-bar__content")]//a[contains(@class, "v-tab") and contains(text(), "Services")]',
    internalServersTab:  '//div[contains(@class, "v-tabs-bar__content")]//a[contains(@class, "v-tab") and contains(text(), "Internal servers")]',
    localGroupsTab:
        '//div[contains(@class, "v-tabs-bar__content")]//a[contains(@class, "v-tab") and contains(text(), "Local groups")]',
    diagnosticsTab:'//a[@data-test="diagnostics"]',
    settingsTab: '//a[@data-test="settings"]',
    userMenuButton:  '//button[@data-test="username-button"]',
    userMenuitemLogout: '//*[@data-test="logout-list-tile"]',
    logoutOKButton:
        '//div[contains(@class, "v-dialog")]//button[.//*[contains(text(), "Ok")]]',
    snackBarCloseButton:  '//button[@data-test="close-snackbar"]',
    snackBarMessage: '//div[@data-test="success-snackbar"]',
    alertCloseButton: '//button[@data-test="close-alert"]',
    alertMessage:
        '//div[@data-test="contextual-alert"]//div[contains(@class, "row-wrapper")]/div',
    sessionExpiredPopupOkButton: '//button[@data-test="session-expired-ok-button"]',

    },
};


