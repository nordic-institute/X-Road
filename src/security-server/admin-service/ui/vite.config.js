/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

import basicSsl from '@vitejs/plugin-basic-ssl';
import vue from '@vitejs/plugin-vue';
import { resolve } from 'node:path';
import { defineConfig, loadEnv } from 'vite';
import vuetify from 'vite-plugin-vuetify';

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const lang = /\/locales?\/([a-z]{2}([-_][A-Z]+))\.(js|json)$/;
  return {
    plugins: [
      vue(),
      vuetify({
        styles: {
          configFile: 'src/assets/settings.scss',
        },
      }),
      basicSsl(),
    ],
    css: {
      preprocessorOptions: {
        scss: {
          api: 'modern',
        },
      },
    },
    build: {
      cssCodeSplit: false,
      rollupOptions: {
        output: {
          manualChunks: function manualChunks(id) {
            const langMatch = lang.exec(id);
            if (langMatch) {
              return `lang-${langMatch[3]}`;
            }

            if (id.includes('/shared-ui/')) {
              return 'shared-ui';
            }

            if (id.includes('/vue/')) {
              return 'vue';
            }

            if (id.includes('/vuetify/')) {
              return 'vuetify';
            }

            if (id.includes('/node_modules/')) {
              return 'vendor';
            }

            return null;
          },
        },
      },
    },
    resolve: {
      alias: {
        '@': resolve(__dirname, './src'),
        'vue-i18n': 'vue-i18n/dist/vue-i18n.cjs.js',
      },
    },
    test: {
      globals: true,
      environment: 'happy-dom',
      testTimeout: 15000,
      server: {
        deps: {
          inline: ['vuetify'],
        },
      },
    },
    server: {
      https: true,
      port: 8080,
      host: 'localhost',
      proxy: {
        '/api': {
          secure: false,
          target: env.PROXY_ADDRESS || 'https://localhost:4200',
        },
        '/login': {
          secure: false,
          target: env.PROXY_ADDRESS || 'https://localhost:4200',
        },
      },
    },
  };
});
