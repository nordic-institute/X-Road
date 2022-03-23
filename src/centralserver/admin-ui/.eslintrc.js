module.exports = {
  root: true,
  env: {
    node: true,
  },
  extends: [
    'plugin:vue/recommended',
    'eslint:recommended',
    '@vue/typescript/recommended',
    'plugin:prettier/recommended',
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
    '@typescript-eslint/no-unused-vars': 'off', // Remove this when the "mock" phase is over
    'vue/multi-word-component-names': 'warn', // Default is error
    'vue/no-unused-vars': 'warn',
    '@typescript-eslint/no-explicit-any':
      process.env.NODE_ENV === 'production' ? 'error' : 'warn',
    'vue/no-unused-components':
      process.env.NODE_ENV === 'production' ? 'error' : 'warn',
  },
};
