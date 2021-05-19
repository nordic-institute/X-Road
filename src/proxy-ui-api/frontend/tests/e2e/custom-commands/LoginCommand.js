
module.exports = class LoginCommand {
  async command() {
    const frontPage = this.api.page.ssFrontPage();
    frontPage.navigate();
    this.api.waitForElementVisible('//*[@id="app"]');
    frontPage
      .clearUsername()
      .clearPassword()
      .enterUsername(this.api.globals.login_usr)
      .enterPassword(this.api.globals.login_pwd)
      .signin();
  }
}
