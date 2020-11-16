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
import Vue from 'vue';
import axios from 'axios';
import Router from 'vue-router';
import SharedComponents from '@niis/shared-ui';
Vue.use(SharedComponents); // This must be done before importing Vuetify
import vuetify from './plugins/vuetify';
import './plugins/vee-validate';
import './filters';
import App from './App.vue';
import router from './router';
import store from './store';
import 'fontsource-open-sans';
import './assets/icons.css';
import i18n from './i18n';

Vue.config.productionTip = false;

// axios.defaults.baseURL = "https://16fc6543-9d45-401a-b14b-73fe26f9f8ba.mock.pstmn.io";
axios.defaults.baseURL = process.env.VUE_APP_BASE_URL;
// axios.defaults.headers.common['Authorization'] = 'fasfdsa'
axios.defaults.headers.get.Accepts = 'application/json';

Vue.use(Router);

new Vue({
  router,
  store,
  i18n,
  vuetify,
  render: (h) => h(App),
}).$mount('#app');
