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
import {config} from 'vitest-browser-vue';
import {createPinia} from 'pinia';
import {createPersistedState} from 'pinia-plugin-persistedstate';
import {createVuetify} from 'vuetify';
import {createValidators} from '@niis/shared-ui/src/plugins/vee-validate';
import {i18n as sharedI18n} from '@niis/shared-ui/src/plugins/i18n';
import {type Plugin} from 'vue';
import * as components from 'vuetify/components';
import * as directives from 'vuetify/directives';
import {beforeEach} from 'vitest';
import {ensureMessages} from './i18n-messages';

beforeEach(() => {
  sessionStorage.clear();
  localStorage.clear();
});

function buildPinia() {
  const pinia = createPinia();
  pinia.use(createPersistedState({storage: sessionStorage}));
  return pinia;
}

function buildVuetify() {
  return createVuetify({components, directives});
}

export async function configureGlobals() {
  await ensureMessages();

  config.global.plugins = [
    buildPinia(),
    buildVuetify(),
    sharedI18n as unknown as Plugin,
    createValidators(),
  ];
}
