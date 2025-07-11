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

/*
Startpoint of the Vue application.
Sets up plugins and 3rd party components that the app uses.
Creates a new Vue instance with the Vue function.
Initialises the app root component.
*/
import { createApp } from 'vue';
import axios from 'axios';
import { createFilters } from '@/filters';
import App from './App.vue';
import router from './router/router';
import '@fontsource/open-sans/800.css';
import '@fontsource/open-sans/700.css';
import '@fontsource/open-sans';
import { createPinia } from 'pinia';
import { createPersistedState } from 'pinia-plugin-persistedstate';
import validation from '@/plugins/vee-validate';
import vuetify from '@/plugins/vuetify';
import {
  i18n,
  XrdButton,
  XrdCloseButton,
  XrdConfirmDialog,
  XrdEmptyPlaceholder,
  XrdIconAdd,
  XrdIconBase,
  XrdIconChecked,
  XrdIconChecker,
  XrdIconClose,
  XrdIconCopy,
  XrdIconEdit,
  XrdIconFolderOutline,
  XrdSearch,
  XrdSimpleDialog,
  XrdSubViewContainer,
  XrdSubViewTitle,
  XrdTitledView,
} from '@niis/shared-ui';
import { createLanguageHelper } from '@/plugins/i18n';
import provider from '@/plugins/provider';

const pinia = createPinia();
pinia.use(
  createPersistedState({
    storage: sessionStorage,
  }),
);

axios.defaults.baseURL = import.meta.env.VITE_BASE_URL;
axios.defaults.headers.get.Accepts = 'application/json';

const app = createApp(App);
app.use(pinia);
app.use(router);
app.use(vuetify);
app.use(validation);
app.use(createFilters());
app.use(provider);
//icons
app.component('XrdIconFolderOutline', XrdIconFolderOutline);
app.component('XrdIconBase', XrdIconBase);
app.component('XrdIconChecker', XrdIconChecker);
app.component('XrdIconClose', XrdIconClose);
app.component('XrdIconChecked', XrdIconChecked);
app.component('XrdIconAdd', XrdIconAdd);
app.component('XrdIconCopy', XrdIconCopy);
app.component('XrdIconEdit', XrdIconEdit);
//components
app.component('XrdButton', XrdButton);
app.component('XrdSearch', XrdSearch);
app.component('XrdCloseButton', XrdCloseButton);
app.component('XrdSubViewContainer', XrdSubViewContainer);
app.component('XrdSimpleDialog', XrdSimpleDialog);
app.component('XrdConfirmDialog', XrdConfirmDialog);
app.component('XrdEmptyPlaceholder', XrdEmptyPlaceholder);
app.component('XrdSubViewTitle', XrdSubViewTitle);
app.component('XrdTitledView', XrdTitledView);

// translations
createLanguageHelper()
  .then((plugin) => app.use(plugin))
  .finally(() => app.mount('#app'));
