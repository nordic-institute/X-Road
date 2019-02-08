module.exports = {
  devServer: {
    proxy: 'http://docker-rest-ui-ss.local:8020' // this needs to be parametrized (address where backend runs)
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
