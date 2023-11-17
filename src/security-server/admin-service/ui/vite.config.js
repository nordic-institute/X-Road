import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import vueJsx from '@vitejs/plugin-vue-jsx';
import vuetify from 'vite-plugin-vuetify';
import basicSsl from '@vitejs/plugin-basic-ssl';

// https://vitejs.dev/config/
const path = require('path');
export default defineConfig({
  plugins: [vue(), vueJsx(), vuetify({ autoImport: true }), basicSsl()],
  build: {
    cssCodeSplit: false,
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
      'vue-i18n': 'vue-i18n/dist/vue-i18n.cjs.js',
    },
  },
  server: {
    https: true,
    port: 8080,
    host: 'localhost',
    proxy: {
      '/api': {
        secure: false,
        target: process.env.PROXY_ADDRESS || 'https://localhost:4100',
      },
      '/login': {
        secure: false,
        target: 'https://localhost:4100',
      },
    },
  },
});
