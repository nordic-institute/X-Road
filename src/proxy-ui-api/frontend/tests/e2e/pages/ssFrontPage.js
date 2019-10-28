
var loginCommands = {
  clearValue2: function (selector) {
    const { RIGHT_ARROW, BACK_SPACE } = this.api.Keys;
    return this.getValue(selector, (result) => {
      const chars = result.value.split('')
      // Make sure cursor is at the end of the input
      chars.forEach(() => this.setValue(selector, RIGHT_ARROW))
      // Delete all the existing characters
      chars.forEach(() => this.setValue(selector, BACK_SPACE))
    })
  },
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
