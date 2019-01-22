module.exports = {
  devServer: {
    proxy: 'http://xroad2-docker-ss6:8020'
  },

  pluginOptions: {
    i18n: {
      locale: 'en',
      fallbackLocale: 'en',
      localeDir: 'locales',
      enableInSFC: false
    }
  }
}
