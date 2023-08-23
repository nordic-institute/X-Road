module.exports = {
  root: true,
  env: {
    node: true,
    es2022: true
  },
  extends: [
    'plugin:vue/recommended',
    'eslint:recommended',
    '@vue/typescript/recommended',
    '@vue/prettier',
    '@vue/prettier/@typescript-eslint',
  ],
  ignorePatterns: ['node_modules/'],
  parserOptions: {
    ecmaVersion: 2020,
  },
  rules: {
    'no-console': import.meta.env.NODE_ENV === 'production' ? 'error' : 'warn',
    'no-debugger': import.meta.env.NODE_ENV === 'production' ? 'error' : 'off',
    '@typescript-eslint/no-var-requires': 0,
    '@typescript-eslint/camelcase': 'off',
    '@typescript-eslint/no-unused-vars': 'off', // Remove this when the "mock" phase is over
    'vue/no-unused-vars': 'warn',
    '@typescript-eslint/no-explicit-any':
      import.meta.env.NODE_ENV === 'production' ? 'error' : 'warn',
    'vue/no-unused-components':
      import.meta.env.NODE_ENV === 'production' ? 'error' : 'warn',
  },
};
