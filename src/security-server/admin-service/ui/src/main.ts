/*
 * The MIT License
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
import {
  XrdButton,
  XrdCloseButton,
  XrdConfirmDialog,
  XrdEmptyPlaceholder,
  XrdEmptyPlaceholderRow,
  XrdExpandable,
  XrdFileUpload,
  XrdFormLabel,
  XrdIconAdd,
  XrdIconBase,
  XrdIconChecked,
  XrdIconChecker,
  XrdIconClose,
  XrdIconCopy,
  XrdIconDeclined,
  XrdIconError,
  XrdIconFolderOutline,
  XrdIconSortingArrow,
  XrdIconTooltip,
  XrdSearch,
  XrdSimpleDialog,
  XrdStatusIcon,
  XrdSubViewContainer,
  XrdSubViewTitle,
} from '@niis/shared-ui';
import vuetify from './plugins/vuetify';
import './plugins/vee-validate';
import './filters';
import App from './App.vue';
import router from './router';
import '@fontsource/open-sans/800.css';
import '@fontsource/open-sans/700.css';
import '@fontsource/open-sans';
import '@niis/shared-ui/dist/style.css';
import i18n from './plugins/i18n';
import { createPinia } from 'pinia';
import { createPersistedState } from 'pinia-plugin-persistedstate';
import { createFilters } from '@/filters';
import { createValidators } from '@/plugins/vee-validate';

const pinia = createPinia();
pinia.use(
  createPersistedState({
    storage: sessionStorage,
  }),
);

axios.defaults.baseURL = import.meta.env.VITE_VUE_APP_BASE_URL;
axios.defaults.headers.get.Accepts = 'application/json';

const app = createApp(App);
app.use(router);
app.use(i18n);
app.use(vuetify);
app.use(pinia);
app.use(createFilters());
app.use(createValidators());
//icons
app.component('XrdIconFolderOutline', XrdIconFolderOutline);
app.component('XrdIconBase', XrdIconBase);
app.component('XrdIconChecker', XrdIconChecker);
app.component('XrdIconChecked', XrdIconChecked);
app.component('XrdIconClose', XrdIconClose);
app.component('XrdIconAdd', XrdIconAdd);
app.component('XrdIconCopy', XrdIconCopy);
app.component('XrdIconError', XrdIconError);
app.component('XrdIconTooltip', XrdIconTooltip);
app.component('XrdIconSortingArrow', XrdIconSortingArrow);
app.component('XrdIconDeclined', XrdIconDeclined);
app.component('XrdStatusIcon', XrdStatusIcon);
//components
app.component('XrdButton', XrdButton);
app.component('XrdSearch', XrdSearch);
app.component('XrdSubViewContainer', XrdSubViewContainer);
app.component('XrdSimpleDialog', XrdSimpleDialog);
app.component('XrdConfirmDialog', XrdConfirmDialog);
app.component('XrdEmptyPlaceholder', XrdEmptyPlaceholder);
app.component('XrdEmptyPlaceholderRow', XrdEmptyPlaceholderRow);
app.component('XrdSubViewTitle', XrdSubViewTitle);
app.component('XrdCloseButton', XrdCloseButton);
app.component('XrdFileUpload', XrdFileUpload);
app.component('XrdFormLabel', XrdFormLabel);
app.component('XrdExpandable', XrdExpandable);
app.mount('#app');
