import Vue from 'vue';
import axios from 'axios';
import Router from 'vue-router';
import vuetify from './plugins/vuetify';
import './plugins/vee-validate';
import './filters';
import App from './App.vue';
import router from './router';
import store from './store';
import 'roboto-fontface/css/roboto/roboto-fontface.css';
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
