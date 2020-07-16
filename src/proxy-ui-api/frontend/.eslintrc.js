module.exports = {
  root: true,
  env: {
    node: true,
  },
  extends: [
    'plugin:vue/essential',
    'eslint:recommended',
    '@vue/typescript/recommended',
    '@vue/prettier',
    '@vue/prettier/@typescript-eslint',
  ],
  ignorePatterns: ['tests/', 'node_modules/'],
  parserOptions: {
    ecmaVersion: 2020,
  },
  rules: {
    'no-console': process.env.NODE_ENV === 'production' ? 'error' : 'off',
    'no-debugger': process.env.NODE_ENV === 'production' ? 'error' : 'off',
    '@typescript-eslint/camelcase': 'off',
    'vue/no-unused-vars': 'warn',
    '@typescript-eslint/no-explicit-any':
      process.env.NODE_ENV === 'production' ? 'error' : 'warn',
  },
};
