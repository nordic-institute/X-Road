
var loginCommands = {
  clearUsername: function() {
    //this.clearValue('@usernameInput');
    this
     .setValue('@usernameInput','Z');
    this.api
     .keys([this.api.Keys.CONTROL, "a", this.api.Keys.CONTROL]);
    this.api
     .keys('\uE017');
    return this;
  },
  clearPassword: function() {
    //this.clearValue('@passwordInput');
    this
     .setValue('@passwordInput','Z');
    this.api
     .keys([this.api.Keys.CONTROL, "a", this.api.Keys.CONTROL]);
    this.api
     .keys('\uE017');
    return this;
  },
  enterUsername: function(username) {
    this.setValue('@usernameInput', username);
    return this;
  },
  enterPassword: function(password) {
    this.setValue('@passwordInput', password);
    return this;
  },
  signin: function() {
    this.click('@loginButton');
    return this;
  }
};

module.exports = {
  url: process.env.VUE_DEV_SERVER_URL,
  commands: [loginCommands],
  elements: {
    usernameInput: { selector: 'input[name=login]' },
    passwordInput: { selector: 'input[id=password]' },
    loginButton: { selector: 'div.v-btn__content' }

  }
};
