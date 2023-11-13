module.exports = {
  root: true,
  env: {
    node: true,
  },
  extends: [
    'eslint:recommended',
    '@vue/eslint-config-typescript',
    'plugin:vue/vue3-recommended',
    'plugin:vuetify/base',
    'plugin:@intlify/vue-i18n/recommended',
    'plugin:vue-scoped-css/vue3-recommended', // Linter for vue scoped styles (eslint-plugin-vue-scoped-css)
    'plugin:prettier-vue/recommended',
    'prettier',
  ],
  ignorePatterns: ['node_modules/'],
  parser: 'vue-eslint-parser',
  parserOptions: {
    ecmaVersion: 2022,
    sourceType: 'module',
  },
  settings: {
    'vue-i18n': {
      localeDir: './locales/*.{json,json5,yaml,yml}',
      messageSyntaxVersion: '^9.2.2',
    },
  },
  rules: {
    'no-console': process.env.NODE_ENV === 'production' ? 'error' : 'warn',
    'no-debugger': process.env.NODE_ENV === 'production' ? 'error' : 'off',
    '@typescript-eslint/no-var-requires': 0,
    '@typescript-eslint/camelcase': 'off',
    'vue/no-unused-vars': 'warn',
    '@typescript-eslint/no-explicit-any':
      process.env.NODE_ENV === 'production' ? 'error' : 'warn',
  },
};
