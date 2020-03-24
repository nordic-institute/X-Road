
var loginCommands = {
  clearUsername: function () {
    this.clearValue2('@usernameInput');
    return this;
  },
  clearPassword: function () {
    this.clearValue2('@passwordInput');
    return this;
  },
  enterUsername: function (username) {
    this.setValue('@usernameInput', username);
    return this;
  },
  enterPassword: function (password) {
    this.setValue('@passwordInput', password);
    return this;
  },
  signin: function () {
    this.click('@loginButton');
    return this;
  },
  signinDefaultUser: function () {
    this.clearValue2('@usernameInput');
    this.clearValue2('@passwordInput');
    this.setValue('@usernameInput', this.api.globals.login_usr);
    this.setValue('@passwordInput', this.api.globals.login_pwd);
    this.click('@loginButton');
    return this;
  }
};

module.exports = {
  url: process.env.VUE_DEV_SERVER_URL,
  commands: [loginCommands],
  elements: {
    usernameInput: { selector: 'input[id=username]' },
    passwordInput: { selector: 'input[id=password]' },
    loginButton: { selector: 'button[id=submit-button]' }
  }
};
