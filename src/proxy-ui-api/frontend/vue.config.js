module.exports = {
  devServer: {
    proxy: process.env.PROXY_ADDRESS || 'https://localhost:4100', // this needs to be parametrized (address where backend runs)
    https: true
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
