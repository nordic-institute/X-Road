module.exports = {
  transpileDependencies: ["vuetify"],
  configureWebpack: {
    output: {
      libraryExport: 'default' // Needed when using default export
      // https://cli.vuejs.org/guide/build-targets.html#vue-vs-js-ts-entry-files
    },
  },
  css: { extract: false } // Keeps the vue sfc style parts in the build
};
