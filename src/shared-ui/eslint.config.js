import eslint from '@eslint/js';

import eslintPluginVueI18n from '@intlify/eslint-plugin-vue-i18n';
import eslintVueConfigPrettier from '@vue/eslint-config-prettier';

import eslintPluginVue from 'eslint-plugin-vue';
import eslintPluginVueScopedCSS from 'eslint-plugin-vue-scoped-css';
import eslintPluginVuetify from 'eslint-plugin-vuetify';
import globals from 'globals';

import typescriptEslint from 'typescript-eslint';

export default typescriptEslint.config(
  {
    ignores: ['*.d.ts', '**/.gitignore', '**/dist'],
  },
  {
    extends: [
      eslint.configs.recommended,
      typescriptEslint.configs.recommended,
      ...eslintPluginVueI18n.configs.recommended,
      ...eslintPluginVueScopedCSS.configs['flat/recommended'],
      ...eslintPluginVue.configs['flat/recommended'],
      ...eslintPluginVuetify.configs['flat/base'],
    ],
    languageOptions: {
      globals: {
        ...globals.browser,
      },
      ecmaVersion: 'latest',
      sourceType: 'module',
      parserOptions: {
        parser: typescriptEslint.parser,
        project: true,
        tsconfigRootDir: import.meta.dirname,
        extraFileExtensions: ['.vue'],
      },
    },
    files: ['src/**/*.vue', 'src/**/*.js', 'src/**.*.jsx', 'src/**/*.cjs', 'src/**.*.mjs', 'src/**/*.ts', 'src/**.*.tsx', 'src/**.*.mts'],

    rules: {
      'no-console': process.env.NODE_ENV === 'production' ? 'error' : 'warn',
      'no-debugger': process.env.NODE_ENV === 'production' ? 'error' : 'off',
      '@typescript-eslint/no-var-requires': 0,
      '@typescript-eslint/camelcase': 'off',
      '@typescript-eslint/no-unused-vars': 'off',
      '@typescript-eslint/no-deprecated': 'warn',
      '@typescript-eslint/no-explicit-any': process.env.NODE_ENV === 'production' ? 'error' : 'warn',
      'vue/no-unused-vars': 'warn',
      'vue/max-attributes-per-line': 'off',
      'vue/no-unused-components': process.env.NODE_ENV === 'production' ? 'error' : 'warn',
    },

    settings: {
      'vue-i18n': {
        localeDir: './locales/*.{json,json5,yaml,yml}',
        messageSyntaxVersion: '^11.0.0',
      },
    },
  },
  eslintVueConfigPrettier,
);
