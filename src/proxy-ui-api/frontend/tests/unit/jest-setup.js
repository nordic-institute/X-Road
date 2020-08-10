//
// package.json has a section that instructs jest to
// read this setup file.
//

import Vue from 'vue';

// DON'T DO THIS or you'll have problems like <v-btn :to="..."> rendering
// to <router-link> instead of <a href="..."> on the unit tests.
//import Vuetify from 'vuetify';
//Vue.use(Vuetify); // NO, DON'T DO THIS.

// Because of the regeneratorRuntime error.
//import 'babel-polyfill';

//Vue.use(Vuetify);
Vue.config.productionTip = false;