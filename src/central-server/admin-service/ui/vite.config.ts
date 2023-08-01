import { resolve } from 'node:path';

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueJsx from '@vitejs/plugin-vue-jsx'
import vuetify from 'vite-plugin-vuetify';
import viteBasicSslPlugin from "@vitejs/plugin-basic-ssl";

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueJsx(),
    vuetify({ autoImport: true }),
    viteBasicSslPlugin()
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
      'vue-i18n': 'vue-i18n/dist/vue-i18n.cjs.js',
      '@shared-ui': resolve(__dirname,'./../../../shared-ui-3/src'),
    }
  },
  server: {
    https: true,
    port:8080,
    host: 'localhost',
    proxy: {
      '/api': {
        secure: false,
        target: process.env.PROXY_ADDRESS || 'https://localhost:4000',
      },
      '/login': {
        secure: false,
        target: 'https://localhost:4000',
      }
    },
    fs: {
      // Allow serving files from one level up to the project root
      allow: ['.', '../../../shared-ui-3/src'],
    }
  }
})
