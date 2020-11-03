
const path = require('path');

module.exports = {
  devServer: {
    proxy: process.env.PROXY_ADDRESS || 'https://localhost:4100',
    host: 'localhost',
    https: true,
  },

  pluginOptions: {
    i18n: {
      locale: 'en',
      fallbackLocale: 'en',
      localeDir: 'locales',
      enableInSFC: false,
    },
  },

  configureWebpack: {
    resolve: {
      symlinks: false, // without this eslint tries to lint npm linked package
      alias: {
        // Fixes an issue with $attrs and $listeners readonly errors in browser console.
        // Which is caused by two instances of vue running on same time
        // https://github.com/vuejs/vue-cli/issues/4271
        vue$: path.resolve('./node_modules/vue/dist/vue.runtime.esm.js'),
      },
    },
  },
};
