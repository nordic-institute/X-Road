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
import { playwright } from '@vitest/browser-playwright';

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const lang = /\/locales?\/([a-z]{2}([-_][A-Z]+))\.(js|json)$/;

  const proxyCfg = {
    secure: false,
    target: env.PROXY_ADDRESS || 'https://localhost:4200',
  }

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
    html: {
      cspNonce: '__CSP_NONCE__',
    },
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
      testTimeout: 15000,
      attachmentsDir: 'tests/results/attachments',
      reporters: ['default', 'junit'],
      outputFile: {
        junit: 'tests/results/vitest-junit.xml',
      },
      server: {
        deps: {
          inline: ['vuetify'],
        },
      },
      projects: [
        {
          extends: true,
          test: {
            name: 'unit',
            include: ['tests/unit/**/*.spec.ts'],
            environment: 'happy-dom',
          },
        },
        {
          extends: true,
          optimizeDeps: {
            include: [
              'vuetify/components/VApp',
              'vuetify/components/VBanner',
              'vuetify/components/VBreadcrumbs',
              'vuetify/components/VBtn',
              'vuetify/components/VCard',
              'vuetify/components/VCheckbox',
              'vuetify/components/VChip',
              'vuetify/components/VDataTable',
              'vuetify/components/VDialog',
              'vuetify/components/VDivider',
              'vuetify/components/VFileInput',
              'vuetify/components/VFooter',
              'vuetify/components/VForm',
              'vuetify/components/VGrid',
              'vuetify/components/VIcon',
              'vuetify/components/VImg',
              'vuetify/components/VList',
              'vuetify/components/VNavigationDrawer',
              'vuetify/components/VPagination',
              'vuetify/components/VProgressLinear',
              'vuetify/components/VRadio',
              'vuetify/components/VRadioGroup',
              'vuetify/components/VSelect',
              'vuetify/components/VSheet',
              'vuetify/components/VSkeletonLoader',
              'vuetify/components/VSnackbar',
              'vuetify/components/VStepper',
              'vuetify/components/VSystemBar',
              'vuetify/components/VTable',
              'vuetify/components/VTabs',
              'vuetify/components/VTextField',
              'vuetify/components/VAlert',
              'vuetify/components/VAvatar',
              'vuetify/components/VCombobox',
              'vuetify/components/VMain',
              'vuetify/components/VSwitch',
              'vuetify/components/VTooltip',
              'vuetify/components/transitions',
            ],
          },
          test: {
            name: 'browser',
            include: ['tests/component/**/*.browser.spec.ts'],
            browser: {
              enabled: true,
              headless: true,
              provider: playwright({
                launchOptions: {
                  args: ['--ignore-certificate-errors'],
                },
              }),
              instances: [{ browser: 'chromium', viewport: { width: 1920, height: 1080 } }],
              screenshotFailures: true,
              screenshotDirectory: 'tests/results/screenshots',
              locators: {
                testIdAttribute: 'data-test',
              },
            },
            setupFiles: ['tests/setup/browser-setup.ts'],
          },
        },
      ],
    },
    server: {
      https: true,
      port: 8888,
      host: 'localhost',
      proxy: {
        '/api': proxyCfg,
        '/login': proxyCfg,
        '/logout': proxyCfg,
      },
    },
  };
});
