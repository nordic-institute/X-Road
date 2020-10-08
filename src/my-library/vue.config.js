module.exports = {
  lintOnSave: false,
  transpileDependencies: ["vuetify"],
  css: { extract: false },
  configureWebpack: {
    output: {
      libraryExport: 'default'
    }
  }
};

