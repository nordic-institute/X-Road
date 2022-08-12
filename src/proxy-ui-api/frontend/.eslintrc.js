require('@rushstack/eslint-patch/modern-module-resolution');

module.exports = {
  root: true,
  env: {
    node: true,
  },
  extends: [
    'plugin:vue/recommended',
    'eslint:recommended',
    '@vue/typescript/recommended',
    '@vue/eslint-config-prettier',
    '@vue/eslint-config-typescript',
    'plugin:vue-scoped-css/recommended', // Linter for vue scoped styles (eslint-plugin-vue-scoped-css)
  ],
  ignorePatterns: ['node_modules/'],
  parserOptions: {
    ecmaVersion: 2020,
  },
  rules: {
    'no-console': process.env.NODE_ENV === 'production' ? 'error' : 'warn',
    'no-debugger': process.env.NODE_ENV === 'production' ? 'error' : 'off',
    '@typescript-eslint/no-var-requires': 0,
    '@typescript-eslint/camelcase': 'off',
    'vue/no-unused-vars': 'warn',
    'vue/multi-word-component-names': 'warn',
    '@typescript-eslint/no-explicit-any':
      process.env.NODE_ENV === 'production' ? 'error' : 'warn',
  },
};
