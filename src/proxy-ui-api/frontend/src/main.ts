import Vue from 'vue';
import './plugins/vuetify';
import './plugins/vee-validate';
import axios from 'axios';
import App from './App.vue';
import router from './router';
import store from './store';
import i18n from './i18n';

Vue.config.productionTip = false;

//axios.defaults.baseURL = "https://16fc6543-9d45-401a-b14b-73fe26f9f8ba.mock.pstmn.io";
axios.defaults.baseURL = process.env.VUE_APP_BASE_URL;
// axios.defaults.headers.common['Authorization'] = 'fasfdsa'
axios.defaults.headers.get.Accepts = 'application/json';

const EventBus = new Vue();

Object.defineProperties(Vue.prototype, {
  $bus: {
    get: function () {
      return EventBus;
    }
  }
});

new Vue({
  router,
  store,
  i18n,
  render: (h) => h(App)
}).$mount('#app');
