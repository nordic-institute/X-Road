import { fileURLToPath } from 'url';
import { defineConfig } from 'vite';
import { createVuePlugin as vue2 } from 'vite-plugin-vue2';
// @ts-ignore
import vueTemplateBabelCompiler from 'vue-template-babel-compiler';
import scriptSetup from 'unplugin-vue2-script-setup/vite';
/* eslint-disable @typescript-eslint/no-var-requires */
const path = require('path');

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    vue2({
      jsx: true,
      vueTemplateOptions: {
        compiler: vueTemplateBabelCompiler,
      },
    }),
    scriptSetup(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      //  'core-js': path.resolve('./node_modules/core-js'),
      // vue$: path.resolve('./node_modules/vue/dist/vue.runtime.esm.js'),
      // vuetify: path.resolve('./node_modules/vuetify'),
      // 'vue-i18n': path.resolve('./node_modules/vue-i18n'),
      // 'vee-validate': path.resolve('./node_modules/vee-validate'),
      // alias for styles
      '~styles': fileURLToPath(new URL('./src/assets', import.meta.url)),
    },
    // dedupe: ['vuetify', 'vue-i18n', 'vee-validate', 'vue', 'code-js'],
  },

  server: {
    port: 8080,
    https: true, // This could be as well false?
    proxy: {
      // string shorthand
      '/': { target: 'https://localhost:4100', secure: false },
    },
  },
});
