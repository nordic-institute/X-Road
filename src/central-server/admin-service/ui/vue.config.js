/* eslint-disable @typescript-eslint/no-var-requires */
const path = require('path');

module.exports = {
  devServer: {
    proxy: process.env.PROXY_ADDRESS || 'https://localhost:4000',
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
      symlinks: false, // without this eslint tries to lint npm linked package (shared-ui)
      alias: {
        // Fixes an issue with $attrs and $listeners readonly errors in browser console.
        // Which is caused by two instances of vue running on same time (main project + shared-ui)
        // https://github.com/vuejs/vue-cli/issues/4271
        'core-js': path.resolve('./node_modules/core-js'),
        vue$: path.resolve('./node_modules/vue/dist/vue.runtime.esm.js'),
        vuetify: path.resolve('./node_modules/vuetify'),
        'vue-i18n': path.resolve('./node_modules/vue-i18n'),
        'vee-validate': path.resolve('./node_modules/vee-validate'),
        // alias for styles
        styles: path.resolve(__dirname, './src/assets'),
      },
    },
  },
  chainWebpack: config => {
    config
      .plugin('fork-ts-checker')
      .tap(args => {
        //args[0].memoryLimit = 4096;
        //args[0].workers = 2;
        return args
      })
  }
};
