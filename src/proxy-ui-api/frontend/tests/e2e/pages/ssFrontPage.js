var loginCommands = {
  clearUsername: function() {  
    this.clearBs('@usernameInput')
    return this;
  },
  clearPassword: function() {

    this.clearBs('@passwordInput')
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
  },
  clearBs: function(selector) {

    return this.getValue(selector, (result) => {
      const chars = result.value.split('')
      chars.forEach(() => this.setValue(selector, this.api.Keys.RIGHT_ARROW))
      chars.forEach(() => this.setValue(selector, this.api.Keys.BACK_SPACE))
    })
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
