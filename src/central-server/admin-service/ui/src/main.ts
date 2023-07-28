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
import { createApp } from 'vue'
import axios from 'axios';
import './plugins/vee-validate';
import { createFilters } from "@/filters";
import App from './App.vue';
import router from './router/router';
import '@fontsource/open-sans/800.css';
import '@fontsource/open-sans/700.css';
import '@fontsource/open-sans';
import { createPinia } from 'pinia';
import { createPersistedState } from 'pinia-plugin-persistedstate';
import { createValidators } from "@/plugins/vee-validate";
import '@shared-ui/assets/icons.css';
import XrdIconFolderOutline from '@shared-ui/components/icons/XrdIconFolderOutline.vue';
import XrdIconBase from '@shared-ui/components/icons/XrdIconBase.vue';
import XrdIconChecker from '@shared-ui/components/icons/XrdIconChecker.vue';
import XrdIconClose from '@shared-ui/components/icons/XrdIconClose.vue';
import XrdIconChecked from '@shared-ui/components/icons/XrdIconChecked.vue';
import XrdIconAdd from '@shared-ui/components/icons/XrdIconAdd.vue';
import XrdIconCopy from '@shared-ui/components/icons/XrdIconCopy.vue';
import XrdButton from '@shared-ui/components/XrdButton.vue';
import XrdSearch from '@shared-ui/components/XrdSearch.vue';
import XrdCloseButton from '@shared-ui/components/XrdCloseButton.vue';
import XrdSubViewContainer from '@shared-ui/components/XrdSubViewContainer.vue';
import XrdSimpleDialog from '@shared-ui/components/XrdSimpleDialog.vue';
import XrdConfirmDialog from '@shared-ui/components/XrdConfirmDialog.vue';
import vuetify from '@/plugins/vuetify';
import i18n from "@/plugins/i18n";


const pinia = createPinia();
pinia.use(
  createPersistedState({
    storage: sessionStorage,
  }),
);

//Vue.config.productionTip = false; TODO vue3 probably save to remove
axios.defaults.baseURL = import.meta.env.VITE_VUE_APP_BASE_URL;
console.log(import.meta.env.VITE_VUE_APP_BASE_URL)

axios.defaults.headers.get.Accepts = 'application/json';

const app = createApp(App);
app.use(pinia);
app.use(router);
app.use(vuetify);
app.use(i18n);
app.use(createFilters());
app.use(createValidators());
//icons
app.component('XrdIconFolderOutline', XrdIconFolderOutline);
app.component('XrdIconBase', XrdIconBase);
app.component('XrdIconChecker', XrdIconChecker);
app.component('XrdIconClose', XrdIconClose);
app.component('XrdIconChecked', XrdIconChecked);
app.component('XrdIconAdd', XrdIconAdd);
app.component('XrdIconCopy', XrdIconCopy);
//components
app.component('XrdButton', XrdButton);
app.component('XrdSearch', XrdSearch);
app.component('XrdCloseButton', XrdCloseButton);
app.component('XrdSubViewContainer', XrdSubViewContainer);
app.component('XrdSimpleDialog', XrdSimpleDialog);
app.component('XrdConfirmDialog', XrdConfirmDialog);

app.mount('#app');


