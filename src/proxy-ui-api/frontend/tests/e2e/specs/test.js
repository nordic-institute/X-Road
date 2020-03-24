// For authoring Nightwatch tests, see
// http://nightwatchjs.org/guide#usage


module.exports = {
  'default e2e tests': browser => {
    browser.useCss()
      .url(process.env.VUE_DEV_SERVER_URL)
      .waitForElementVisible('#app', 5000)
      .assert.elementPresent('.login-form-toolbar-title')
      .assert.containsText('div', 'Log in');
    browser.end();
  }
}
