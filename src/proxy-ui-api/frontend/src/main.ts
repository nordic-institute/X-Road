import Vue from 'vue';
import axios from 'axios';
import VeeValidate from 'vee-validate';
import './plugins/vuetify';
import './filters';
import App from './App.vue';
import router from './router';
import store from './store';
import 'roboto-fontface/css/roboto/roboto-fontface.css';
import '@fortawesome/fontawesome-free/css/all.css';
import 'material-design-icons-iconfont/dist/material-design-icons.css';
import i18n from './i18n';
import validationMessagesEN from 'vee-validate/dist/locale/en';

Vue.config.productionTip = false;

// axios.defaults.baseURL = "https://16fc6543-9d45-401a-b14b-73fe26f9f8ba.mock.pstmn.io";
axios.defaults.baseURL = process.env.VUE_APP_BASE_URL;
// axios.defaults.headers.common['Authorization'] = 'fasfdsa'
axios.defaults.headers.get.Accepts = 'application/json';

const EventBus = new Vue();

Object.defineProperties(Vue.prototype, {
  $bus: {
    get() {
      return EventBus;
    },
  },
});

Vue.use(VeeValidate, {
  i18nRootKey: 'validations', // customize the root path for validation messages.
  i18n,
  dictionary: {
    en: validationMessagesEN,
  },
});

new Vue({
  router,
  store,
  i18n,
  render: (h) => h(App),
}).$mount('#app');
