// eslint-disable-next-line @typescript-eslint/no-var-requires
const nodeExternals = require('webpack-node-externals');

module.exports = {
  chainWebpack: (config) => {
    // Remove these from the build package
    // config.externals(["vue-i18n", "vee-validate", "vuetify"]);
    // Removes all nore_modules from the built package
    config.externals(nodeExternals());

    if (process.env.NODE_ENV === 'production') {
      config.output // Modify output settings
        .libraryExport('default'); // Needed when using default export
    }
  },
  css: {
    extract: false, // Keeps the Vue SFC style parts in the build
  },
  pluginOptions: {
    webpackBundleAnalyzer: {
      openAnalyzer: true,
    },
  },
};
