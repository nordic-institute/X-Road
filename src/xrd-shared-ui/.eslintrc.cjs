/* eslint-env node */
require('@rushstack/eslint-patch/modern-module-resolution')

module.exports = {
  root: true,
  'extends': [
    'eslint:recommended',
    'plugin:vue/vue3-recommended',
    'plugin:vuetify/recommended',
    '@vue/eslint-config-typescript'
  ],
  parser: "vue-eslint-parser",
  parserOptions: {
    ecmaVersion: 'latest',
    sourceType: 'module'
  }
}
